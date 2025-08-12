package fish.payara.payments.domain;

import java.math.BigDecimal;

public record PaymentRequest(String correlationId, BigDecimal amount) { }
