package org.acme.infrastructure;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.domain.Payment;
import org.acme.domain.PaymentSummary;
import org.acme.domain.Payments;
import org.acme.domain.PaymentsSummary;
import org.acme.domain.RemotePaymentName;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summarizingDouble;

@ApplicationScoped
public class InMemoryPayments implements Payments {

    public static final int DEFAULT_PARALLEL_STREAM_THRESHOLD = 1_000_000;

    private final ConcurrentLinkedQueue<Payment> payments = new ConcurrentLinkedQueue<>();

    private final int parallelStreamThreshold;

    public InMemoryPayments(
            @ConfigProperty(name = "payments.parallel.stream.threshold", defaultValue = "100_000")
            int parallelStreamThreshold) {
        this.parallelStreamThreshold = parallelStreamThreshold;
    }

    @Override
    public PaymentsSummary getSummary(Instant from, Instant to) {

        Map<RemotePaymentName, PaymentSummary> summary = getStream()
                .filter(Payment.createdOn(from, to))
                .collect(Collectors.groupingBy(
                        payment -> payment.processedBy(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                payments ->
                                        new PaymentSummary(
                                                Long.valueOf(payments.size()),
                                                payments.stream().map(Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                                        )
                        )
                ));

        return PaymentsSummary.of(summary);
    }

    private Stream<Payment> getStream() {
        List<Payment> payments = snapshot();
        if (payments.size() > parallelStreamThreshold)
            return payments.stream().parallel();
        return payments.stream();
    }

    private List<Payment> snapshot() {
        return new ArrayList<>(payments);
    }

    @Override
    public void add(Payment payment) {
        if (payment == null)
            return;
        this.payments.offer(payment);
    }

    @Override
    public void purge() {
        this.payments.clear();
    }
}
