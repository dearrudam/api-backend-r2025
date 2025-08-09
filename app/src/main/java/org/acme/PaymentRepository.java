package org.acme;

import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.keys.ReactiveKeyCommands;
import io.quarkus.redis.datasource.list.ReactiveListCommands;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.unchecked.Unchecked;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

@ApplicationScoped
public class PaymentRepository {

    private final String KEY = "payments";
    private final ReactiveListCommands<String, Payment> commands;
    private final ReactiveKeyCommands<String> key;


    @Inject
    public PaymentRepository(ReactiveRedisDataSource ds) {
        this.commands = ds.list(Payment.class);
        this.key = ds.key();
    }

    public Uni<Payment> savePayment(Payment payment) {
        return this.commands.lpush(KEY, payment)
                .onItem().transform(Unchecked.function(count -> {
                    if (count == null || count <= 0) {
                        throw new RuntimeException("Failed to save payment");
                    }
                    return payment;
                }));
    }

    public Uni<Payment> saveAsDefaultPayment(Payment payment) {
        return savePayment(payment.withType(Payment.PaymentType.DEFAULT));
    }

    public Uni<Payment> saveAsFallbackPayment(Payment payment) {
        return savePayment(payment.withType(Payment.PaymentType.FALLBACK));
    }

    public Uni<Map<String, Summary>> summary(Instant from, Instant to) {

        Predicate<Payment> filter = payment -> {
            if (from == null && to == null) {
                return true;
            }
            Instant requestedAt = payment.requestedAt();
            return (from == null || (from.isBefore(requestedAt) || from.equals(requestedAt)))
                    &&
                    (to == null || (to.isAfter(requestedAt) || to.equals(requestedAt)));
        };

        return this.commands
                .lrange(KEY, 0, -1)
                .flatMap(data -> {
                    Map<String, Summary> summary = ofNullable(data)
                            .stream()
                            .flatMap(Collection::stream)
                            .filter(filter)
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
                    Arrays.stream(Payment.PaymentType.values())
                            .forEach(type -> summary.putIfAbsent(type.name().toLowerCase(), new Summary(0L, BigDecimal.ZERO)));
                    return Uni.createFrom().item(summary);
                });
    }

    public Uni<?> deleteAll() {
        return this.key.del(KEY);
    }
}
