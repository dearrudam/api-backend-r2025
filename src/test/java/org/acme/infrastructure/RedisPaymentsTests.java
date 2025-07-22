package org.acme.infrastructure;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.acme.domain.PaymentsTests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

@QuarkusTest
@DisplayName("RedisPayments Tests")
public class RedisPaymentsTests implements PaymentsTests.AllTests {

    @Inject
    RedisExecutor redisExecutor;

    @BeforeEach
    void beforeEach() {
        redisExecutor.execute(ctx -> RedisPayments.purge(ctx));
    }

    @Override
    public Context testContext() {
        return Context.of(new RedisPayments(redisExecutor));
    }
}
