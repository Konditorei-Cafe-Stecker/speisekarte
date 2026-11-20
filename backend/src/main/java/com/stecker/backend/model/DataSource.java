package com.stecker.backend.model;

/**
 * Where an order came from.
 *
 * REAL      - placed by a guest through the website.
 * SYNTHETIC - generated for the simulated order history.
 *             Results based on these orders are simulation results.
 */
public enum DataSource {
    REAL,
    SYNTHETIC
}
