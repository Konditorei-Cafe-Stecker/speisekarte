package com.stecker.backend.synthetic;

import com.stecker.backend.model.LocalEventType;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

/**
 * Every assumption built into the simulated order history, in one place.
 *
 * The weather, football matches and city events are REAL (see
 * docs/synthetic-data.md for sources). How guests react to them is NOT
 * known — there is no real sales data yet — so the reactions below are
 * invented, plausible assumptions. The analysis should find these effects
 * again; that checks the analysis, not the real café.
 */
public final class SyntheticScenario {

    private SyntheticScenario() {
    }

    public static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    // ---- Opening calendar (from frontend/src/content/site.ts, Nov 2026) ----

    public static final LocalTime WEEKDAY_OPEN = LocalTime.of(9, 0);
    public static final LocalTime SUNDAY_OPEN = LocalTime.of(13, 0);
    public static final LocalTime CLOSE = LocalTime.of(18, 0);
    /** No new orders in the last half hour before closing. */
    public static final int LAST_ORDER_MINUTES_BEFORE_CLOSE = 30;

    // ---- How many orders arrive ----

    /** Expected orders per hour when every multiplier below is 1.0. */
    public static final double BASE_ORDERS_PER_HOUR = 5.0;

    /** Relative busyness by the local hour the order is placed in. */
    public static final Map<Integer, Double> HOUR_WEIGHT = Map.of(
            9, 0.6, 10, 0.9, 11, 1.0, 12, 1.1, 13, 1.0,
            14, 1.3, 15, 1.5, 16, 1.3, 17, 0.6);

    public static final Map<DayOfWeek, Double> WEEKDAY_FACTOR = Map.of(
            DayOfWeek.MONDAY, 0.80,
            DayOfWeek.TUESDAY, 0.85,
            DayOfWeek.WEDNESDAY, 0.90,
            DayOfWeek.THURSDAY, 0.95,
            DayOfWeek.FRIDAY, 1.10,
            DayOfWeek.SATURDAY, 1.40,
            DayOfWeek.SUNDAY, 1.20);

    /** Tourist season in the old town; weather effects come on top. */
    public static final Map<Month, Double> MONTH_FACTOR = Map.ofEntries(
            Map.entry(Month.JANUARY, 0.85), Map.entry(Month.FEBRUARY, 0.85),
            Map.entry(Month.MARCH, 0.90), Map.entry(Month.APRIL, 1.00),
            Map.entry(Month.MAY, 1.05), Map.entry(Month.JUNE, 1.05),
            Map.entry(Month.JULY, 1.10), Map.entry(Month.AUGUST, 1.10),
            Map.entry(Month.SEPTEMBER, 1.05), Map.entry(Month.OCTOBER, 1.00),
            Map.entry(Month.NOVEMBER, 0.95), Map.entry(Month.DECEMBER, 1.05));

    // ---- Reactions to the REAL weather (DWD station Bremen) ----

    /** An hour counts as rainy from this much measured rain. */
    public static final double RAINY_HOUR_MM = 0.5;
    /** Fewer guests walk in during a rainy hour. */
    public static final double RAINY_HOUR_FACTOR = 0.80;

    // ---- Reactions to REAL events ----

    /** Extra guests while a city event runs. Overlapping events multiply. */
    public static final Map<LocalEventType, Double> EVENT_FACTOR = Map.of(
            LocalEventType.CHRISTMAS_MARKET, 1.30,
            LocalEventType.FAIR, 1.10,
            LocalEventType.FESTIVAL, 1.10,
            LocalEventType.PARADE, 1.30);

    /** Werder home match: fans pass through the old town before kickoff. */
    public static final Duration PRE_MATCH_WINDOW = Duration.ofHours(4);
    /** Fans have left for the stadium this long before kickoff. */
    public static final Duration PRE_MATCH_END_BEFORE_KICKOFF = Duration.ofMinutes(30);
    public static final double PRE_MATCH_FACTOR = 1.20;
    /** Alcohol is this much more likely in the pre-match window. */
    public static final double PRE_MATCH_ALCOHOL_BOOST = 2.5;
    /** Quieter while the match is on (kickoff to kickoff + 2 h). */
    public static final double DURING_MATCH_FACTOR = 0.85;

    // ---- What each guest orders ----

    /** Party size 1, 2, 3, 4 with these probabilities. */
    public static final double[] PARTY_SIZE_PROBABILITY = {0.40, 0.40, 0.12, 0.08};

    public static final double DRINK_PROBABILITY = 0.93;
    public static final double HOT_DRINK_WEIGHT = 6.0;
    public static final double COLD_DRINK_WEIGHT = 2.0;
    public static final double ALCOHOL_WEIGHT = 0.5;
    /** Hot drinks: weight × exp(-0.05 × (T − 12 °C)), T = hour's temperature. */
    public static final double HOT_DRINK_TEMPERATURE_SLOPE = -0.05;
    /** Cold drinks: weight × exp(0.06 × (T − 12 °C)). */
    public static final double COLD_DRINK_TEMPERATURE_SLOPE = 0.06;
    public static final double DRINK_REFERENCE_TEMPERATURE_C = 12.0;
    /** Alcohol before noon is rare, and more common from 16:00. */
    public static final double ALCOHOL_BEFORE_NOON_FACTOR = 0.15;
    public static final double ALCOHOL_LATE_AFTERNOON_FACTOR = 1.6;

