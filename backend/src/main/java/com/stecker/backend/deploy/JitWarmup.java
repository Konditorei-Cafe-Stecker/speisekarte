package com.stecker.backend.deploy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Warms up the guest endpoints before the backend reports itself ready.
 *
 * A fresh JVM runs the code interpreted at first and compiles it while
 * requests come in. In a rush, when Kubernetes adds copies, those cold
 * copies answer slowly. This runner sends the requests guests send
 * (menu, order status, an order) to its own port before Spring Boot marks
 * the app as ready, so Kubernetes only routes guests to a warm copy.
 *
 * Nothing is written: the status check asks for an unknown order (404) and
 * the order has no items (400), so only reading, JSON and validation run.
 * Off unless {@code stecker.warmup.requests} is above 0 (set in Kubernetes).
 */
@Component
@ConditionalOnExpression("${stecker.warmup.requests:0} > 0")
public class JitWarmup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JitWarmup.class);
    private static final int THREADS = 2;

    private final Environment environment;
    private final int rounds;

    public JitWarmup(Environment environment, @Value("${stecker.warmup.requests}") int rounds) {
        this.environment = environment;
        this.rounds = rounds;
    }

    @Override
    public void run(ApplicationArguments args) throws InterruptedException {
        String port = environment.getProperty("local.server.port");
        if (port == null) {
            return;
        }
        String base = "http://127.0.0.1:" + port;
        long started = System.nanoTime();
        AtomicInteger failures = new AtomicInteger();

        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build()) {
            ExecutorService pool = Executors.newFixedThreadPool(THREADS);
            for (int t = 0; t < THREADS; t++) {
                pool.submit(() -> {
                    for (int i = 0; i < rounds / THREADS; i++) {
                        send(client, get(base + "/api/menu"), failures);
                        send(client, get(base + "/api/orders/" + UUID.randomUUID()), failures);
                        send(client, HttpRequest.newBuilder(URI.create(base + "/api/orders"))
                                .timeout(Duration.ofSeconds(5))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString("{\"items\":[]}"))
                                .build(), failures);
                    }
                });
            }
            pool.shutdown();
            pool.awaitTermination(3, TimeUnit.MINUTES);
        }
        log.info("Warm-up: {} rounds of guest requests in {} ms ({} failed to connect)",
                rounds, (System.nanoTime() - started) / 1_000_000, failures.get());
    }

    private static HttpRequest get(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build();
    }

    private static void send(HttpClient client, HttpRequest request, AtomicInteger failures) {
        try {
            client.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception error) {
            failures.incrementAndGet();
        }
    }
}
