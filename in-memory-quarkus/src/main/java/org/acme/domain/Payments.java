package org.acme.domain;

import java.time.Instant;


public interface Payments {

    void add(Payment payment);

    void purge();

    PaymentsSummary getSummary(Instant from, Instant to);

}
