package com.stecker.backend.service;

import com.stecker.backend.dto.MenuItemResponse;
import com.stecker.backend.dto.TodayRecommendations;
import com.stecker.backend.dto.TodayRecommendations.Recommendation;
import com.stecker.backend.model.ForecastDay;
import com.stecker.backend.model.MenuItem;
import com.stecker.backend.repository.ForecastDayRepository;
import com.stecker.backend.repository.MenuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TodayRecommendationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

    private ForecastDayRepository forecasts;
    private MenuRepository menu;
    private final List<MenuItem> menuItems = new ArrayList<>();
    private TodayRecommendationService service;

    @BeforeEach
    void setUp() {
        forecasts = mock(ForecastDayRepository.class);
        menu = mock(MenuRepository.class);
        when(menu.findAllByOrderByIdAsc()).thenReturn(menuItems);
        // 23:30 UTC on the 22nd is already 00:30 on the 23rd in Bremen.
        service = at("2026-11-22T23:30:00Z");

        item(1, "Tasse Filterkaffee", null, "3.50", true);
        item(18, "Tasse Schokolade", null, "4.70", true);
        item(19, "Kännchen Schokolade mit Schlagsahne", null, "7.70", true);
        item(27, "Glas Tee – Azul Friesen-Mischung", null, "4.10", true);
        item(24, "Glühwein", null, "5.50", true);
        item(60, "Franziskaner Hefe Weißbier", "Fl. 0,5 l", "7.10", true);
        item(63, "Nussmischung", null, "5.30", true);
        item(81, "1 ganzes belegtes Brötchen", null, "6.20", true);
        item(82, "1 ganzes belegtes Brötchen", null, "7.20", true);
        item(105, "Eis Schokolade", null, "7.50", false);
    }

    @Test
    void linksAWinterWeekdayToMenuItemsWithNormalPrices() {
        forecast(WINTER_WEEKDAY);

        TodayRecommendations result = service.today();

        assertEquals(TODAY, result.day());
        // Time-limited first (breakfast ends at 12), then the all-day ones.
        assertEquals(List.of("BREAKFAST", "WARM_DRINKS", "CHRISTMAS_MARKET"), kinds(result));

        Recommendation breakfast = result.recommendations().get(0);
        assertEquals("Frühstück", breakfast.title());
        assertEquals(LocalTime.of(9, 0), breakfast.validFrom());
        assertEquals(LocalTime.of(12, 0), breakfast.validUntil());
        // Both Brötchen variants carry the same label; both are shown.
        assertEquals(List.of(81L, 82L, 1L), ids(breakfast));

        Recommendation warm = result.recommendations().get(1);
        assertEquals("Zum Aufwärmen", warm.title());
        assertNull(warm.validUntil());
        assertEquals(List.of(18L, 19L, 27L), ids(warm));
        assertEquals(new BigDecimal("4.70"), warm.products().getFirst().price());

        assertEquals(List.of(24L), ids(result.recommendations().get(2)));
    }

    @Test
    void dropsBreakfastOnceItIsOver() {
        forecast(WINTER_WEEKDAY);

        // 10:59 UTC = 11:59 in Bremen: still breakfast. 11:00 UTC = 12:00: over.
        assertEquals("BREAKFAST", kinds(at("2026-11-23T10:59:00Z").today()).getFirst());
        assertEquals(List.of("WARM_DRINKS", "CHRISTMAS_MARKET"), kinds(at("2026-11-23T11:00:00Z").today()));
    }

    @Test
    void readsTheMatchDaySuggestionAndEndsItBeforeKickOff() {
        forecast("Spieltags-Bundle bis 17:00: Franziskaner Hefe Weißbier (Fl. 0,5 l) + Nussmischung");

        Recommendation match = service.today().recommendations().getFirst();

        assertEquals("MATCH_DAY", match.kind());
        assertEquals(LocalTime.of(17, 0), match.validUntil());
        assertEquals(List.of(60L, 63L), ids(match));
        assertTrue(at("2026-11-23T15:59:00Z").today().recommendations().size() == 1);
        assertTrue(at("2026-11-23T16:00:00Z").today().recommendations().isEmpty());
    }

    @Test
    void leavesOutUnknownSuggestionsAndUnavailableProducts() {
        // Eis Schokolade is not available, so the ice-cream tip has no product.
        forecast("Eis hervorheben, z. B. Eis Schokolade | Etwas ganz Neues | Glühwein-Angebot");

        assertEquals(List.of("CHRISTMAS_MARKET"), kinds(service.today()));
    }

    @Test
    void isEmptyWithoutAForecastForToday() {
        when(forecasts.findById(TODAY)).thenReturn(Optional.empty());

        TodayRecommendations result = service.today();

        assertEquals(TODAY, result.day());
        assertTrue(result.recommendations().isEmpty());
    }

    private static final String WINTER_WEEKDAY =
            "Heißgetränke-Angebot (Schokolade, Tee) | Glühwein-Angebot (Weihnachtsmarkt) | "
                    + "Frühstücks-Bundle 9–12 Uhr: 1 ganzes belegtes Brötchen + Tasse Filterkaffee";

    private TodayRecommendationService at(String instant) {
        return new TodayRecommendationService(forecasts, menu,
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private void forecast(String offers) {
        ForecastDay day = new ForecastDay(TODAY, 47, 38, 56, new BigDecimal("685.05"),
                new BigDecimal("550"), new BigDecimal("820"), -1.0, 3.0, 0, "", offers,
                "SYNTHETIC", Instant.now());
        when(forecasts.findById(TODAY)).thenReturn(Optional.of(day));
    }

    private void item(long id, String name, String size, String price, boolean available) {
        MenuItem item = mock(MenuItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getName()).thenReturn(name);
        when(item.getSizeVolume()).thenReturn(size);
        when(item.getPrice()).thenReturn(new BigDecimal(price));
        when(item.isAvailable()).thenReturn(available);
        menuItems.add(item);
    }

    private static List<String> kinds(TodayRecommendations result) {
        return result.recommendations().stream().map(Recommendation::kind).toList();
    }

    private static List<Long> ids(Recommendation recommendation) {
        return recommendation.products().stream().map(MenuItemResponse::id).toList();
    }
}
