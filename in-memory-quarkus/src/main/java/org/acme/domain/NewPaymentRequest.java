package org.acme.domain;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.time.Instant;

@RegisterForReflection
public record NewPaymentRequest(String correlationId, BigDecimal amount) {

    public RemotePaymentRequest toNewPayment() {
        return new RemotePaymentRequest(correlationId, amount, Instant.now());
    }

}