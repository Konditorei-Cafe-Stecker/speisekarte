package com.stecker.backend.synthetic;

import com.stecker.backend.model.LocalEvent;
import com.stecker.backend.model.LocalEventType;
import com.stecker.backend.model.MenuItem;
import com.stecker.backend.model.WeatherObservation;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;

import static com.stecker.backend.synthetic.SyntheticScenario.*;

/**
 * Generates a reproducible order history from the real menu, the real
 * hourly weather and the real Bremen events.
 *
 * The same menu, context data, date range and seed always produce exactly
 * the same orders. Nothing here touches the database.
 */
public final class SyntheticOrderGenerator {

    /** One order line: a product and how many. */
    public record Line(MenuItem item, int quantity) {
        public BigDecimal lineTotal() {
            return item.getPrice().multiply(BigDecimal.valueOf(quantity));
        }
    }

    /**
     * One generated order. handledAt is when the (simulated) waiter
     * confirmed or cancelled it.
     */
    public record GeneratedOrder(
            UUID reference,
            Instant createdAt,
            Instant handledAt,
            boolean cancelled,
            List<Line> lines
    ) {
        public BigDecimal total() {
            return lines.stream()
                    .map(Line::lineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    /** Sorts products by their content, so database IDs don't change results. */
    static final Comparator<MenuItem> NATURAL_ORDER = Comparator
            .comparing(MenuItem::getCategory)
            .thenComparing(MenuItem::getSubcategory,
                    Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(MenuItem::getName)
            .thenComparing(MenuItem::getSizeVolume,
                    Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(MenuItem::getDescription,
                    Comparator.nullsFirst(Comparator.naturalOrder()));

    private final long seed;
    private final Map<Instant, WeatherObservation> weatherByHour;
    private final List<LocalEvent> cityEvents;
    private final List<LocalEvent> footballMatches;
    private final Map<ProductGroup, List<MenuItem>> productsByGroup =
            new EnumMap<>(ProductGroup.class);
    private final Map<MenuItem, Double> popularity = new HashMap<>();
    private final SplittableRandom random;

    public SyntheticOrderGenerator(
            List<MenuItem> menu,
            List<WeatherObservation> weather,
            List<LocalEvent> events,
            long seed
    ) {
        this.seed = seed;
        this.weatherByHour = new HashMap<>();
        for (WeatherObservation hour : weather) {
            weatherByHour.put(hour.getObservedAt(), hour);
        }
        this.cityEvents = events.stream()
                .filter(e -> e.getType() != LocalEventType.FOOTBALL)
                .toList();
        this.footballMatches = events.stream()
                .filter(e -> e.getType() == LocalEventType.FOOTBALL)
                .toList();

        // Popularity is drawn once per product in a fixed order.
        SplittableRandom popularityRandom = new SplittableRandom(seed);
        List<MenuItem> sorted = menu.stream()
                .filter(MenuItem::isAvailable)
                .sorted(NATURAL_ORDER)
                .toList();
        Map<String, Long> perSubcategory = new HashMap<>();
        for (MenuItem item : sorted) {
            perSubcategory.merge(String.valueOf(item.getSubcategory()), 1L, Long::sum);
        }
        for (MenuItem item : sorted) {
            String subcategory = String.valueOf(item.getSubcategory());
            double share = SUBCATEGORY_SHARE.getOrDefault(subcategory, 1.0)
                    / perSubcategory.get(subcategory);
            double factor = PRODUCT_FACTOR.getOrDefault(item.getName(), 1.0);
            if (ProductGroup.of(item) == ProductGroup.HOT_DRINK
                    && "2 cl".equals(item.getSizeVolume())) {
                factor *= LIQUEUR_HOT_DRINK_FACTOR;
            }
            double weight = share * factor
                    * Math.exp(POPULARITY_SPREAD * popularityRandom.nextGaussian());
            popularity.put(item, weight);
            productsByGroup
                    .computeIfAbsent(ProductGroup.of(item), g -> new ArrayList<>())
                    .add(item);
        }

        this.random = new SplittableRandom(seed ^ 0x5DEECE66DL);
    }

    /** Generates all orders from start to endInclusive (local dates). */
    public List<GeneratedOrder> generate(LocalDate start, LocalDate endInclusive) {
        List<GeneratedOrder> orders = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(endInclusive); day = day.plusDays(1)) {
            generateDay(day, orders);
        }
        return orders;
    }

    private void generateDay(LocalDate day, List<GeneratedOrder> orders) {
        Optional<LocalTime> opens = CafeCalendar.opens(day);
        if (opens.isEmpty()) {
            return;
        }

        LocalTime lastOrder = CafeCalendar.lastOrderBefore();
        double dayFactor = WEEKDAY_FACTOR.get(day.getDayOfWeek())
                * MONTH_FACTOR.get(day.getMonth());
        int indexInDay = 0;

        for (LocalTime hour = opens.get(); hour.isBefore(lastOrder); hour = hour.plusHours(1)) {
            ZonedDateTime hourStart = day.atTime(hour).atZone(ZONE);
            int minutesOpen = (int) Math.min(60,
                    java.time.Duration.between(hour, lastOrder).toMinutes());
            WeatherObservation weather = weatherAt(hourStart.toInstant());
            Instant middle = hourStart.plusMinutes(minutesOpen / 2).toInstant();

            double expected = BASE_ORDERS_PER_HOUR
                    * HOUR_WEIGHT.get(hour.getHour())
                    * (minutesOpen / 60.0)
                    * dayFactor
                    * rainFactor(weather)
                    * eventFactor(middle)
                    * matchFactor(middle);

            int count = poisson(expected);
            List<Integer> minutes = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                minutes.add(random.nextInt(minutesOpen * 60)); // seconds into the hour
            }
            minutes.sort(Integer::compare);

            for (int secondsIn : minutes) {
                Instant createdAt = hourStart.plusSeconds(secondsIn).toInstant();
                orders.add(createOrder(day, indexInDay++, createdAt, weather));
            }
        }
    }

    private GeneratedOrder createOrder(
            LocalDate day,
            int indexInDay,
            Instant createdAt,
            WeatherObservation weather
    ) {
        double temperature = weather.getTemperatureC().doubleValue();
        LocalTime localTime = createdAt.atZone(ZONE).toLocalTime();
        boolean preMatch = isPreMatch(createdAt);
        boolean christmasMarket = cityEvents.stream().anyMatch(e ->
                e.getType() == LocalEventType.CHRISTMAS_MARKET && e.isActiveAt(createdAt));

        Map<MenuItem, Integer> quantities = new LinkedHashMap<>();
        int partySize = partySize();

        for (int person = 0; person < partySize; person++) {
            boolean ordered = false;
            ProductGroup drinkGroup = null;

            if (random.nextDouble() < DRINK_PROBABILITY) {
                drinkGroup = drinkGroup(temperature, localTime, preMatch);
                ordered |= add(quantities, drinkGroup, day, christmasMarket);
            }

            double food = localTime.isBefore(BREAKFAST_UNTIL) ? BREAKFAST_PROBABILITY
                    : localTime.isBefore(LUNCH_UNTIL) ? LUNCH_SAVOURY_PROBABILITY
                    : OTHER_SAVOURY_PROBABILITY;
            if (random.nextDouble() < food) {
                ProductGroup foodGroup = localTime.isBefore(BREAKFAST_UNTIL)
                        ? ProductGroup.BREAKFAST : ProductGroup.SAVOURY;
                ordered |= add(quantities, foodGroup, day, christmasMarket);
            }

            if (random.nextDouble() < iceProbability(temperature, localTime)) {
                ordered |= add(quantities, ProductGroup.ICE_DESSERT, day, christmasMarket);
            }

            if (drinkGroup == ProductGroup.ALCOHOL
                    && random.nextDouble() < BAR_SNACK_WITH_ALCOHOL_PROBABILITY) {
                ordered |= add(quantities, ProductGroup.BAR_SNACK, day, christmasMarket);
            }

            if (!ordered) {
                add(quantities, ProductGroup.HOT_DRINK, day, christmasMarket);
            }
        }

        List<Line> lines = quantities.entrySet().stream()
                .map(entry -> new Line(entry.getKey(), entry.getValue()))
                .toList();

        boolean cancelled = random.nextDouble() < CANCEL_PROBABILITY;
        Instant handledAt = createdAt.plusSeconds(60L
                * (MIN_CONFIRM_DELAY_MINUTES
                + random.nextInt(MAX_CONFIRM_DELAY_MINUTES - MIN_CONFIRM_DELAY_MINUTES + 1))
                + random.nextInt(60));

        UUID reference = UUID.nameUUIDFromBytes(
                ("stecker-synthetic|" + seed + "|" + day + "|" + indexInDay)
                        .getBytes(StandardCharsets.UTF_8));

        return new GeneratedOrder(reference, createdAt, handledAt, cancelled, lines);
    }

    // ---- Footfall multipliers ----

    private static double rainFactor(WeatherObservation weather) {
        return weather.getPrecipitationMm().doubleValue() >= RAINY_HOUR_MM
                ? RAINY_HOUR_FACTOR : 1.0;
    }

    private double eventFactor(Instant moment) {
        double factor = 1.0;
        for (LocalEvent event : cityEvents) {
            if (event.isActiveAt(moment)) {
                factor *= EVENT_FACTOR.getOrDefault(event.getType(), 1.0);
            }
        }
        return factor;
    }

    private double matchFactor(Instant moment) {
        if (isPreMatch(moment)) {
            return PRE_MATCH_FACTOR;
        }
        for (LocalEvent match : footballMatches) {
            if (match.isActiveAt(moment)) {
                return DURING_MATCH_FACTOR;
            }
        }
        return 1.0;
    }

    private boolean isPreMatch(Instant moment) {
        for (LocalEvent match : footballMatches) {
            Instant from = match.getStartsAt().minus(PRE_MATCH_WINDOW);
            Instant until = match.getStartsAt().minus(PRE_MATCH_END_BEFORE_KICKOFF);
            if (!moment.isBefore(from) && moment.isBefore(until)) {
                return true;
            }
        }
        return false;
    }

    // ---- Product choice ----

    private ProductGroup drinkGroup(double temperature, LocalTime time, boolean preMatch) {
        double hot = HOT_DRINK_WEIGHT * Math.exp(HOT_DRINK_TEMPERATURE_SLOPE
                * (temperature - DRINK_REFERENCE_TEMPERATURE_C));
        double cold = COLD_DRINK_WEIGHT * Math.exp(COLD_DRINK_TEMPERATURE_SLOPE
                * (temperature - DRINK_REFERENCE_TEMPERATURE_C));
        double alcohol = ALCOHOL_WEIGHT
                * (time.getHour() < 12 ? ALCOHOL_BEFORE_NOON_FACTOR : 1.0)
                * (time.getHour() >= 16 ? ALCOHOL_LATE_AFTERNOON_FACTOR : 1.0)
                * (preMatch ? PRE_MATCH_ALCOHOL_BOOST : 1.0);

        double pick = random.nextDouble() * (hot + cold + alcohol);
        if (pick < hot) {
            return ProductGroup.HOT_DRINK;
        }
        return pick < hot + cold ? ProductGroup.COLD_DRINK : ProductGroup.ALCOHOL;
    }

    private static double iceProbability(double temperature, LocalTime time) {
        double probability = Math.min(ICE_MAX_PROBABILITY, ICE_BASE_PROBABILITY
                * Math.exp(ICE_TEMPERATURE_SLOPE
                * (temperature - ICE_REFERENCE_TEMPERATURE_C)));
        return probability * (time.getHour() >= 13 ? ICE_AFTERNOON_FACTOR : ICE_MORNING_FACTOR);
    }

    /** Adds one product from the group. Returns false if none can be sold. */
    private boolean add(
            Map<MenuItem, Integer> quantities,
            ProductGroup group,
            LocalDate day,
            boolean christmasMarket
    ) {
        List<MenuItem> products = productsByGroup.getOrDefault(group, List.of());
        double[] weights = new double[products.size()];
        double sum = 0;
        for (int i = 0; i < products.size(); i++) {
            weights[i] = weight(products.get(i), day, christmasMarket);
            sum += weights[i];
        }
        if (sum <= 0) {
            return false;
        }

        double pick = random.nextDouble() * sum;
        for (int i = 0; i < products.size(); i++) {
            pick -= weights[i];
            if (pick < 0 || i == products.size() - 1) {
                quantities.merge(products.get(i), 1, Integer::sum);
                return true;
            }
        }
        return false;
    }

    private double weight(MenuItem item, LocalDate day, boolean christmasMarket) {
        double weight = popularity.get(item);

        if (item.getName().contains(GLUEHWEIN_NAME)) {
            if (!GLUEHWEIN_MONTHS.contains(day.getMonth())) {
                return 0.0;
            }
            if (christmasMarket) {
                weight *= GLUEHWEIN_CHRISTMAS_MARKET_BOOST;
            }
        }

        if (isStrawberry(item) && !STRAWBERRY_MONTHS.contains(day.getMonth())) {
            weight *= STRAWBERRY_OFF_SEASON_FACTOR;
        }

        return weight;
    }

    static boolean isStrawberry(MenuItem item) {
        String text = (item.getName() + " " + Optional.ofNullable(item.getDescription()).orElse(""))
                .toLowerCase();
        return text.contains("erdbeer");
    }

    // ---- Random helpers ----

    private int partySize() {
        double pick = random.nextDouble();
        for (int i = 0; i < PARTY_SIZE_PROBABILITY.length; i++) {
            pick -= PARTY_SIZE_PROBABILITY[i];
            if (pick < 0) {
                return i + 1;
            }
        }
        return PARTY_SIZE_PROBABILITY.length;
    }

    /** Poisson-distributed count (Knuth's method; fine for small means). */
    private int poisson(double mean) {
        double limit = Math.exp(-mean);
        double product = random.nextDouble();
        int count = 0;
        while (product > limit) {
            product *= random.nextDouble();
            count++;
        }
        return count;
    }

    private WeatherObservation weatherAt(Instant hourStart) {
        WeatherObservation weather = weatherByHour.get(hourStart);
        if (weather == null) {
            throw new IllegalStateException("No weather observation for " + hourStart
                    + ". Import the context data for the whole date range first.");
        }
        return weather;
    }
}
