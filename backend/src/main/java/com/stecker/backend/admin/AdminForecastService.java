package com.stecker.backend.admin;

import com.stecker.backend.admin.AdminForecastResponse.Day;
import com.stecker.backend.admin.AdminForecastResponse.Hour;
import com.stecker.backend.admin.AdminForecastResponse.ProductNeed;
import com.stecker.backend.admin.AdminForecastResponse.Staffing;
import com.stecker.backend.admin.AdminForecastResponse.Summary;
import com.stecker.backend.model.ForecastDay;
import com.stecker.backend.model.ForecastHour;
import com.stecker.backend.model.ForecastProduct;
import com.stecker.backend.repository.ForecastDayRepository;
import com.stecker.backend.repository.ForecastHourRepository;
import com.stecker.backend.repository.ForecastProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Turns the published forecast tables into the admin dashboard's view. */
@Service
public class AdminForecastService {

    /** Share of periods whose demand stays at or below the "busy case". */
    static final double BUSY_CASE = 0.90;

    private final ForecastDayRepository dayRepository;
    private final ForecastHourRepository hourRepository;
    private final ForecastProductRepository productRepository;

    public AdminForecastService(
            ForecastDayRepository dayRepository,
            ForecastHourRepository hourRepository,
            ForecastProductRepository productRepository
    ) {
        this.dayRepository = dayRepository;
        this.hourRepository = hourRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public AdminForecastResponse forecast(LocalDate from, int days, Staffing staffing) {
        LocalDate to = from.plusDays(days - 1L);
        List<ForecastDay> forecastDays = dayRepository.findByDayBetweenOrderByDayAsc(from, to);
        if (forecastDays.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No forecast published for " + from + " – " + to + ". Run "
                            + "python3 analysis/forecast_and_offers.py --publish");
        }

        Map<LocalDate, List<ForecastHour>> hoursByDay = hourRepository
                .findByDayBetweenOrderByDayAscHourAsc(from, to).stream()
                .collect(Collectors.groupingBy(ForecastHour::getDay, LinkedHashMap::new,
                        Collectors.toList()));

        List<Day> dayViews = new ArrayList<>();
        int staffHoursTotal = 0;
        for (ForecastDay day : forecastDays) {
            List<Hour> hours = hoursByDay.getOrDefault(day.getDay(), List.of()).stream()
                    .map(h -> new Hour(h.getHour(), h.getExpectedOrders(), h.getOrdersHigh(),
                            staffFor(h.getOrdersHigh(), staffing)))
                    .toList();
            int staffHours = hours.stream().mapToInt(Hour::staff).sum();
            int peakStaff = hours.stream().mapToInt(Hour::staff).max().orElse(0);
            staffHoursTotal += staffHours;
            dayViews.add(new Day(
                    day.getDay(), day.getExpectedOrders(), day.getOrdersLow(), day.getOrdersHigh(),
                    day.getExpectedRevenue(), day.getRevenueLow(), day.getRevenueHigh(),
                    day.getTempMin(), day.getTempMax(), day.getRainyHours(),
                    split(day.getEvents()), split(day.getOffers()),
                    staffHours, peakStaff, hours));
        }

        ForecastDay busiest = forecastDays.stream()
                .max(Comparator.comparing(ForecastDay::getExpectedRevenue)).orElseThrow();
        double expectedOrders = forecastDays.stream().mapToDouble(ForecastDay::getExpectedOrders).sum();
        BigDecimal expectedRevenue = sum(forecastDays, ForecastDay::getExpectedRevenue);
        // The period's range comes from the total, not from adding up the daily
        // ranges: quiet and busy days partly cancel out over a week.
        Summary summary = new Summary(
                expectedOrders,
                expectedRevenue,
                scale(expectedRevenue, PoissonQuantile.of(expectedOrders, 0.10), expectedOrders),
                scale(expectedRevenue, PoissonQuantile.of(expectedOrders, BUSY_CASE), expectedOrders),
                staffHoursTotal,
                busiest.getDay());

        ForecastDay first = forecastDays.get(0);
        return new AdminForecastResponse(first.getGeneratedAt(), first.getBasis(),
                from, to, staffing, summary, dayViews, products(from, to));
    }

    /** Staff needed in an hour: enough for the busy case, never below the minimum. */
    static int staffFor(int ordersHigh, Staffing staffing) {
        int needed = (int) Math.ceil(ordersHigh / staffing.ordersPerStaffHour());
        return Math.max(staffing.minStaff(), needed);
    }

    private List<ProductNeed> products(LocalDate from, LocalDate to) {
        Map<Long, List<ForecastProduct>> byProduct = productRepository.findByDayBetween(from, to)
                .stream()
                .collect(Collectors.groupingBy(ForecastProduct::getMenuItemId));

        return byProduct.values().stream()
                .map(rows -> {
                    ForecastProduct any = rows.get(0);
                    double expected = rows.stream().mapToDouble(ForecastProduct::getExpectedUnits).sum();
                    BigDecimal revenue = any.getUnitPrice()
                            .multiply(BigDecimal.valueOf(expected))
                            .setScale(2, RoundingMode.HALF_UP);
                    return new ProductNeed(any.getMenuItemId(), any.getProductLabel(),
                            any.getCategory(), any.getSubcategory(), round1(expected),
                            PoissonQuantile.of(expected, BUSY_CASE), revenue);
                })
                .sorted(Comparator.comparing(ProductNeed::expectedUnits).reversed())
                .toList();
    }

    private static List<String> split(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joined.split("\\s*\\|\\s*")).filter(s -> !s.isBlank()).toList();
    }

    private static BigDecimal sum(List<ForecastDay> days,
                                  java.util.function.Function<ForecastDay, BigDecimal> value) {
        return days.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal scale(BigDecimal revenue, int orders, double expectedOrders) {
        if (expectedOrders <= 0) {
            return revenue;
        }
        return revenue.multiply(BigDecimal.valueOf(orders / expectedOrders))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
