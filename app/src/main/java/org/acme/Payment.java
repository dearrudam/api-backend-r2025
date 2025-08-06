package org.acme;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.time.Instant;


@RegisterForReflection
public record Payment(
        String correlationId,
        BigDecimal amount,
        PaymentType type,
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