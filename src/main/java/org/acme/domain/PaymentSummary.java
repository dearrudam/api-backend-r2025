package org.acme.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public record PaymentSummary(Long totalRequests,
                             @JsonFormat(shape = JsonFormat.Shape.NUMBER)
                             BigDecimal totalAmount) {
    public static PaymentSummary ZERO = new PaymentSummary(0L, BigDecimal.ZERO);

    public PaymentSummary {
        totalRequests = Objects.requireNonNull(totalRequests, "Total requests must not be null");
        if (totalRequests < 0) {
            throw new IllegalArgumentException("Total requests must be non-negative");
        }
        totalAmount = totalAmount.setScale(2, RoundingMode.HALF_DOWN);
    }

    public static PaymentSummary of(Long totalRequests, BigDecimal totalAmount) {
        return new PaymentSummary(totalRequests, totalAmount);
    }

    public static PaymentSummary of(Number totalRequests, Number totalAmount) {
        return new PaymentSummary(totalRequests.longValue(), BigDecimal.valueOf(totalAmount.doubleValue()));
    }

    public static PaymentSummary of(Supplier<? extends Number> totalRequestsSupplier,
                                    Supplier<? extends Number> totalAmountSupplier) {
        return of(totalRequestsSupplier.get(), totalAmountSupplier.get());
    }

    public PaymentSummary add(Payment payment) {
        if (payment == null) {
            return this;
        }
        return addAll(List.of(payment));
    }

    public PaymentSummary add(PaymentSummary paymentSummary) {
        if (paymentSummary == null) {
            return this;
        }
        var newTotalRequests = this.totalRequests + paymentSummary.totalRequests;
        BigDecimal newTotalAmount = this.totalAmount.add(paymentSummary.totalAmount);
        return PaymentSummary.of(newTotalRequests, newTotalAmount);
    }

    public PaymentSummary addAll(Collection<Payment> payments) {
        if (payments == null) {
            return this;
        }
        var totalRequests = new AtomicLong(this.totalRequests);
        var totalAmount = payments.stream()
                .parallel()
                .filter(Objects::nonNull)
                .peek(p -> totalRequests.incrementAndGet())
                .map(Payment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add, BigDecimal::add);
        return PaymentSummary.of(totalRequests.get(), this.totalAmount.add(totalAmount));
    }

}