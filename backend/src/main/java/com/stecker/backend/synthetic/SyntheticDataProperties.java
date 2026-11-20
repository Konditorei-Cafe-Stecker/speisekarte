package com.stecker.backend.synthetic;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.LocalDate;

/**
 * Settings for a generator run, e.g.
 * --stecker.synthetic.seed=20261120 --stecker.synthetic.replace=true
 */
@ConfigurationProperties(prefix = "stecker.synthetic")
public record SyntheticDataProperties(
        @DefaultValue("false") boolean generate,
        @DefaultValue("20261120") long seed,
        @DefaultValue("2025-11-01") LocalDate start,
        @DefaultValue("2026-10-31") LocalDate end,
        @DefaultValue("false") boolean replace
) {
}
