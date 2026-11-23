package com.stecker.backend.daily;

import com.stecker.backend.context.ContextUpdatedEvent;
import com.stecker.backend.synthetic.SyntheticOrderService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyDataUpdateTest {

    private final SyntheticOrderService orders = mock(SyntheticOrderService.class);
    private final ForecastPublisher forecast = mock(ForecastPublisher.class);

    @Test
    void addsOrdersWithTheHistorysSeedThenPublishes() {
        when(orders.continueHistory(20261120L)).thenReturn(Optional.of(
                new SyntheticOrderService.Continuation(
                        LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 22), 980)));

        new DailyDataUpdate(orders, forecast, true, 20261120L)
                .afterContextUpdate(new ContextUpdatedEvent("daily"));

        verify(orders).continueHistory(20261120L);
        verify(forecast).publishIfOutdated();
    }

    @Test
    void leavesOrdersAloneWhenSwitchedOff() {
        new DailyDataUpdate(orders, forecast, false, 20261120L)
                .afterContextUpdate(new ContextUpdatedEvent("startup"));

        verify(orders, never()).continueHistory(anyLong());
        verify(forecast).publishIfOutdated();
    }

    @Test
    void stillPublishesWhenAddingOrdersFails() {
        when(orders.continueHistory(anyLong())).thenThrow(new IllegalStateException("no weather"));

        new DailyDataUpdate(orders, forecast, true, 20261120L)
                .afterContextUpdate(new ContextUpdatedEvent("daily"));

        verify(forecast).publishIfOutdated();
    }
}
