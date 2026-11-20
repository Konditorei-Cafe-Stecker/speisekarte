package com.stecker.backend.synthetic;

import com.stecker.backend.context.ContextDataTestAccess;
import com.stecker.backend.model.LocalEvent;
import com.stecker.backend.model.LocalEventType;
import com.stecker.backend.model.MenuItem;
import com.stecker.backend.model.WeatherObservation;
import com.stecker.backend.synthetic.SyntheticOrderGenerator.GeneratedOrder;
import com.stecker.backend.synthetic.SyntheticOrderGenerator.Line;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the generator on the real menu and the real Bremen context data for
 * the full year, and checks that the built-in effects can be found again.
 */
class SyntheticOrderGeneratorTest {

    private static final LocalDate START = LocalDate.of(2025, 11, 1);
    private static final LocalDate END = LocalDate.of(2026, 10, 31);
    private static final long SEED = 20261120L;

    private static List<MenuItem> menu;
    private static List<WeatherObservation> weather;
    private static List<LocalEvent> events;
    private static List<GeneratedOrder> orders;
    private static Map<Instant, WeatherObservation> weatherByHour;

    @BeforeAll
    static void generateOnce() {
        menu = TestMenu.loadWithIds();
        weather = ContextDataTestAccess.weather();
        events = ContextDataTestAccess.events();
        orders = new SyntheticOrderGenerator(menu, weather, events, SEED)
                .generate(START, END);
        weatherByHour = new HashMap<>();
        weather.forEach(w -> weatherByHour.put(w.getObservedAt(), w));
    }

    @Test
    void usesTheRealMenu() {
        assertEquals(107, menu.size());
    }

    @Test
    void sameSeedGivesTheSameOrders() {
        List<GeneratedOrder> again = new SyntheticOrderGenerator(
                TestMenu.loadWithIds(), weather, events, SEED).generate(START, END);

        assertEquals(summary(orders), summary(again));
    }

    @Test
    void differentSeedGivesDifferentOrders() {
        List<GeneratedOrder> other = new SyntheticOrderGenerator(
                menu, weather, events, SEED + 1).generate(START, END);

        assertNotEquals(summary(orders), summary(other));
    }

    @Test
    void everyOrderPassesValidation() {
        SyntheticOrderService.validate(orders, START, END);
    }

    @Test
    void volumeAndOrderValuesLookLikeASmallCafe() {
        long openDays = START.datesUntil(END.plusDays(1))
                .filter(CafeCalendar::isOpen).count();
        double perDay = orders.size() / (double) openDays;
        BigDecimal average = orders.stream().map(GeneratedOrder::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(orders.size()), 2, RoundingMode.HALF_UP);

        assertTrue(perDay > 30 && perDay < 90, "orders per day: " + perDay);
        assertTrue(average.compareTo(new BigDecimal("8")) > 0
                && average.compareTo(new BigDecimal("25")) < 0, "average order: " + average);
    }

    @Test
    void saturdaysAreBusierThanMondays() {
        double saturday = ordersPerDay(DayOfWeek.SATURDAY);
        double monday = ordersPerDay(DayOfWeek.MONDAY);

        assertTrue(saturday > monday * 1.4, saturday + " vs " + monday);
    }

    @Test
    void iceCreamSellsMuchBetterInWarmHours() {
        double warm = groupShare(ProductGroup.ICE_DESSERT, o -> temperature(o) >= 22);
        double cold = groupShare(ProductGroup.ICE_DESSERT, o -> temperature(o) < 8);

        assertTrue(warm > cold * 2, "warm " + warm + " vs cold " + cold);
    }

    @Test
    void hotDrinksSellBetterInColdHours() {
        double cold = groupShare(ProductGroup.HOT_DRINK, o -> temperature(o) < 8);
        double warm = groupShare(ProductGroup.HOT_DRINK, o -> temperature(o) >= 22);

        assertTrue(cold > warm, "cold " + cold + " vs warm " + warm);
    }

    @Test
    void fewerOrdersArriveInRainyHours() {
        Map<Boolean, List<Instant>> hours = openHours().stream()
                .collect(Collectors.partitioningBy(h ->
                        weatherByHour.get(h).getPrecipitationMm().doubleValue()
                                >= SyntheticScenario.RAINY_HOUR_MM));
        Map<Instant, Long> perHour = orders.stream().collect(Collectors.groupingBy(
                o -> o.createdAt().truncatedTo(ChronoUnit.HOURS), Collectors.counting()));

        double rainy = hours.get(true).stream()
                .mapToLong(h -> perHour.getOrDefault(h, 0L)).average().orElseThrow();
        double dry = hours.get(false).stream()
                .mapToLong(h -> perHour.getOrDefault(h, 0L)).average().orElseThrow();

        assertTrue(rainy < dry, "rainy " + rainy + " vs dry " + dry);
    }

