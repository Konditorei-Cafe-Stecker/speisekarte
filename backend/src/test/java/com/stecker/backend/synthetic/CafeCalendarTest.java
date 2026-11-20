package com.stecker.backend.synthetic;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CafeCalendarTest {

    @Test
    void easterDatesAreCorrect() {
        assertEquals(LocalDate.of(2025, 4, 20), CafeCalendar.easterSunday(2025));
        assertEquals(LocalDate.of(2026, 4, 5), CafeCalendar.easterSunday(2026));
        assertEquals(LocalDate.of(2027, 3, 28), CafeCalendar.easterSunday(2027));
    }

    @Test
    void closedOnBremenPublicHolidays() {
        assertFalse(CafeCalendar.isOpen(LocalDate.of(2026, 4, 3)));   // Karfreitag
        assertFalse(CafeCalendar.isOpen(LocalDate.of(2026, 5, 14)));  // Himmelfahrt
        assertFalse(CafeCalendar.isOpen(LocalDate.of(2026, 10, 31))); // Reformationstag
        assertFalse(CafeCalendar.isOpen(LocalDate.of(2025, 12, 25)));
        assertTrue(CafeCalendar.isOpen(LocalDate.of(2025, 12, 24)));
    }

    @Test
    void sundayOpensAtOne() {
        assertEquals(Optional.of(LocalTime.of(13, 0)),
                CafeCalendar.opens(LocalDate.of(2026, 3, 1)));   // Sunday
        assertEquals(Optional.of(LocalTime.of(9, 0)),
                CafeCalendar.opens(LocalDate.of(2026, 3, 2)));   // Monday
    }
}
