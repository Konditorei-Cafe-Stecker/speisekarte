package com.stecker.backend.synthetic;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.util.Optional;
import java.util.Set;

/**
 * Opening hours used for the simulation.
 *
 * Assumption: the hours stored in site.ts (Mon–Sat 09:00–18:00,
 * Sun 13:00–18:00) applied for the whole simulated year, and the café was
 * closed on public holidays in Bremen. Real historical hours are unknown.
 */
public final class CafeCalendar {

    private static final Set<MonthDay> FIXED_HOLIDAYS = Set.of(
            MonthDay.of(1, 1),    // Neujahr
            MonthDay.of(5, 1),    // Tag der Arbeit
            MonthDay.of(10, 3),   // Tag der Deutschen Einheit
            MonthDay.of(10, 31),  // Reformationstag (Bremen)
            MonthDay.of(12, 25),  // 1. Weihnachtstag
            MonthDay.of(12, 26)); // 2. Weihnachtstag

    private CafeCalendar() {
    }

    public static boolean isOpen(LocalDate day) {
        return !isPublicHoliday(day);
    }

    /** Opening time, or empty when closed. */
    public static Optional<LocalTime> opens(LocalDate day) {
        if (!isOpen(day)) {
            return Optional.empty();
        }
        return Optional.of(day.getDayOfWeek() == DayOfWeek.SUNDAY
                ? SyntheticScenario.SUNDAY_OPEN
                : SyntheticScenario.WEEKDAY_OPEN);
    }

    /** Latest time a new order can be placed (exclusive). */
    public static LocalTime lastOrderBefore() {
        return SyntheticScenario.CLOSE.minusMinutes(
                SyntheticScenario.LAST_ORDER_MINUTES_BEFORE_CLOSE);
    }

    public static boolean isPublicHoliday(LocalDate day) {
        if (FIXED_HOLIDAYS.contains(MonthDay.from(day))) {
            return true;
        }
        LocalDate easter = easterSunday(day.getYear());
        return day.equals(easter.minusDays(2))   // Karfreitag
                || day.equals(easter.plusDays(1))  // Ostermontag
                || day.equals(easter.plusDays(39)) // Christi Himmelfahrt
                || day.equals(easter.plusDays(50)); // Pfingstmontag
    }

    /** Gregorian Easter Sunday (anonymous Gregorian algorithm). */
    static LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int dayOfMonth = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, dayOfMonth);
    }
}
