package com.stecker.backend.context;

/**
 * Published after a successful context update, so work that needs the
 * newest weather (the daily synthetic orders and forecast) runs right after.
 *
 * @param trigger "daily" or "startup"
 */
public record ContextUpdatedEvent(String trigger) {
}