    /** Breakfast is ordered until 11:30. */
    public static final LocalTime BREAKFAST_UNTIL = LocalTime.of(11, 30);
    public static final double BREAKFAST_PROBABILITY = 0.40;
    /** Lunch window for soups and classics. */
    public static final LocalTime LUNCH_UNTIL = LocalTime.of(14, 30);
    public static final double LUNCH_SAVOURY_PROBABILITY = 0.30;
    public static final double OTHER_SAVOURY_PROBABILITY = 0.08;

    /** Ice cream: 0.10 × exp(0.08 × (T − 15 °C)), at most 0.60. */
    public static final double ICE_BASE_PROBABILITY = 0.10;
    public static final double ICE_TEMPERATURE_SLOPE = 0.08;
    public static final double ICE_REFERENCE_TEMPERATURE_C = 15.0;
    public static final double ICE_MAX_PROBABILITY = 0.60;
    /** Ice cream from 13:00 is more likely than in the morning. */
    public static final double ICE_AFTERNOON_FACTOR = 1.3;
    public static final double ICE_MORNING_FACTOR = 0.6;

    /** A bar snack goes with an alcoholic drink this often. */
    public static final double BAR_SNACK_WITH_ALCOHOL_PROBABILITY = 0.20;

    /** Glühwein is only sold November–February ... */
    public static final String GLUEHWEIN_NAME = "Glühwein";
    public static final Set<Month> GLUEHWEIN_MONTHS =
            Set.of(Month.NOVEMBER, Month.DECEMBER, Month.JANUARY, Month.FEBRUARY);
    /** ... and much more while the Christmas market runs. */
    public static final double GLUEHWEIN_CHRISTMAS_MARKET_BOOST = 4.0;

    /** Fresh-strawberry products sell mostly in strawberry season. */
    public static final Set<Month> STRAWBERRY_MONTHS =
            Set.of(Month.MAY, Month.JUNE, Month.JULY, Month.AUGUST);
    public static final double STRAWBERRY_OFF_SEASON_FACTOR = 0.2;

    /**
     * How a product group's demand splits across its subcategories.
     * Each subcategory's share is divided evenly over its products before
     * the product factors below are applied.
     */
    public static final Map<String, Double> SUBCATEGORY_SHARE = Map.ofEntries(
            // Hot drinks
            Map.entry("Azul Kaffee", 0.68),
            Map.entry("Tee (Glas)", 0.12),
            Map.entry("Schokolade", 0.10),
            Map.entry("Tee auf Stövchen traditionell", 0.06),
            Map.entry("Zum Aufwärmen", 0.04),
            // Cold drinks
            Map.entry("Alkoholfreie Getränke", 0.70),
            Map.entry("Fruchtsäfte", 0.30),
            // Alcohol
            Map.entry("Sekt & Prosecco", 0.35),
            Map.entry("Biere", 0.30),
            Map.entry("Weine", 0.20),
            Map.entry("Longdrinks", 0.15),
            // Food
            Map.entry("Frühstück", 0.70),
            Map.entry("Belegte Brötchen", 0.30),
            Map.entry("Kleine, herzhafte Leckereien", 0.55),
            Map.entry("Klassisches", 0.45));

    /** Per-product adjustments: staples sell more, niche items less. */
    public static final Map<String, Double> PRODUCT_FACTOR = Map.ofEntries(
            Map.entry("Cappuccino mit Milch", 4.0),
            Map.entry("Tasse Filterkaffee", 4.0),
            Map.entry("Milchkaffee", 2.5),
            Map.entry("Latte Macchiato", 2.5),
            Map.entry("Kännchen Filterkaffee", 1.5),
            Map.entry("Espresso", 1.5),
            Map.entry("Cappuccino mit Sahne", 1.2),
            Map.entry("Glas Tee – Azul Friesen-Mischung", 1.8),
            Map.entry("Glas Pfefferminztee", 1.5),
            Map.entry("Tasse Schokolade", 2.0),
            Map.entry("Apfelsaftschorle", 2.5),
            Map.entry("Apollinaris Selection", 1.5),
            Map.entry("Coca-Cola oder Coca-Cola light", 1.5),
            Map.entry("Rhabarbersaft-Schorle", 1.3),
            Map.entry("Schweppes Tonic Water", 0.3),
            Map.entry("Schweppes Bitter Lemon", 0.3),
            Map.entry("Orangensaft", 1.5),
            Map.entry("Apfelsaft", 1.3),
            Map.entry("Grapefruit", 0.5),
            Map.entry("Aperol Spritz", 2.0),
            Map.entry("Boulevard-Frühstück", 1.5),
            Map.entry("Bremer Frühstückchen", 1.3),
            Map.entry("Portion Eis – Kleine Portion (3 Kugeln)", 2.5),
            Map.entry("Portion Eis – Eispalette (4 Kugeln)", 1.5),
            Map.entry("Eiskaffee", 2.0),
            Map.entry("Erdbeere Stück", 1.5));

    /** Hot drinks with a 2 cl shot of liquor are occasional treats. */
    public static final double LIQUEUR_HOT_DRINK_FACTOR = 0.3;

    /** Remaining random variation between products, fixed by the seed. */
    public static final double POPULARITY_SPREAD = 0.25;

    // ---- Order outcome ----

    public static final double CANCEL_PROBABILITY = 0.03;
    /** Waiter confirms 1–8 minutes after the order is placed. */
    public static final int MIN_CONFIRM_DELAY_MINUTES = 1;
    public static final int MAX_CONFIRM_DELAY_MINUTES = 8;
}
