package org.acme.infrastructure;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.domain.Payment;
import org.acme.domain.PaymentSummary;
import org.acme.domain.Payments;
import org.acme.domain.PaymentsSummary;
import org.acme.domain.RemotePaymentName;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summarizingDouble;

public class InMemoryPayments implements Payments {

    private final ConcurrentLinkedQueue<Payment> payments = new ConcurrentLinkedQueue<>();

    @Override
    public PaymentsSummary getSummary(Instant from, Instant to) {
        final Map<RemotePaymentName, PaymentSummary> summaryMap = new HashMap<>();
        var paymentsByProcessedBy = currentPayments()
                .stream()
                .parallel()
                .filter(Payment.createdOn(from, to))
                .collect(groupingBy(Payment::processedBy, summarizingDouble(p -> p.amount().doubleValue())));
        paymentsByProcessedBy.forEach((name, statistics) -> {
            var summary = summaryMap.getOrDefault(name, PaymentSummary.ZERO)
                    .add(PaymentSummary.of(statistics::getCount, statistics::getSum));
            summaryMap.put(name, summary);
        });
        return PaymentsSummary.of(summaryMap);
    }

    @Override
    public TransactionOperations newPaymentTransaction() {
        return new ImMemoryTransactionOperations();
    }

    class ImMemoryTransactionOperations implements TransactionOperations {

        private Payment payment;

        @Override
        public void prepare(Payment payment) {
            this.payment = payment;
        }

        @Override
        public void commit(Payment payment) {
            if (payment != null) {
                InMemoryPayments.this.add(payment);
            }
        }

        @Override
        public void rollback(Payment payment, Throwable throwable) {
            // No action needed for in-memory storage
        }
    }

    private List<Payment> currentPayments() {
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
