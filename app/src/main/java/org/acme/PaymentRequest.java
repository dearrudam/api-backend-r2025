package org.acme;

import java.math.BigDecimal;

public record PaymentRequest(String correlationId, BigDecimal amount) {

}
