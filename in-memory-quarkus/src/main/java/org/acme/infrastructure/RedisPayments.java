package org.acme.infrastructure;

import org.acme.domain.Payment;
import org.acme.domain.PaymentSummary;
import org.acme.domain.Payments;
import org.acme.domain.PaymentsSummary;
import org.acme.domain.RemotePaymentName;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summarizingDouble;

public class RedisPayments implements Payments {

    private final static String HASH = "payments";
    private final RedisExecutor redisExecutor;

    public RedisPayments(RedisExecutor redisExecutor) {
        this.redisExecutor = redisExecutor;
    }

    @Override
    public PaymentsSummary getSummary(Instant from, Instant to) {
        return redisExecutor.retrieve(ctx -> getSummary(ctx, from, to));
    }

    public static PaymentsSummary getSummary(final RedisExecutor.RedisContext ctx, Instant from, Instant to) {

        final Map<RemotePaymentName, PaymentSummary> summaryMap = new HashMap<>();

        var paymentsByProcessedBy = ctx.jedis()
                .hgetAll(HASH)
                .values()
                .stream()
                .parallel()
                .map(json -> ctx.decodeFromJSON(json, Payment.class))
                .filter(Payment.createdOn(from, to))
                .collect(groupingBy(Payment::processedBy, summarizingDouble(p -> p.amount().doubleValue())));
        paymentsByProcessedBy.forEach((name, statistics) -> {
            var summary = summaryMap.getOrDefault(name, PaymentSummary.ZERO)
                    .add(PaymentSummary.of(statistics::getCount, statistics::getSum));
            summaryMap.put(name, summary);
        });
        return PaymentsSummary.of(summaryMap);
    }

    @Override
    public void add(Payment payment) {
        if (payment == null)
            return;
        redisExecutor.execute(ctx -> add(ctx, payment));
    }

    public static void add(RedisExecutor.RedisContext ctx, Payment newPayment) {
        ctx.jedis().hsetnx(HASH, newPayment.correlationId(), ctx.encodeToJSON(newPayment));
    }

    public void purge() {
        redisExecutor.execute(RedisPayments::purge);
    }

    public static void purge(RedisExecutor.RedisContext ctx) {
        var jedis = ctx.jedis();
        jedis.keys(HASH + "*")
                .stream()
                .forEach(jedis::del);
    }
}
