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
 * Requests without the expected answer (another status, no answer within
 * 5 s, or not sent at all) are counted and logged as a warning: then the
 * warm-up did not exercise what it should. It never stops the backend.
 * Off unless {@code stecker.warmup.requests} is above 0 (set in Kubernetes).
 */
@Component
@ConditionalOnExpression("${stecker.warmup.requests:0} > 0")
public class JitWarmup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JitWarmup.class);
    private static final int THREADS = 2;

    /**
     * How the warm-up went: answers as expected, requests that were not sent
     * or got no answer in time, and answers with another status.
     */
    record Result(int expectedAnswers, int noAnswer, int unexpectedStatus) {
    }

    private final Environment environment;
    private final int rounds;

    public JitWarmup(Environment environment, @Value("${stecker.warmup.requests}") int rounds) {
        this.environment = environment;
        this.rounds = rounds;
    }

    @Override
    public void run(ApplicationArguments args) throws InterruptedException {
        warmUp();
    }

    Result warmUp() throws InterruptedException {
        String port = environment.getProperty("local.server.port");
        if (port == null) {
            log.warn("Warm-up skipped: the web server port is not known.");
            return new Result(0, 0, 0);
        }
        String base = "http://127.0.0.1:" + port;
        long started = System.nanoTime();
        AtomicInteger expected = new AtomicInteger();
        AtomicInteger noAnswer = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build()) {
            ExecutorService pool = Executors.newFixedThreadPool(THREADS);
            for (int t = 0; t < THREADS; t++) {
                pool.submit(() -> {
                    for (int i = 0; i < rounds / THREADS; i++) {
                        send(client, get(base + "/api/menu"), 200, expected, noAnswer, unexpected);
                        send(client, get(base + "/api/orders/" + UUID.randomUUID()), 404,
                                expected, noAnswer, unexpected);
                        send(client, HttpRequest.newBuilder(URI.create(base + "/api/orders"))
                                .timeout(Duration.ofSeconds(5))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString("{\"items\":[]}"))
                                .build(), 400, expected, noAnswer, unexpected);
                    }
                });
            }
            pool.shutdown();
            if (!pool.awaitTermination(3, TimeUnit.MINUTES)) {
                pool.shutdownNow();
                log.warn("Warm-up stopped after 3 minutes before all rounds were done.");
            }
        }

        Result result = new Result(expected.get(), noAnswer.get(), unexpected.get());
        long millis = (System.nanoTime() - started) / 1_000_000;
        if (result.noAnswer() > 0 || result.unexpectedStatus() > 0) {
            log.warn("Warm-up: {} rounds in {} ms, but {} requests were not sent or got no "
                            + "answer in time and {} got an unexpected answer; the first guests "
                            + "may meet a cold JVM.",
                    rounds, millis, result.noAnswer(), result.unexpectedStatus());
        } else {
            log.info("Warm-up: {} rounds of guest requests in {} ms", rounds, millis);
        }
        return result;
    }

    private static HttpRequest get(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build();
    }

    private static void send(HttpClient client, HttpRequest request, int expectedStatus,
                             AtomicInteger expected, AtomicInteger noAnswer,
                             AtomicInteger unexpected) {
        try {
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            (status == expectedStatus ? expected : unexpected).incrementAndGet();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            noAnswer.incrementAndGet();
        } catch (Exception notSentOrTimedOut) {
            noAnswer.incrementAndGet();
        }
    }
}
