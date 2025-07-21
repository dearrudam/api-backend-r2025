package org.acme.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.*;

class PaymentSummaryTest {

    @Test
    void testZeroConstant() {
        PaymentSummary zero = PaymentSummary.ZERO;

        assertThat(zero.totalRequests()).isZero();
        assertThat(zero.totalAmount()).isEqualTo(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_DOWN));
    }

    @Test
    void testOf() {
        PaymentSummary summary = PaymentSummary.of(5, BigDecimal.valueOf(100.255));

        assertThat(summary.totalRequests()).isEqualTo(5);
        assertThat(summary.totalAmount()).isEqualTo(BigDecimal.valueOf(100.25));
    }

    @Test
    void testAddSinglePayment() {
        Payment payment = Payment.of(
            "corr-1",
            RemotePaymentName.DEFAULT,
            BigDecimal.valueOf(10.50),
            Instant.now()
        );

        PaymentSummary summary = PaymentSummary.ZERO.add(payment);

        assertThat(summary.totalRequests()).isEqualTo(1);
        assertThat(summary.totalAmount()).isEqualTo(BigDecimal.valueOf(10.50).setScale(2, RoundingMode.HALF_DOWN));
    }

    @Test
    void testAddNullPayment() {
        PaymentSummary summary = PaymentSummary.ZERO.add((Payment) null);

        assertThat(summary).isSameAs(PaymentSummary.ZERO);
    }

    @Test
    void testAddMultiplePayments() {
        Payment payment1 = Payment.of(
            "corr-2",
            RemotePaymentName.DEFAULT,
            BigDecimal.valueOf(5.25),
            Instant.now()
        );
        Payment payment2 = Payment.of(
            "corr-3",
            RemotePaymentName.DEFAULT,
            BigDecimal.valueOf(4.75),
            Instant.now()
        );

        PaymentSummary summary = PaymentSummary.ZERO.add(payment1).add(payment2);

        assertThat(summary.totalRequests()).isEqualTo(2);
        assertThat(summary.totalAmount()).isEqualTo(BigDecimal.valueOf(10.00).setScale(2, RoundingMode.HALF_DOWN));
    }

    @Test
    void testAddAllWithCollection() {
        Payment payment1 = Payment.of("corr-4", RemotePaymentName.DEFAULT, BigDecimal.valueOf(15.00), Instant.now());
        Payment payment2 = Payment.of("corr-5", RemotePaymentName.DEFAULT, BigDecimal.valueOf(25.50), Instant.now());

        PaymentSummary summary = PaymentSummary.ZERO.addAll(Arrays.asList(payment1, payment2));

        assertThat(summary.totalRequests()).isEqualTo(2);
        assertThat(summary.totalAmount()).isEqualTo(BigDecimal.valueOf(40.50).setScale(2, RoundingMode.HALF_DOWN));
    }

    @Test
    void testAddAllWithEmptyCollection() {
        PaymentSummary summary = PaymentSummary.ZERO.addAll(Collections.emptyList());

        assertThat(summary).isSameAs(PaymentSummary.ZERO);
    }

    @Test
    void testAddAllWithNullCollection() {
        PaymentSummary summary = PaymentSummary.ZERO.addAll((java.util.Collection<Payment>) null);

        assertThat(summary).isSameAs(PaymentSummary.ZERO);
    }

    @Test
    void testAddAllWithVarArgs() {
        Payment payment1 = Payment.of("corr-6", RemotePaymentName.DEFAULT, BigDecimal.valueOf(7.25), Instant.now());
        Payment payment2 = Payment.of("corr-7", RemotePaymentName.DEFAULT, BigDecimal.valueOf(2.75), Instant.now());
        Payment payment3 = Payment.of("corr-8", RemotePaymentName.DEFAULT, BigDecimal.valueOf(5.00), Instant.now());

        PaymentSummary summary = PaymentSummary.ZERO.addAll(payment1, payment2, payment3);

        assertThat(summary.totalRequests()).isEqualTo(3);
        assertThat(summary.totalAmount()).isEqualTo(BigDecimal.valueOf(15.00).setScale(2, RoundingMode.HALF_DOWN));
    }

    @Test
    void testAddAllWithNullVarArgs() {
        PaymentSummary summary = PaymentSummary.ZERO.addAll((Payment[]) null);

        assertThat(summary).isSameAs(PaymentSummary.ZERO);
    }

    @Test
    void testAddAllWithEmptyVarArgs() {
        PaymentSummary summary = PaymentSummary.ZERO.addAll();

        assertThat(summary).isSameAs(PaymentSummary.ZERO);
    }

    @Test
    void testAddPaymentSummary() {
        PaymentSummary summary1 = PaymentSummary.of(3, BigDecimal.valueOf(30.00));
        PaymentSummary summary2 = PaymentSummary.of(2, BigDecimal.valueOf(20.00));

        PaymentSummary result = summary1.add(summary2);

        assertThat(result.totalRequests()).isEqualTo(5);
        assertThat(result.totalAmount()).isEqualTo(BigDecimal.valueOf(50.00).setScale(2, RoundingMode.HALF_DOWN));
    }

    @Test
    void testRoundingBehavior() {
        Payment payment = Payment.of(
            "corr-9",
            RemotePaymentName.DEFAULT,
            BigDecimal.valueOf(10.555),
            Instant.now()
        );

        PaymentSummary summary = PaymentSummary.ZERO.add(payment);

        assertThat(summary.totalAmount()).isEqualTo(BigDecimal.valueOf(10.55));
    }
}
