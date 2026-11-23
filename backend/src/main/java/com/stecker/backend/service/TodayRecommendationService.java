package com.stecker.backend.service;

import com.stecker.backend.dto.MenuItemResponse;
import com.stecker.backend.dto.TodayRecommendations;
import com.stecker.backend.dto.TodayRecommendations.Recommendation;
import com.stecker.backend.model.ForecastDay;
import com.stecker.backend.model.MenuItem;
import com.stecker.backend.repository.ForecastDayRepository;
import com.stecker.backend.repository.MenuRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the offer suggestions of today's forecast into recommendations for
 * guests.
 *
 * The forecast (analysis/forecast_and_offers.py --publish) writes its
 * suggestions as German text, separated by " | ". This class knows the five
 * kinds it writes and links each to real menu items. Anything it does not
 * recognise is left out, and so is any product that is not on the menu or
 * not available. Prices stay the normal menu prices.
 *
 * Breakfast ("9–12 Uhr") and the match-day tip ("bis 17:00") carry the
 * time window from the forecast; once it is over they are no longer
 * returned that day.
 */
@Service
public class TodayRecommendationService {

    private static final Pattern ICE_CREAM = Pattern.compile("^Eis hervorheben, z\\. B\\. (.+)$");
    private static final Pattern WARM_DRINKS = Pattern.compile("^Heißgetränke-Angebot\\b.*$");
    private static final Pattern MATCH_DAY =
            Pattern.compile("^Spieltags-Bundle bis (\\d{1,2}:\\d{2}): (.+?) \\+ (.+)$");
    private static final Pattern CHRISTMAS_MARKET = Pattern.compile("^Glühwein-Angebot\\b.*$");
    private static final Pattern BREAKFAST =
            Pattern.compile("^Frühstücks-Bundle (\\d{1,2})–(\\d{1,2}) Uhr: (.+?) \\+ (.+)$");

    /** The suggestion names no products for these two; these menu items fit. */
    private static final List<String> WARM_DRINK_PRODUCTS = List.of(
            "Tasse Schokolade",
            "Kännchen Schokolade mit Schlagsahne",
            "Glas Tee – Azul Friesen-Mischung");
    private static final List<String> CHRISTMAS_MARKET_PRODUCTS = List.of("Glühwein");

    private final ForecastDayRepository forecasts;
    private final MenuRepository menu;
    private final Clock clock;

    public TodayRecommendationService(
            ForecastDayRepository forecasts,
            MenuRepository menu,
            Clock clock
    ) {
        this.forecasts = forecasts;
        this.menu = menu;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TodayRecommendations today() {
        LocalDateTime now = LocalDateTime.now(clock.withZone(OrderService.CAFE_ZONE));
        LocalDate today = now.toLocalDate();

        String suggestions = forecasts.findById(today).map(ForecastDay::getOffers).orElse(null);
        if (suggestions == null || suggestions.isBlank()) {
            return new TodayRecommendations(today, List.of());
        }

        Map<String, List<MenuItem>> available = availableByLabel();
        List<Recommendation> result = new ArrayList<>();

        for (String suggestion : suggestions.split("\\s*\\|\\s*")) {
            toRecommendation(suggestion.strip(), available)
                    .filter(recommendation -> stillValid(recommendation, now.toLocalTime()))
                    .ifPresent(result::add);
        }

        // Time-limited ones first, the one ending soonest at the top.
        result.sort(Comparator.comparing(Recommendation::validUntil,
                Comparator.nullsLast(Comparator.naturalOrder())));

        return new TodayRecommendations(today, result);
    }

    static Optional<Recommendation> toRecommendation(
            String suggestion,
            Map<String, List<MenuItem>> available
    ) {
        Matcher match;

        if (WARM_DRINKS.matcher(suggestion).matches()) {
            return recommendation("WARM_DRINKS", "Zum Aufwärmen",
                    "Draußen bleibt es heute kalt – Schokolade und Tee wärmen von innen.",
                    null, null, WARM_DRINK_PRODUCTS, available);
        }
        if (CHRISTMAS_MARKET.matcher(suggestion).matches()) {
            return recommendation("CHRISTMAS_MARKET", "Zur Weihnachtsmarkt-Zeit",
                    "Ein heißer Glühwein nach dem Bummel über den Bremer Weihnachtsmarkt.",
                    null, null, CHRISTMAS_MARKET_PRODUCTS, available);
        }
        if ((match = BREAKFAST.matcher(suggestion)).matches()) {
            LocalTime from = LocalTime.of(Integer.parseInt(match.group(1)), 0);
            LocalTime until = LocalTime.of(Integer.parseInt(match.group(2)), 0);
            return recommendation("BREAKFAST", "Frühstück",
                    "Ein guter Start in den Tag.",
                    from, until, List.of(match.group(3), match.group(4)), available);
        }
        if ((match = MATCH_DAY.matcher(suggestion)).matches()) {
            return recommendation("MATCH_DAY", "Vor dem Anpfiff",
                    "Heute spielt Werder im Weserstadion – passend dazu:",
                    null, LocalTime.parse(pad(match.group(1))),
                    List.of(match.group(2), match.group(3)), available);
        }
        if ((match = ICE_CREAM.matcher(suggestion)).matches()) {
            return recommendation("ICE_CREAM", "Eis bei Sonne",
                    "Heute wird es warm – Zeit für Eis aus eigener Herstellung.",
                    null, null, List.of(match.group(1)), available);
        }
        return Optional.empty();
    }

    /** Still to come or running; recommendations without an end always are. */
    static boolean stillValid(Recommendation recommendation, LocalTime now) {
        return recommendation.validUntil() == null || now.isBefore(recommendation.validUntil());
    }

    /** "9:30" becomes "09:30", as LocalTime.parse expects. */
    private static String pad(String time) {
        return time.length() == 4 ? "0" + time : time;
    }

    private static Optional<Recommendation> recommendation(
            String kind,
            String title,
            String text,
            LocalTime validFrom,
            LocalTime validUntil,
            List<String> labels,
            Map<String, List<MenuItem>> available
    ) {
        List<MenuItemResponse> products = labels.stream()
                .flatMap(label -> available.getOrDefault(label, List.of()).stream())
                .distinct()
                .map(MenuItemResponse::from)
                .toList();

        // Without a single orderable product there is nothing to recommend.
        return products.isEmpty()
                ? Optional.empty()
                : Optional.of(new Recommendation(kind, title, text, validFrom, validUntil, products));
    }

    /** Available menu items by the label the forecast uses for them. */
    private Map<String, List<MenuItem>> availableByLabel() {
        Map<String, List<MenuItem>> byLabel = new LinkedHashMap<>();
        menu.findAllByOrderByIdAsc().stream()
                .filter(MenuItem::isAvailable)
                .forEach(item -> byLabel.computeIfAbsent(label(item), key -> new ArrayList<>()).add(item));
        return byLabel;
    }

    /**
     * The same label as {@code analytics.order_line_facts.product_label}:
     * the name, plus the size in brackets unless the name already says it.
     */
    static String label(MenuItem item) {
        String size = item.getSizeVolume();
        if (size == null || size.isBlank() || item.getName().contains(size)) {
            return item.getName();
        }
        return item.getName() + " (" + size + ")";
    }
}
