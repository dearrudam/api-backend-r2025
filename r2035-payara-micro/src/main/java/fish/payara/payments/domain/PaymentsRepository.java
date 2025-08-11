package fish.payara.payments.domain;

import java.time.Instant;

public interface PaymentsRepository {

    void purge();

    PaymentsSummary summary(Instant from, Instant to);
}
