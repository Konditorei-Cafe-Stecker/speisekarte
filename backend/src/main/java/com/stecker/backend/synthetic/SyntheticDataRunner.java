package com.stecker.backend.synthetic;

import com.stecker.backend.context.ContextDataImporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Runs once when the app starts with the "synthetic" profile:
 * 1. imports the real Bremen weather/football/event data,
 * 2. generates and saves the synthetic order history.
 *
 *   ./gradlew bootRun --args='--spring.profiles.active=local,synthetic'
 */
@Component
@ConditionalOnProperty(prefix = "stecker.synthetic", name = "generate", havingValue = "true")
@EnableConfigurationProperties(SyntheticDataProperties.class)
public class SyntheticDataRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SyntheticDataRunner.class);

    private final ContextDataImporter contextImporter;
    private final SyntheticOrderService orderService;
    private final SyntheticDataProperties properties;

    public SyntheticDataRunner(
            ContextDataImporter contextImporter,
            SyntheticOrderService orderService,
            SyntheticDataProperties properties
    ) {
        this.contextImporter = contextImporter;
        this.orderService = orderService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        ContextDataImporter.ImportSummary context = contextImporter.importAll();
        log.info("Imported real context: {} weather hours, {} Werder home matches, "
                        + "{} city events",
                context.weatherHours(), context.footballMatches(), context.cityEvents());

        SyntheticOrderService.Summary summary = orderService.generate(
                properties.start(), properties.end(), properties.seed(), properties.replace());

        log.info("Synthetic orders {}–{} (seed {}): replaced {}, created {} "
                        + "({} confirmed, {} cancelled), {} lines, confirmed revenue {} EUR",
                summary.start(), summary.end(), summary.seed(), summary.deletedOrders(),
                summary.orders(), summary.confirmed(), summary.cancelled(),
                summary.lines(), summary.confirmedRevenue());
    }
}
