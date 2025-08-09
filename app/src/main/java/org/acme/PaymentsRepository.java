package org.acme;

import jakarta.data.repository.Param;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import org.eclipse.jnosql.mapping.NoSQLRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Repository
public interface PaymentsRepository extends NoSQLRepository<Payment, String> {

    default Payment saveAsDefaultPayment(Payment payment) {
        return this.insert(payment.withType(Payment.PaymentType.DEFAULT));
    }

    default Payment saveAsFallbackPayment(Payment payment) {
        return this.insert(payment.withType(Payment.PaymentType.FALLBACK));
    }

    @Query("FROM Payment WHERE requestedAt BETWEEN :_from AND :_to")
    Stream<Payment> summary(@Param("_from") Instant from, @Param("_to") Instant to);

    default Map<String, Summary> getSummary(Instant from, Instant to) {
        Stream<Payment> payments = null;
        if (from == null || to == null) {
            payments = findAll();
        } else {
            if (from.isAfter(to)) {
                throw new IllegalArgumentException("The 'from' date must be before the 'to' date.");
            }
            payments = summary(from, to);
        }
        Map<String, Summary> collect = payments
                .parallel()
                .collect(Collectors.groupingBy(p -> p.type().name().toLowerCase(),
                        Collectors.reducing(
                                new Summary(),
                                payment -> new Summary(1L, payment.amount()),
                                Summary::add)));
        Arrays.stream(Payment.PaymentType.values())
                .forEach(type ->
                        collect.computeIfAbsent(
                                type.name().toLowerCase(),
                                k -> new Summary(0L, BigDecimal.ZERO)));
        return collect;
    }

}
