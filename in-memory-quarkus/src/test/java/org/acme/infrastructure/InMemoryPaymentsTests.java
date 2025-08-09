package org.acme.infrastructure;

import org.acme.domain.PaymentsTests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

@DisplayName("InMemoryPayments Tests")
class InMemoryPaymentsTests implements PaymentsTests.AllTests {

    private InMemoryPayments payments;

    @BeforeEach
    void setUp() {
        payments = new InMemoryPayments();
    }

    @Override
    public PaymentsTests.Context testContext() {
        return PaymentsTests.Context.of(payments);
    }

}
