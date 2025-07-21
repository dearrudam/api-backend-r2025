package org.acme.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.function.Predicate;

public record Payment(String correlationId,
                      RemotePaymentName processedBy,
                      BigDecimal amount,
                      Instant createAt) {

    public static Payment of(String correlationId,
                             RemotePaymentName processedBy,
                             BigDecimal amount,
                             Instant createAt) {
        return new Payment(correlationId,
                processedBy,
                amount,
                createAt
        );
    }

    public static Predicate<Payment> createdOn(Instant from, Instant to) {
        Predicate<Payment> fromWasOmitted = unused -> from == null;
        Predicate<Payment> toWasOmitted = unused -> to == null;

        Predicate<Payment> afterOrEqualFrom = payment -> from != null && from.isBefore(payment.createAt()) || from.equals(payment.createAt());
        Predicate<Payment> beforeOrEqualTo = payment -> to != null && to.isAfter(payment.createAt()) || to.equals(payment.createAt());

        Predicate<Payment> fromTo = fromWasOmitted.or(afterOrEqualFrom)
                .and(toWasOmitted.or(beforeOrEqualTo));
        return fromTo;
    }
}
