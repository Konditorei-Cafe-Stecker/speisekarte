package com.stecker.backend.deploy;

import com.stecker.backend.repository.CustomerOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The warm-up runs against the real port and writes nothing. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "stecker.warmup.requests=20")
@ActiveProfiles("test")
class JitWarmupTest {

    @Autowired private JitWarmup warmup;
    @Autowired private CustomerOrderRepository orders;

    @Test
    void warmsUpWithoutCreatingOrders() throws Exception {
        assertNotNull(warmup);  // it ran at startup and the app still started

        long before = orders.count();  // other tests share the in-memory database
        warmup.run(null);
        assertEquals(before, orders.count());
    }
}
