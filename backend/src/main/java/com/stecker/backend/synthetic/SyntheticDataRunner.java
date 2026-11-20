package com.stecker.backend.synthetic;

import com.stecker.backend.context.ContextDataImporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
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
    private final ApplicationContext context;

    public SyntheticDataRunner(
            ContextDataImporter contextImporter,
            SyntheticOrderService orderService,
            SyntheticDataProperties properties,
            ApplicationContext context
    ) {
        this.contextImporter = contextImporter;
        this.orderService = orderService;
        this.properties = properties;
        this.context = context;
    }

    /** Generates the history, then stops the app (exit code 1 on failure). */
    @Override
    public void run(ApplicationArguments args) {
        int exitCode = 0;
        try {
            generate();
        } catch (RuntimeException error) {
            log.error("Synthetic data generation failed: {}", error.getMessage());
            exitCode = 1;
        }
        int code = exitCode;
        System.exit(SpringApplication.exit(context, () -> code));
    }

    private void generate() {
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
