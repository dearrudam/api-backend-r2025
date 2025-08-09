package org.acme.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;

class PaymentSummaryTest {

    @Test
    void testZeroConstant() {
        PaymentSummary zero = PaymentSummary.ZERO;

        assertThat(zero.totalRequests()).isZero();
        assertThat(zero.totalAmount()).isEqualTo(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_DOWN));
    }

    @Test
    void testConstructorWithValidValues() {
        PaymentSummary summary = new PaymentSummary(5L, BigDecimal.valueOf(100.256));

        assertThat(summary.totalRequests()).isEqualTo(5L);
        assertThat(summary.totalAmount()).isEqualTo(BigDecimal.valueOf(100.26).setScale(2, RoundingMode.HALF_DOWN));
    }

    @Test
    void testConstructorWithNullTotalRequests() {
        assertThatThrownBy(() -> new PaymentSummary(null, BigDecimal.valueOf(100)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Total requests must not be null");
    }

    @Test
    void testConstructorWithNegativeTotalRequests() {
        assertThatThrownBy(() -> new PaymentSummary(-1L, BigDecimal.valueOf(100)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Total requests must be non-negative");
    }

    @Test
    void testConstructorWithZeroTotalRequests() {
        PaymentSummary summary = new PaymentSummary(0L, BigDecimal.valueOf(50.75));

        assertThat(summary.totalRequests()).isZero();
        assertThat(summary.totalAmount()).isEqualTo(BigDecimal.valueOf(50.75).setScale(2, RoundingMode.HALF_DOWN));
    }

    @Test
    void testOfWithLongAndBigDecimal() {
        PaymentSummary summary = PaymentSummary.of(3L, BigDecimal.valueOf(75.999));

        assertThat(summary.totalRequests()).isEqualTo(3L);
        assertThat(summary.totalAmount()).isEqualTo(BigDecimal.valueOf(76.00).setScale(2, RoundingMode.HALF_DOWN));
    }

    @Test
    void testOfWithNumbers() {
        PaymentSummary summary = PaymentSummary.of(5, 100.256);

        assertThat(summary.totalRequests()).isEqualTo(5L);
        assertThat(summary.totalAmount()).isEqualTo(BigDecimal.valueOf(100.26).setScale(2, RoundingMode.HALF_DOWN));
    }

    @Test
    void testOfWithSuppliers() {
        Supplier<Number> requestsSupplier = () -> 7;
        Supplier<Number> amountSupplier = () -> 150.789;

        PaymentSummary summary = PaymentSummary.of(requestsSupplier, amountSupplier);

        assertThat(summary.totalRequests()).isEqualTo(7L);
        assertThat(summary.totalAmount()).isEqualTo(BigDecimal.valueOf(150.79).setScale(2, RoundingMode.HALF_DOWN));
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

        assertThat(summary.totalRequests()).isEqualTo(1L);
        assertThat(summary.totalAmount()).isEqualTo(BigDecimal.valueOf(10.50).setScale(2, RoundingMode.HALF_DOWN));
    }

    @Test
    void testAddNullPayment() {
        PaymentSummary summary = PaymentSummary.ZERO.add((Payment) null);

        assertThat(summary).isSameAs(PaymentSummary.ZERO);
    }

    @Test
    void testAddPaymentToExistingSummary() {
        PaymentSummary initial = PaymentSummary.of(2L, BigDecimal.valueOf(20.00));
        Payment payment = Payment.of("corr-1", RemotePaymentName.DEFAULT, BigDecimal.valueOf(5.75), Instant.now());

        PaymentSummary result = initial.add(payment);

        assertThat(result.totalRequests()).isEqualTo(3L);
        assertThat(result.totalAmount()).isEqualTo(BigDecimal.valueOf(25.75).setScale(2, RoundingMode.HALF_DOWN));
    }

    @Test
    void testAddPaymentSummary() {
        PaymentSummary summary1 = PaymentSummary.of(3L, BigDecimal.valueOf(30.00));
        PaymentSummary summary2 = PaymentSummary.of(2L, BigDecimal.valueOf(20.50));

        PaymentSummary result = summary1.add(summary2);

        assertThat(result.totalRequests()).isEqualTo(5L);
        assertThat(result.totalAmount()).isEqualTo(BigDecimal.valueOf(50.50).setScale(2, RoundingMode.HALF_DOWN));
    }

    @Test
    void testAddNullPaymentSummary() {
        PaymentSummary summary = PaymentSummary.of(1L, BigDecimal.valueOf(10.00));

        PaymentSummary result = summary.add((PaymentSummary) null);

        assertThat(result).isSameAs(summary);
    }

    @Test
    void testAddZeroPaymentSummary() {
        PaymentSummary summary = PaymentSummary.of(3L, BigDecimal.valueOf(25.00));

        PaymentSummary result = summary.add(PaymentSummary.ZERO);

        assertThat(result.totalRequests()).isEqualTo(3L);
        assertThat(result.totalAmount()).isEqualTo(BigDecimal.valueOf(25.00).setScale(2, RoundingMode.HALF_DOWN));
    }

    @Test
    void testAddAllWithValidCollection() {
        Payment payment1 = Payment.of("corr-2", RemotePaymentName.DEFAULT, BigDecimal.valueOf(15.25), Instant.now());
        Payment payment2 = Payment.of("corr-3", RemotePaymentName.DEFAULT, BigDecimal.valueOf(8.75), Instant.now());
        Payment payment3 = Payment.of("corr-4", RemotePaymentName.DEFAULT, BigDecimal.valueOf(12.00), Instant.now());

        PaymentSummary summary = PaymentSummary.ZERO.addAll(Arrays.asList(payment1, payment2, payment3));

        assertThat(summary.totalRequests()).isEqualTo(3L);
        assertThat(summary.totalAmount()).isEqualTo(BigDecimal.valueOf(36.00).setScale(2, RoundingMode.HALF_DOWN));
    }

    @Test
    void testAddAllWithEmptyCollection() {
        PaymentSummary summary = PaymentSummary.ZERO.addAll(Collections.emptyList());

        assertThat(summary.totalRequests()).isZero();
        assertThat(summary.totalAmount()).isEqualTo(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_DOWN));
    }

    @Test
    void testAddAllWithNullCollection() {
        PaymentSummary summary = PaymentSummary.ZERO.addAll(null);

        assertThat(summary).isSameAs(PaymentSummary.ZERO);
    }

    @Test
    void testAddAllWithCollectionContainingNulls() {
        Payment payment1 = Payment.of("corr-5", RemotePaymentName.DEFAULT, BigDecimal.valueOf(10.00), Instant.now());
        Payment payment2 = null;
        Payment payment3 = Payment.of("corr-6", RemotePaymentName.DEFAULT, BigDecimal.valueOf(15.50), Instant.now());

        PaymentSummary summary = PaymentSummary.ZERO.addAll(Arrays.asList(payment1, payment2, payment3));

        assertThat(summary.totalRequests()).isEqualTo(2L);
        assertThat(summary.totalAmount()).isEqualTo(BigDecimal.valueOf(25.50).setScale(2, RoundingMode.HALF_DOWN));
    }

    @Test
    void testAddAllToExistingSummary() {
        PaymentSummary initial = PaymentSummary.of(1L, BigDecimal.valueOf(5.00));
        Payment payment1 = Payment.of("corr-7", RemotePaymentName.DEFAULT, BigDecimal.valueOf(7.25), Instant.now());
        Payment payment2 = Payment.of("corr-8", RemotePaymentName.DEFAULT, BigDecimal.valueOf(2.75), Instant.now());

        PaymentSummary result = initial.addAll(List.of(payment1, payment2));

        assertThat(result.totalRequests()).isEqualTo(3L);
        assertThat(result.totalAmount()).isEqualTo(BigDecimal.valueOf(15.00).setScale(2, RoundingMode.HALF_DOWN));
    }

    @Test
    void testRoundingBehaviorHalfDown() {
        PaymentSummary summary1 = PaymentSummary.of(1L, BigDecimal.valueOf(10.554)); // rounds to 10.55
        PaymentSummary summary2 = PaymentSummary.of(1L, BigDecimal.valueOf(10.555)); // rounds to 10.55 (half down)
        PaymentSummary summary3 = PaymentSummary.of(1L, BigDecimal.valueOf(10.556)); // rounds to 10.56

        assertThat(summary1.totalAmount()).isEqualTo(BigDecimal.valueOf(10.55).setScale(2, RoundingMode.HALF_DOWN));
        assertThat(summary2.totalAmount()).isEqualTo(BigDecimal.valueOf(10.55).setScale(2, RoundingMode.HALF_DOWN));
        assertThat(summary3.totalAmount()).isEqualTo(BigDecimal.valueOf(10.56).setScale(2, RoundingMode.HALF_DOWN));
    }

    @Test
    void testLargeNumbers() {
        PaymentSummary summary = PaymentSummary.of(Long.MAX_VALUE, new BigDecimal("999999999.999"));

        assertThat(summary.totalRequests()).isEqualTo(Long.MAX_VALUE);
        assertThat(summary.totalAmount()).isEqualTo(new BigDecimal("1000000000.00").setScale(2, RoundingMode.HALF_DOWN));
    }

    @Test
    void testImmutability() {
        PaymentSummary original = PaymentSummary.of(2L, BigDecimal.valueOf(20.00));
        Payment payment = Payment.of("corr-9", RemotePaymentName.DEFAULT, BigDecimal.valueOf(5.00), Instant.now());

        PaymentSummary modified = original.add(payment);

        assertThat(original.totalRequests()).isEqualTo(2L);
        assertThat(original.totalAmount()).isEqualTo(BigDecimal.valueOf(20.00).setScale(2, RoundingMode.HALF_DOWN));
        assertThat(modified.totalRequests()).isEqualTo(3L);
        assertThat(modified.totalAmount()).isEqualTo(BigDecimal.valueOf(25.00).setScale(2, RoundingMode.HALF_DOWN));
        assertThat(modified).isNotSameAs(original);
    }

    @Test
    void testChainedOperations() {
        Payment payment1 = Payment.of("corr-10", RemotePaymentName.DEFAULT, BigDecimal.valueOf(10.00), Instant.now());
        Payment payment2 = Payment.of("corr-11", RemotePaymentName.DEFAULT, BigDecimal.valueOf(15.00), Instant.now());
        PaymentSummary summary1 = PaymentSummary.of(1L, BigDecimal.valueOf(5.00));

        // Primeiro adiciona payment1 (cria novo summary com 1 request e 10.00)
        // Depois adiciona payment2 (cria novo summary com 1 request e 15.00)
        // Por último adiciona summary1 (soma: 1+1=2 requests, 15.00+5.00=20.00)
        PaymentSummary result = PaymentSummary.ZERO
                .add(payment1)
                .add(payment2)
                .add(summary1);

        assertThat(result.totalRequests()).isEqualTo(3L);
        assertThat(result.totalAmount()).isEqualTo(BigDecimal.valueOf(30.00).setScale(2, RoundingMode.HALF_DOWN));
    }

    @Test
    void testParallelProcessingWithLargeCollection() {
        List<Payment> payments = Arrays.asList(
            Payment.of("corr-12", RemotePaymentName.DEFAULT, BigDecimal.valueOf(1.00), Instant.now()),
            Payment.of("corr-13", RemotePaymentName.DEFAULT, BigDecimal.valueOf(2.00), Instant.now()),
            Payment.of("corr-14", RemotePaymentName.DEFAULT, BigDecimal.valueOf(3.00), Instant.now()),
            Payment.of("corr-15", RemotePaymentName.DEFAULT, BigDecimal.valueOf(4.00), Instant.now()),
            Payment.of("corr-16", RemotePaymentName.DEFAULT, BigDecimal.valueOf(5.00), Instant.now())
        );

        PaymentSummary summary = PaymentSummary.ZERO.addAll(payments);

        assertThat(summary.totalRequests()).isEqualTo(5L);
        assertThat(summary.totalAmount()).isEqualTo(BigDecimal.valueOf(15.00).setScale(2, RoundingMode.HALF_DOWN));
    }
}
