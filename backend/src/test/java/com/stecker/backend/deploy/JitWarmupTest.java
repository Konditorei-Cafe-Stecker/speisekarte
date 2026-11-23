package com.stecker.backend.deploy;

import com.stecker.backend.repository.CustomerOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The warm-up runs against the real port, gets the expected answers and writes nothing. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "stecker.warmup.requests=20")
@ActiveProfiles("test")
class JitWarmupTest {

    @Autowired private JitWarmup warmup;
    @Autowired private CustomerOrderRepository orders;

    @Test
    void sendsGuestRequestsThatAllGetTheExpectedAnswer() throws Exception {
        JitWarmup.Result result = warmup.warmUp();

        // 20 rounds on 2 threads, 3 requests each: menu 200, unknown order 404, empty order 400
        assertEquals(60, result.expectedAnswers());
        assertEquals(0, result.noAnswer());
        assertEquals(0, result.unexpectedStatus());
    }

    @Test
    void createsNoOrders() throws Exception {
        long before = orders.count();  // other tests share the in-memory database
        warmup.warmUp();
        assertEquals(before, orders.count());
    }
}
