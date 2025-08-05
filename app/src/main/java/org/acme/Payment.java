package org.acme;

import jakarta.nosql.Column;
import jakarta.nosql.Entity;
import jakarta.nosql.Id;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
public record Payment(
        @Id
        String correlationId,
        @Column
        BigDecimal amount,
        @Column
        PaymentType type,
        @Column
        Instant requestedAt) {

    public static Payment create(PaymentRequest paymentRequest) {
        return new Payment(paymentRequest.correlationId(),
                paymentRequest.amount(),
                null,
                Instant.now());
    }

    public enum PaymentType {
        DEFAULT,
        FALLBACK
    }

    public Payment withType(PaymentType type) {
        return new Payment(correlationId, amount, type, requestedAt);
    }
}