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

        assertEquals(0, result.failedToConnect(), "requests that could not be sent");
        assertEquals(0, result.unexpectedStatus(), "menu 200, unknown order 404, empty order 400");
    }

    @Test
    void createsNoOrders() throws Exception {
        long before = orders.count();  // other tests share the in-memory database
        warmup.warmUp();
        assertEquals(before, orders.count());
    }
}
