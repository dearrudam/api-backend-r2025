package org.acme.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.function.Predicate;

public record Payment(String correlationId, RemotePaymentName processedBy, BigDecimal amount, Instant requestedAt) {

    public static Payment of(String correlationId, RemotePaymentName processedBy, BigDecimal amount, Instant createAt) {
        return new Payment(correlationId, processedBy, amount, createAt);
    }

    public static Predicate<Payment> createdOn(Instant from, Instant to) {
        return payment -> {
            if (from == null && to == null) {
                return true;
            }
            Instant requestedAt = payment.requestedAt();
            return (from == null || (from.isBefore(requestedAt) || from.equals(requestedAt)))
                    &&
                    (to == null || (to.isAfter(requestedAt) || to.equals(requestedAt)));
        };
    }
}