package org.acme.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public interface PaymentsTests {

    // Helper method to create test payments
    static Payment createPayment(String correlationId, RemotePaymentName processor, BigDecimal amount, Instant createdOn) {
        return Payment.of(correlationId, processor, amount, createdOn);
    }

    record Context(Payments payments, Instant baseTime, Instant fromTime, Instant toTime) {

        public static Context of(Payments payments) {
            return new Context(payments, null, null, null);
        }

        public Context {
            Objects.requireNonNull(payments, "Payments instance must not be null");
            baseTime = Instant.parse("2025-07-21T10:00:00Z");
            fromTime = baseTime.minus(1, ChronoUnit.HOURS);
            toTime = baseTime.plus(1, ChronoUnit.HOURS);
        }

    }

    Context testContext();

    @DisplayName("TCK Payments")
    interface AllTests extends
            AddPaymentTests,
            PurgeTests,
            GetSummaryTests,
            ConcurrencyTests,
            IntegrationTests {
    }

    @DisplayName("Add Payment Tests")
    interface AddPaymentTests extends PaymentsTests {

        @Test
        @DisplayName("Should add single valid payment with DEFAULT processor")
        default void shouldAddSingleValidPaymentWithDefaultProcessor() {
            var context = testContext();
            var payments = context.payments();
            var baseTime = context.baseTime();
            var fromTime = context.fromTime();
            var toTime = context.toTime();

            Payment payment = createPayment("corr-1", RemotePaymentName.DEFAULT, BigDecimal.valueOf(100.00), baseTime);

            payments.add(payment);

            PaymentsSummary summary = payments.getSummary(fromTime, toTime);
            assertThat(summary.defaultPaymentSummary().totalRequests()).isEqualTo(1L);
            assertThat(summary.defaultPaymentSummary().totalAmount()).isEqualTo(BigDecimal.valueOf(100.00).setScale(2, RoundingMode.HALF_DOWN));
            assertThat(summary.fallbackPaymentSummary().totalRequests()).isZero();
        }

        @Test
        @DisplayName("Should add single valid payment with FALLBACK processor")
        default void shouldAddSingleValidPaymentWithFallbackProcessor() {
            var context = testContext();
            var payments = context.payments();
            var baseTime = context.baseTime();
            var fromTime = context.fromTime();
            var toTime = context.toTime();

            Payment payment = createPayment("corr-1", RemotePaymentName.FALLBACK, BigDecimal.valueOf(150.00), baseTime);

            payments.add(payment);

            PaymentsSummary summary = payments.getSummary(fromTime, toTime);
            assertThat(summary.fallbackPaymentSummary().totalRequests()).isEqualTo(1L);
            assertThat(summary.fallbackPaymentSummary().totalAmount()).isEqualTo(BigDecimal.valueOf(150.00).setScale(2, RoundingMode.HALF_DOWN));
            assertThat(summary.defaultPaymentSummary().totalRequests()).isZero();
        }

        @Test
        @DisplayName("Should add multiple payments to same processor")
        default void shouldAddMultiplePaymentsToSameProcessor() {
            var context = testContext();
            var payments = context.payments();
            var baseTime = context.baseTime();
            var fromTime = context.fromTime();
            var toTime = context.toTime();

            Payment payment1 = createPayment("corr-1", RemotePaymentName.DEFAULT, BigDecimal.valueOf(50.00), baseTime);
            Payment payment2 = createPayment("corr-2", RemotePaymentName.DEFAULT, BigDecimal.valueOf(75.50), baseTime);

            payments.add(payment1);
            payments.add(payment2);

            PaymentsSummary summary = payments.getSummary(fromTime, toTime);
            assertThat(summary.defaultPaymentSummary().totalRequests()).isEqualTo(2L);
            assertThat(summary.defaultPaymentSummary().totalAmount()).isEqualTo(BigDecimal.valueOf(125.50).setScale(2, RoundingMode.HALF_DOWN));
        }

        @Test
        @DisplayName("Should handle null payment gracefully")
        default void shouldHandleNullPaymentGracefully() {
            var context = testContext();
            var payments = context.payments();
            var fromTime = context.fromTime();
            var toTime = context.toTime();

            payments.add(null);

            PaymentsSummary summary = payments.getSummary(fromTime, toTime);
            assertThat(summary.defaultPaymentSummary().totalRequests()).isZero();
            assertThat(summary.fallbackPaymentSummary().totalRequests()).isZero();
        }

        @Test
        @DisplayName("Should add payments with different processors")
        default void shouldAddPaymentsWithDifferentProcessors() {
            var context = testContext();
            var payments = context.payments();
            var baseTime = context.baseTime();
            var fromTime = context.fromTime();
            var toTime = context.toTime();

            Payment payment1 = createPayment("corr-1", RemotePaymentName.DEFAULT, BigDecimal.valueOf(100.00), baseTime);
            Payment payment2 = createPayment("corr-2", RemotePaymentName.FALLBACK, BigDecimal.valueOf(200.00), baseTime);

            payments.add(payment1);
            payments.add(payment2);

            PaymentsSummary summary = payments.getSummary(fromTime, toTime);

            assertThat(summary.defaultPaymentSummary().totalRequests()).isEqualTo(1L);
            assertThat(summary.defaultPaymentSummary().totalAmount()).isEqualTo(BigDecimal.valueOf(100.00).setScale(2, RoundingMode.HALF_DOWN));

            assertThat(summary.fallbackPaymentSummary().totalRequests()).isEqualTo(1L);
            assertThat(summary.fallbackPaymentSummary().totalAmount()).isEqualTo(BigDecimal.valueOf(200.00).setScale(2, RoundingMode.HALF_DOWN));
        }
    }

    @DisplayName("Purge Tests")
    interface PurgeTests extends PaymentsTests {

        @Test
        @DisplayName("Should purge empty repository")
        default void shouldPurgeEmptyRepository() {
            var context = testContext();
            var payments = context.payments();
            var fromTime = context.fromTime();
            var toTime = context.toTime();

            payments.purge();

            PaymentsSummary summary = payments.getSummary(fromTime, toTime);
            assertThat(summary.defaultPaymentSummary().totalRequests()).isZero();
            assertThat(summary.fallbackPaymentSummary().totalRequests()).isZero();
        }

        @Test
        @DisplayName("Should purge repository with payments")
        default void shouldPurgeRepositoryWithPayments() {
            var context = testContext();
            var payments = context.payments();
            var baseTime = context.baseTime();
            var fromTime = context.fromTime();
            var toTime = context.toTime();

            Payment payment1 = createPayment("corr-1", RemotePaymentName.DEFAULT, BigDecimal.valueOf(100.00), baseTime);
            Payment payment2 = createPayment("corr-2", RemotePaymentName.FALLBACK, BigDecimal.valueOf(200.00), baseTime);

            payments.add(payment1);
            payments.add(payment2);

            payments.purge();

            PaymentsSummary summary = payments.getSummary(fromTime, toTime);
            assertThat(summary.defaultPaymentSummary().totalRequests()).isZero();
            assertThat(summary.fallbackPaymentSummary().totalRequests()).isZero();
        }

        @Test
        @DisplayName("Should allow adding payments after purge")
        default void shouldAllowAddingPaymentsAfterPurge() {
            var context = testContext();
            var payments = context.payments();
            var baseTime = context.baseTime();
            var fromTime = context.fromTime();
            var toTime = context.toTime();

            Payment payment1 = createPayment("corr-1", RemotePaymentName.DEFAULT, BigDecimal.valueOf(100.00), baseTime);
            payments.add(payment1);

            payments.purge();

            Payment payment2 = createPayment("corr-2", RemotePaymentName.DEFAULT, BigDecimal.valueOf(50.00), baseTime);
            payments.add(payment2);

            PaymentsSummary summary = payments.getSummary(fromTime, toTime);
            assertThat(summary.defaultPaymentSummary().totalRequests()).isEqualTo(1L);
            assertThat(summary.defaultPaymentSummary().totalAmount()).isEqualTo(BigDecimal.valueOf(50.00).setScale(2, RoundingMode.HALF_DOWN));
        }
    }

    @DisplayName("Get Summary Tests")
    interface GetSummaryTests extends PaymentsTests {

        @Test
        @DisplayName("Should return empty summary for empty repository")
        default void shouldReturnEmptySummaryForEmptyRepository() {
            var context = testContext();
            var payments = context.payments();
            var fromTime = context.fromTime();
            var toTime = context.toTime();

            PaymentsSummary summary = payments.getSummary(fromTime, toTime);

            assertThat(summary.defaultPaymentSummary().totalRequests()).isZero();
            assertThat(summary.fallbackPaymentSummary().totalRequests()).isZero();
        }

        @Test
        @DisplayName("Should return summary with payments in period")
        default void shouldReturnSummaryWithPaymentsInPeriod() {
            var context = testContext();
            var payments = context.payments();
            var baseTime = context.baseTime();
            var fromTime = context.fromTime();
            var toTime = context.toTime();

            Payment payment = createPayment("corr-1", RemotePaymentName.DEFAULT, BigDecimal.valueOf(150.75), baseTime);
            payments.add(payment);

            PaymentsSummary summary = payments.getSummary(fromTime, toTime);

            assertThat(summary.defaultPaymentSummary().totalRequests()).isEqualTo(1L);
            assertThat(summary.defaultPaymentSummary().totalAmount()).isEqualTo(BigDecimal.valueOf(150.75).setScale(2, RoundingMode.HALF_DOWN));
        }

        @Test
        @DisplayName("Should filter payments outside period")
        default void shouldFilterPaymentsOutsidePeriod() {
            var context = testContext();
            var payments = context.payments();
            var baseTime = context.baseTime();
            var fromTime = context.fromTime();
            var toTime = context.toTime();

            Payment paymentBefore = createPayment("corr-1", RemotePaymentName.DEFAULT, BigDecimal.valueOf(100.00), fromTime.minus(1, ChronoUnit.MINUTES));
            Payment paymentInside = createPayment("corr-2", RemotePaymentName.DEFAULT, BigDecimal.valueOf(200.00), baseTime);
            Payment paymentAfter = createPayment("corr-3", RemotePaymentName.DEFAULT, BigDecimal.valueOf(300.00), toTime.plus(1, ChronoUnit.MINUTES));

            payments.add(paymentBefore);
            payments.add(paymentInside);
            payments.add(paymentAfter);

            PaymentsSummary summary = payments.getSummary(fromTime, toTime);

            assertThat(summary.defaultPaymentSummary().totalRequests()).isEqualTo(1L);
            assertThat(summary.defaultPaymentSummary().totalAmount()).isEqualTo(BigDecimal.valueOf(200.00).setScale(2, RoundingMode.HALF_DOWN));
        }

        @Test
        @DisplayName("Should include payments at period boundaries")
        default void shouldIncludePaymentsAtPeriodBoundaries() {
            var context = testContext();
            var payments = context.payments();
            var fromTime = context.fromTime();
            var toTime = context.toTime();

            Payment paymentAtFrom = createPayment("corr-1", RemotePaymentName.DEFAULT, BigDecimal.valueOf(100.00), fromTime);
            Payment paymentAtTo = createPayment("corr-2", RemotePaymentName.DEFAULT, BigDecimal.valueOf(200.00), toTime);

            payments.add(paymentAtFrom);
            payments.add(paymentAtTo);

            PaymentsSummary summary = payments.getSummary(fromTime, toTime);

            assertThat(summary.defaultPaymentSummary().totalRequests()).isEqualTo(2L);
            assertThat(summary.defaultPaymentSummary().totalAmount()).isEqualTo(BigDecimal.valueOf(300.00).setScale(2, RoundingMode.HALF_DOWN));
        }

        @Test
        @DisplayName("Should group payments by processor")
        default void shouldGroupPaymentsByProcessor() {
            var context = testContext();
            var payments = context.payments();
            var baseTime = context.baseTime();
            var fromTime = context.fromTime();
            var toTime = context.toTime();

            Payment payment1 = createPayment("corr-1", RemotePaymentName.DEFAULT, BigDecimal.valueOf(100.00), baseTime);
            Payment payment2 = createPayment("corr-2", RemotePaymentName.DEFAULT, BigDecimal.valueOf(150.00), baseTime);
            Payment payment3 = createPayment("corr-3", RemotePaymentName.FALLBACK, BigDecimal.valueOf(200.00), baseTime);
            Payment payment4 = createPayment("corr-4", RemotePaymentName.FALLBACK, BigDecimal.valueOf(250.00), baseTime);

            payments.add(payment1);
            payments.add(payment2);
            payments.add(payment3);
            payments.add(payment4);

            PaymentsSummary summary = payments.getSummary(fromTime, toTime);

            assertThat(summary.defaultPaymentSummary().totalRequests()).isEqualTo(2L);
            assertThat(summary.defaultPaymentSummary().totalAmount()).isEqualTo(BigDecimal.valueOf(250.00).setScale(2, RoundingMode.HALF_DOWN));

            assertThat(summary.fallbackPaymentSummary().totalRequests()).isEqualTo(2L);
            assertThat(summary.fallbackPaymentSummary().totalAmount()).isEqualTo(BigDecimal.valueOf(450.00).setScale(2, RoundingMode.HALF_DOWN));
        }

        @Test
        @DisplayName("Should handle decimal precision correctly")
        default void shouldHandleDecimalPrecisionCorrectly() {
            var context = testContext();
            var payments = context.payments();
            var baseTime = context.baseTime();
            var fromTime = context.fromTime();
            var toTime = context.toTime();

            Payment payment1 = createPayment("corr-1", RemotePaymentName.DEFAULT, BigDecimal.valueOf(10.555), baseTime); // 10.555 should be rounded to 10.55
            Payment payment2 = createPayment("corr-2", RemotePaymentName.DEFAULT, BigDecimal.valueOf(20.444), baseTime); // 20.444 should be rounded to 20.44

            payments.add(payment1);
            payments.add(payment2);

            PaymentsSummary summary = payments.getSummary(fromTime, toTime);

            assertThat(summary.defaultPaymentSummary().totalRequests()).isEqualTo(2L);
            // 10.55 + 20.44 = 30.99
            assertThat(summary.defaultPaymentSummary().totalAmount()).isEqualTo(BigDecimal.valueOf(30.99).setScale(2, RoundingMode.HALF_DOWN));
        }
    }

    @DisplayName("Concurrency Tests")
    interface ConcurrencyTests extends PaymentsTests {

        @Test
        @DisplayName("Should handle concurrent additions safely")
        default void shouldHandleConcurrentAdditionsSafely() throws InterruptedException {
            var context = testContext();
            var payments = context.payments();
            var baseTime = context.baseTime();
            var fromTime = context.fromTime();
            var toTime = context.toTime();

            ExecutorService executor = Executors.newFixedThreadPool(10);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // Add 100 payments concurrently
            for (int i = 0; i < 100; i++) {
                final int index = i;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    Payment payment = createPayment("corr-" + index, RemotePaymentName.DEFAULT, BigDecimal.valueOf(10.00), baseTime);
                    payments.add(payment);
                }, executor);
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            PaymentsSummary summary = payments.getSummary(fromTime, toTime);
            assertThat(summary.defaultPaymentSummary().totalRequests()).isEqualTo(100L);
            assertThat(summary.defaultPaymentSummary().totalAmount()).isEqualTo(BigDecimal.valueOf(1000.00).setScale(2, RoundingMode.HALF_DOWN));

            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        @Test
        @DisplayName("Should handle concurrent summary generation safely")
        default void shouldHandleConcurrentSummaryGenerationSafely() throws InterruptedException {
            var context = testContext();
            var payments = context.payments();
            var baseTime = context.baseTime();
            var fromTime = context.fromTime();
            var toTime = context.toTime();

            // Add some payments first
            IntStream.range(0, 50).forEach(i -> {
                Payment payment = createPayment("corr-" + i, RemotePaymentName.DEFAULT, BigDecimal.valueOf(10.00), baseTime);
                payments.add(payment);
            });

            ExecutorService executor = Executors.newFixedThreadPool(10);
            List<CompletableFuture<PaymentsSummary>> futures = new ArrayList<>();

            // Generate 20 summaries concurrently
            for (int i = 0; i < 20; i++) {
                CompletableFuture<PaymentsSummary> future = CompletableFuture.supplyAsync(() ->
                        payments.getSummary(fromTime, toTime), executor);
                futures.add(future);
            }

            List<PaymentsSummary> summaries = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();

            // All summaries should be consistent
            assertThat(summaries).allSatisfy(summary -> {
                assertThat(summary.defaultPaymentSummary().totalRequests()).isEqualTo(50L);
                assertThat(summary.defaultPaymentSummary().totalAmount()).isEqualTo(BigDecimal.valueOf(500.00).setScale(2, RoundingMode.HALF_DOWN));
            });

            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        @Test
        @DisplayName("Should handle concurrent purge safely")
        default void shouldHandleConcurrentPurgeSafely() throws InterruptedException {
            var context = testContext();
            var payments = context.payments();
            var baseTime = context.baseTime();
            var fromTime = context.fromTime();
            var toTime = context.toTime();

            // Add some payments
            IntStream.range(0, 20).forEach(i -> {
                Payment payment = createPayment("corr-" + i, RemotePaymentName.DEFAULT, BigDecimal.valueOf(10.00), baseTime);
                payments.add(payment);
            });

            ExecutorService executor = Executors.newFixedThreadPool(5);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // Multiple purge operations
            for (int i = 0; i < 5; i++) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(payments::purge, executor);
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            PaymentsSummary summary = payments.getSummary(fromTime, toTime);
            assertThat(summary.defaultPaymentSummary().totalRequests()).isZero();
            assertThat(summary.fallbackPaymentSummary().totalRequests()).isZero();

            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @DisplayName("Integration Tests")
    interface IntegrationTests extends PaymentsTests {

        @Test
        @DisplayName("Should handle complete workflow")
        default void shouldHandleCompleteWorkflow() {
            var context = testContext();
            var payments = context.payments();
            var baseTime = context.baseTime();
            var fromTime = context.fromTime();
            var toTime = context.toTime();

            // Add payments
            Payment payment1 = createPayment("corr-1", RemotePaymentName.DEFAULT, BigDecimal.valueOf(100.00), baseTime);
            Payment payment2 = createPayment("corr-2", RemotePaymentName.FALLBACK, BigDecimal.valueOf(200.00), baseTime);

            payments.add(payment1);
            payments.add(payment2);

            // Get summary
            PaymentsSummary summary1 = payments.getSummary(fromTime, toTime);
            assertThat(summary1.defaultPaymentSummary().totalRequests()).isEqualTo(1L);
            assertThat(summary1.fallbackPaymentSummary().totalRequests()).isEqualTo(1L);

            // Add more payments
            Payment payment3 = createPayment("corr-3", RemotePaymentName.DEFAULT, BigDecimal.valueOf(50.00), baseTime);
            payments.add(payment3);

            // Get updated summary
            PaymentsSummary summary2 = payments.getSummary(fromTime, toTime);
            assertThat(summary2.defaultPaymentSummary().totalRequests()).isEqualTo(2L);
            assertThat(summary2.defaultPaymentSummary().totalAmount()).isEqualTo(BigDecimal.valueOf(150.00).setScale(2, RoundingMode.HALF_DOWN));

            // Purge and verify
            payments.purge();
            PaymentsSummary summary3 = payments.getSummary(fromTime, toTime);
            assertThat(summary3.defaultPaymentSummary().totalRequests()).isZero();
            assertThat(summary3.fallbackPaymentSummary().totalRequests()).isZero();
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 5, 10, 50, 100})
        @DisplayName("Should handle various payment counts")
        default void shouldHandleVariousPaymentCounts(int paymentCount) {
            var context = testContext();
            var payments = context.payments();
            var baseTime = context.baseTime();
            var fromTime = context.fromTime();
            var toTime = context.toTime();

            BigDecimal paymentAmount = BigDecimal.valueOf(10.00);

            IntStream.range(0, paymentCount).forEach(i -> {
                Payment payment = createPayment("corr-" + i, RemotePaymentName.DEFAULT, paymentAmount, baseTime);
                payments.add(payment);
            });

            PaymentsSummary summary = payments.getSummary(fromTime, toTime);

            if (paymentCount > 0) {
                assertThat(summary.defaultPaymentSummary().totalRequests()).isEqualTo(paymentCount);
                BigDecimal expectedAmount = paymentAmount.multiply(BigDecimal.valueOf(paymentCount));
                assertThat(summary.defaultPaymentSummary().totalAmount()).isEqualTo(expectedAmount.setScale(2, RoundingMode.HALF_DOWN));
            } else {
                assertThat(summary.defaultPaymentSummary().totalRequests()).isZero();
                assertThat(summary.fallbackPaymentSummary().totalRequests()).isZero();
            }
        }
    }

}
