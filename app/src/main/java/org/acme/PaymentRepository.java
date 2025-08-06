package org.acme;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.list.ListCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class PaymentRepository {

    private final String KEY = "payments";
    private final KeyCommands<String> key;
    private final ListCommands<String, Payment> commands;

    @Inject
    public PaymentRepository(RedisDataSource ds) {
        this.commands = ds.list(Payment.class);
        this.key = ds.key();
    }

    public Payment savePayment(Payment payment) {
        this.commands.lpush(KEY, payment);
        return payment;
    }

    public Payment saveAsDefaultPayment(Payment payment) {
        return savePayment(payment.withType(Payment.PaymentType.DEFAULT));
    }

    public Payment saveAsFallbackPayment(Payment payment) {
        return savePayment(payment.withType(Payment.PaymentType.FALLBACK));
    }

    public Map<String, Summary> summary(Instant from, Instant to) {

        Predicate<Payment> filter = payment -> {
            if (from == null && to == null) {
                return true;
            }
            Instant requestedAt = payment.requestedAt();
            return (from == null || (from.isBefore(requestedAt) || from.equals(requestedAt)))
                    &&
                    (to == null || (to.isAfter(requestedAt) || to.equals(requestedAt)));
        };

        Stream<Payment> stream= this.commands
                .lrange(KEY, 0, -1)
                .stream()
                .parallel()
                .filter(filter);

        // Group a parallel stream by payment type and collect into a summary

        Map<String, Summary> summary = stream
                .collect(Collectors.groupingBy(
                        payment -> payment.type().name().toLowerCase(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                payments ->
                                        new Summary(
                                                Long.valueOf(payments.size()),
                                                payments.stream().map(Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                                        )
                        )
                ));
        // Ensure all payment types are represented in the summary
        Arrays.stream(Payment.PaymentType.values())
                .forEach(type -> summary.putIfAbsent(type.name().toLowerCase(), new Summary(0L, BigDecimal.ZERO)));
        return summary;
    }

    public void deleteAll() {
        this.key.del(KEY);
    }
}
