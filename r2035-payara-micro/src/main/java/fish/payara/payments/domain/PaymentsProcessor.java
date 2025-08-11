package fish.payara.payments.domain;

public interface PaymentsProcessor {
    void queue(PaymentRequest paymentRequest);
}
