package org.acme;

import java.math.BigDecimal;

public record Summary(Long totalRequests, BigDecimal totalAmount) {

    public Summary() {
        this(0L, BigDecimal.ZERO);
    }

    public Summary(Long totalRequests, BigDecimal totalAmount) {
        this.totalRequests = totalRequests;
        this.totalAmount = totalAmount;
    }

    public Summary add(Summary other) {
        return new Summary(
                this.totalRequests + other.totalRequests,
                this.totalAmount.add(other.totalAmount)
        );
    }
}
