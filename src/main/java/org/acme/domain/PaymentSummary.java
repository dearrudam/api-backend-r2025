package org.acme.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.stream.Stream;

public record PaymentSummary(Integer totalRequests,
                             @JsonFormat(shape = JsonFormat.Shape.NUMBER)
                             BigDecimal totalAmount) {
    public static PaymentSummary ZERO = new PaymentSummary(0, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_DOWN));

    public static PaymentSummary of(Integer totalRequests, BigDecimal totalAmount) {
        return new PaymentSummary(totalRequests, totalAmount.setScale(2, RoundingMode.HALF_DOWN));
    }

    public PaymentSummary add(Payment payment) {
        if (payment == null) {
            return this;
        }
        return new PaymentSummary(
                totalRequests + 1,
                totalAmount.add(payment.amount()).setScale(2, RoundingMode.HALF_DOWN)
        );
    }

    public PaymentSummary addAll(Collection<Payment>payments) {
        if (payments == null) {
            return this;
        }
        return addAll(payments.stream());
    }

    public PaymentSummary addAll(Payment...payments) {
        if (payments == null || payments.length == 0) {
            return this;
        }
        return addAll(Stream.of(payments));
    }

    private PaymentSummary addAll(Stream<Payment> payments) {
        return payments
                .parallel()
                .map(PaymentSummary.ZERO::add)
                .reduce(PaymentSummary.ZERO, PaymentSummary::add, PaymentSummary::add);
    }

    public PaymentSummary add(PaymentSummary other) {
        return new PaymentSummary(
                this.totalRequests + other.totalRequests,
                this.totalAmount.add(other.totalAmount).setScale(2, RoundingMode.HALF_DOWN)
        );
    }
}