    @Test
    void moreAlcoholBeforeWerderHomeMatches() {
        List<LocalEvent> matches = events.stream()
                .filter(e -> e.getType() == LocalEventType.FOOTBALL).toList();

        double preMatch = groupShare(ProductGroup.ALCOHOL, o -> matches.stream().anyMatch(m ->
                !o.createdAt().isBefore(m.getStartsAt().minus(SyntheticScenario.PRE_MATCH_WINDOW))
                        && o.createdAt().isBefore(m.getStartsAt()
                        .minus(SyntheticScenario.PRE_MATCH_END_BEFORE_KICKOFF))));
        double otherAfternoons = groupShare(ProductGroup.ALCOHOL, o -> {
            ZonedDateTime local = o.createdAt().atZone(SyntheticScenario.ZONE);
            return local.getHour() >= 11 && local.getHour() < 15 && matches.stream()
                    .noneMatch(m -> m.getStartsAt().atZone(SyntheticScenario.ZONE)
                            .toLocalDate().equals(local.toLocalDate()));
        });

        assertTrue(preMatch > otherAfternoons * 1.5,
                "pre-match " + preMatch + " vs other " + otherAfternoons);
    }

    @Test
    void gluehweinOnlyInWinterAndMostlyAtTheChristmasMarket() {
        Map<Month, Long> byMonth = new HashMap<>();
        for (GeneratedOrder order : orders) {
            for (Line line : order.lines()) {
                if (line.item().getName().contains("Glühwein")) {
                    byMonth.merge(order.createdAt().atZone(SyntheticScenario.ZONE).getMonth(),
                            (long) line.quantity(), Long::sum);
                }
            }
        }

        assertTrue(SyntheticScenario.GLUEHWEIN_MONTHS.containsAll(byMonth.keySet()),
                byMonth.toString());
        assertTrue(byMonth.getOrDefault(Month.DECEMBER, 0L)
                > byMonth.getOrDefault(Month.FEBRUARY, 0L) * 2, byMonth.toString());
    }

    @Test
    void cappuccinoAndFilterkaffeeAreAmongTheTopSellers() {
        Map<String, Integer> sold = new HashMap<>();
        orders.forEach(o -> o.lines().forEach(l ->
                sold.merge(l.item().getName(), l.quantity(), Integer::sum)));
        List<String> topFive = sold.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5).map(Map.Entry::getKey).toList();

        assertTrue(topFive.contains("Cappuccino mit Milch")
                || topFive.contains("Tasse Filterkaffee"), topFive.toString());
    }

    // ---- helpers ----

    private static List<String> summary(List<GeneratedOrder> list) {
        return list.stream().map(o -> o.reference() + " " + o.createdAt() + " "
                + o.confirmedAt() + " " + o.lines().stream()
                .map(l -> l.item().getName() + "×" + l.quantity()).toList()).toList();
    }

    private double ordersPerDay(DayOfWeek weekday) {
        long days = START.datesUntil(END.plusDays(1))
                .filter(d -> d.getDayOfWeek() == weekday && CafeCalendar.isOpen(d)).count();
        long count = orders.stream().filter(o ->
                o.createdAt().atZone(SyntheticScenario.ZONE).getDayOfWeek() == weekday).count();
        return count / (double) days;
    }

    private double temperature(GeneratedOrder order) {
        return weatherByHour.get(order.createdAt().truncatedTo(ChronoUnit.HOURS))
                .getTemperatureC().doubleValue();
    }

    /** Share of order lines that belong to a group, among matching orders. */
    private double groupShare(ProductGroup group, java.util.function.Predicate<GeneratedOrder> filter) {
        long inGroup = 0;
        long all = 0;
        for (GeneratedOrder order : orders) {
            if (!filter.test(order)) {
                continue;
            }
            for (Line line : order.lines()) {
                all += line.quantity();
                if (ProductGroup.of(line.item()) == group) {
                    inGroup += line.quantity();
                }
            }
        }
        return all == 0 ? 0 : inGroup / (double) all;
    }

    private List<Instant> openHours() {
        List<Instant> hours = new ArrayList<>();
        for (LocalDate day = START; !day.isAfter(END); day = day.plusDays(1)) {
            var opens = CafeCalendar.opens(day);
            if (opens.isEmpty()) {
                continue;
            }
            for (var t = opens.get(); t.isBefore(CafeCalendar.lastOrderBefore()); t = t.plusHours(1)) {
                if (t.getHour() < 17) { // skip the shortened last hour
                    hours.add(day.atTime(t).atZone(SyntheticScenario.ZONE).toInstant());
                }
            }
        }
        return hours;
    }
}
