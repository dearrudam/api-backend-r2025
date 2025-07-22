package org.acme.domain;

import java.time.Instant;


public interface Payments {

    void add(Payment payment);

    void purge();

    PaymentsSummary getSummary(Instant from, Instant to);

    TransactionOperations newPaymentTransaction();

    interface TransactionOperations {
        void prepare(Payment payment);
        void commit(Payment payment);
        void rollback(Payment payment, Throwable throwable);
    }
}
