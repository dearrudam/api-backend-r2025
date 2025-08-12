package fish.payara.payments.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record ProcessedPayment(String correlationId, String processedBy, BigDecimal amount, Instant requestedAt) {

    public static ProcessedPayment defaultPayment(PaymentRequest paymentRequest) {
        return of("default", paymentRequest);
    }

    public static ProcessedPayment fallbackPayment(PaymentRequest paymentRequest) {
        return of("fallback", paymentRequest);
    }

    public static ProcessedPayment of(String processedBy, PaymentRequest paymentRequest) {
        return new ProcessedPayment(
                paymentRequest.correlationId(),
                processedBy,
                paymentRequest.amount(),
                Instant.now()
        );
    }

}
