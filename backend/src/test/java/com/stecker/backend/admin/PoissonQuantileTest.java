package com.stecker.backend.admin;

import com.stecker.backend.admin.AdminForecastResponse.Staffing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoissonQuantileTest {

    @Test
    void matchesKnownPoissonQuantiles() {
        // Reference values from scipy.stats.poisson.ppf(0.9, mean)
        assertEquals(0, PoissonQuantile.of(0, 0.9));
        assertEquals(2, PoissonQuantile.of(1, 0.9));
        assertEquals(8, PoissonQuantile.of(5, 0.9));
        assertEquals(14, PoissonQuantile.of(10, 0.9));
        assertEquals(59, PoissonQuantile.of(50, 0.9));
    }

    @Test
    void normalApproximationIsCloseForLargeMeans() {
        int quantile = PoissonQuantile.of(400, 0.9);  // exact value: 426
        assertTrue(quantile >= 425 && quantile <= 427, "got " + quantile);
    }

    @Test
    void staffCoversTheBusyCaseButNeverGoesBelowTheMinimum() {
        Staffing staffing = new Staffing(6, 2);
        assertEquals(2, AdminForecastService.staffFor(3, staffing));
        assertEquals(2, AdminForecastService.staffFor(12, staffing));
        assertEquals(3, AdminForecastService.staffFor(13, staffing));
        assertEquals(4, AdminForecastService.staffFor(13, new Staffing(4, 1)));
    }
}
