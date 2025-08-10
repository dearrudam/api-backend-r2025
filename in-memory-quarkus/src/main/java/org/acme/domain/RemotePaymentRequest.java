package org.acme.domain;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@RegisterForReflection
public record RemotePaymentRequest(String correlationId,
                                   BigDecimal amount,
                                   Instant requestedAt) {

    public RemotePaymentRequest {
        requestedAt = Optional.ofNullable(requestedAt).orElse(Instant.now());
    }

}