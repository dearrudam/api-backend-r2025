package org.acme.payments.infrastructure;

import io.quarkus.runtime.Startup;
import io.quarkus.virtual.threads.VirtualThreads;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.acme.payments.domain.PaymentRequest;
import org.acme.payments.domain.PaymentSummary;
import org.acme.payments.domain.PaymentsProcessor;
import org.acme.payments.domain.PaymentsRepository;
import org.acme.payments.domain.PaymentsSummary;
import org.acme.payments.domain.ProcessedPayment;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.exceptions.JedisException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@ApplicationScoped
public class JedisPayments implements PaymentsProcessor, PaymentsRepository {

    public static final String PAYMENTS_PROCESSED = "payments-processed";

    @Inject
    @ConfigProperty(name = "jedis.url", defaultValue = "redis://localhost:6377")
    private String jedisUrl;

    @Inject
    @ConfigProperty(name = "instanceId", defaultValue = "instance-01")
    private String instanceId;

    @Inject
    @ConfigProperty(name = "workers.size", defaultValue = "20")
    private int workersSize;

    @Inject
    private ExternalPaymentProcessor externalPaymentProcessor;

    private ExecutorService executeService;

    private UnifiedJedis purgeJedis;

    private UnifiedJedis summaryJedis;

    private Jsonb jsonb;
    private String queueName;

    private LinkedBlockingQueue<PaymentRequest> queue = new LinkedBlockingQueue<>();

    @Startup
    public void onApplicationStart() {
        for (int i = 0; i < workersSize; i++) {
            executeService.execute(() -> this.listenForPayments(createUnifiedJedis()));
            System.out.printf("Started worker %d for instance %s%n", i, instanceId);
        }
        this.executeService.execute(() -> {
            UnifiedJedis unifiedJedis = createUnifiedJedis();
            while (true) {
                try {
                    PaymentRequest paymentRequest = queue.take();
                    queueInRedis(unifiedJedis, paymentRequest);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Restore interrupted status
                    break; // Exit the loop if interrupted
                }
            }
        });
        System.out.println("Started worker for queue processing");

    }

    @PostConstruct
    public void postConstruct() {
        this.queueName = "payments-queue-" + instanceId;
        this.jsonb = JsonbBuilder.create();
        this.purgeJedis = createUnifiedJedis();
        this.summaryJedis = createUnifiedJedis();
        this.executeService = Executors.newVirtualThreadPerTaskExecutor();
    }

    private UnifiedJedis createUnifiedJedis() {
        return new UnifiedJedis(jedisUrl);
    }

    @PreDestroy
    public void destroy() {
        if (executeService != null) {
            executeService.shutdown();
            int maxWaitSeconds = 5;
            if (!executeService.isTerminated()) {
                try {
                    Thread.sleep(Duration.ofSeconds(maxWaitSeconds).toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Restore interrupted status
                }
                if (!executeService.isTerminated()) {
                    executeService.shutdownNow();
                }
            }
        }
    }

    private void listenForPayments(UnifiedJedis unifiedJedis) {
        while (!executeService.isShutdown()) {
            try {
                Optional<PaymentRequest> receivedPaymentRequest = Optional.empty();
                // BLPOP returns a list of two elements: the queue name and the message
                var result = unifiedJedis.blpop(0, queueName);
                if (result != null && result.size() == 2) {
                    String message = result.get(1);
                    receivedPaymentRequest = Optional.ofNullable(jsonb.fromJson(message, PaymentRequest.class));
                }

                receivedPaymentRequest.ifPresent(paymentRequest ->
                        externalPaymentProcessor.process(paymentRequest)
                                .ifPresentOrElse(
                                        processedPayment ->
                                                unifiedJedis.lpush(PAYMENTS_PROCESSED, jsonb.toJson(processedPayment)),
                                        () -> {
                                            // If processing fails, re-queue the payment request
                                            queue.offer(paymentRequest);
                                        }));
            } catch (RuntimeException e) {
                // Log the exception or handle it as needed
                if (e instanceof JedisException) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void queue(PaymentRequest paymentRequest) {
        this.queue.offer(paymentRequest);
    }

    private void queueInRedis(UnifiedJedis unifiedJedis, PaymentRequest paymentRequest) {
        unifiedJedis.lpush(queueName, jsonb.toJson(paymentRequest));
    }

    @Override
    public void purge() {
        this.purgeJedis.del(PAYMENTS_PROCESSED);
    }

    @Override
    public PaymentsSummary summary(Instant from, Instant to) {
        Map<String, PaymentSummary> summary = summaryJedis.lrange(PAYMENTS_PROCESSED, 0, -1)
                .stream()
                .map(json -> jsonb.fromJson(json, ProcessedPayment.class))
                .filter(createdOn(from, to))
                .collect(Collectors.groupingBy(
                        payment -> payment.processedBy(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                payments ->
                                        new PaymentSummary(
                                                Long.valueOf(payments.size()),
                                                payments.stream().map(ProcessedPayment::amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                                        )
                        )
                ));
        return PaymentsSummary.of(summary);
    }

    public static Predicate<ProcessedPayment> createdOn(Instant from, Instant to) {
        return payment -> {
            if (from == null && to == null) {
                return true;
            }
            Instant requestedAt = payment.requestedAt();
            return (from == null || (from.isBefore(requestedAt) || from.equals(requestedAt)))
                    &&
                    (to == null || (to.isAfter(requestedAt) || to.equals(requestedAt)));
        };
    }
}
