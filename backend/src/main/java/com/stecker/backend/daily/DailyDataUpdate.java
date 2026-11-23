package com.stecker.backend.daily;

import com.stecker.backend.context.ContextUpdatedEvent;
import com.stecker.backend.synthetic.SyntheticOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Runs right after each context update (every morning and once after the
 * backend starts), when the newest weather is in:
 *
 * 1. continues the synthetic order history up to yesterday, if
 *    stecker.synthetic.daily-orders.enabled=true (local profile: there are
 *    no real guests yet, so the dashboard and forecast need fresh history);
 * 2. publishes a new forecast for the coming week, if today's is missing or
 *    older than today (see {@link ForecastPublisher}).
 *
 * Failures are logged and tried again next time; they never stop the backend.
 */
@Component
public class DailyDataUpdate {

    private static final Logger log = LoggerFactory.getLogger(DailyDataUpdate.class);

    private final SyntheticOrderService syntheticOrders;
    private final ForecastPublisher forecastPublisher;
    private final boolean dailyOrders;
    private final long seed;

    public DailyDataUpdate(
            SyntheticOrderService syntheticOrders,
            ForecastPublisher forecastPublisher,
            @Value("${stecker.synthetic.daily-orders.enabled:false}") boolean dailyOrders,
            // The seed the history was generated with, so it continues seamlessly.
            @Value("${stecker.synthetic.seed:20261120}") long seed
    ) {
        this.syntheticOrders = syntheticOrders;
        this.forecastPublisher = forecastPublisher;
        this.dailyOrders = dailyOrders;
        this.seed = seed;
    }

    @EventListener
    public void afterContextUpdate(ContextUpdatedEvent event) {
        if (dailyOrders) {
            try {
                syntheticOrders.continueHistory(seed).ifPresentOrElse(
                        added -> log.info("Synthetic orders ({}): added {} orders for {} – {}",
                                event.trigger(), added.orders(), added.from(), added.to()),
                        () -> log.info("Synthetic orders ({}): up to date", event.trigger()));
            } catch (RuntimeException error) {
                log.warn("Synthetic orders ({}) failed, will try again next time: {}",
                        event.trigger(), error.getMessage());
            }
        }

        try {
            forecastPublisher.publishIfOutdated();
        } catch (RuntimeException error) {
            log.warn("Publishing the forecast failed, will try again next time: {}",
                    error.getMessage());
        }
    }
}
