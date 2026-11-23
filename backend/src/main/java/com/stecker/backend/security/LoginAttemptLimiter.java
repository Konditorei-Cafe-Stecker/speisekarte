package com.stecker.backend.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing.
 *
 * After 5 failed sign-ins for one username, or 20 from one network
 * address, within 15 minutes, further attempts are refused until the
 * oldest failure is 15 minutes old. Kept in memory: a restart clears it.
 * With several backend copies each copy counts on its own, so with n
 * copies up to n times as many attempts get through; still far too few
 * to guess a password.
 */
@Component
public class LoginAttemptLimiter {

    static final int MAX_PER_USERNAME = 5;
    static final int MAX_PER_ADDRESS = 20;
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String username, String address) {
        Instant now = clock.instant();
        return count(userKey(username), now) >= MAX_PER_USERNAME
                || count(addressKey(address), now) >= MAX_PER_ADDRESS;
    }

    public void recordFailure(String username, String address) {
        Instant now = clock.instant();
        if (failures.size() > MAX_TRACKED_KEYS) {
            failures.values().removeIf(times -> {
                synchronized (times) {
                    prune(times, now);
                    return times.isEmpty();
                }
            });
        }
        add(userKey(username), now);
        add(addressKey(address), now);
    }

    public void recordSuccess(String username) {
        failures.remove(userKey(username));
    }

    /** Forgets all failures. Used by tests; a restart has the same effect. */
    public void clear() {
        failures.clear();
    }

    private void add(String key, Instant now) {
        Deque<Instant> times = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (times) {
            prune(times, now);
            times.addLast(now);
        }
    }

    private int count(String key, Instant now) {
        Deque<Instant> times = failures.get(key);
        if (times == null) {
            return 0;
        }
        synchronized (times) {
            prune(times, now);
            return times.size();
        }
    }

    private static void prune(Deque<Instant> times, Instant now) {
        Instant oldestAllowed = now.minus(WINDOW);
        while (!times.isEmpty() && times.peekFirst().isBefore(oldestAllowed)) {
            times.removeFirst();
        }
    }

    private static String userKey(String username) {
        return "user:" + (username == null ? "" : username.strip().toLowerCase(Locale.ROOT));
    }

    private static String addressKey(String address) {
        return "addr:" + address;
    }
}
