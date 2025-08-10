package org.acme.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


@ApplicationScoped
public class PaymentProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentProcessor.class);
    private final DefaultRemotePaymentProcessor defaultRemotePaymentProcessor;
    private final FallbackRemotePaymentProcessor fallbackRemotePaymentProcessor;
    private final int retries;
    private final Map<String, AtomicInteger> errorCounter = new ConcurrentHashMap<>();

    @Inject
    public PaymentProcessor(
            @RestClient
            DefaultRemotePaymentProcessor defaultRemotePaymentProcessor,
            @RestClient
            FallbackRemotePaymentProcessor fallbackRemotePaymentProcessor,
            @ConfigProperty(name = "retries.before.fallback", defaultValue = "16")
            int retries
    ) {
        this.defaultRemotePaymentProcessor = defaultRemotePaymentProcessor;
        this.fallbackRemotePaymentProcessor = fallbackRemotePaymentProcessor;
        this.retries = retries;
    }

    public Optional<Payment> sendPayment(NewPaymentRequest newPaymentRequest) {
        try {
            RemotePaymentRequest request = newPaymentRequest.toNewPayment();
            var response = defaultRemotePaymentProcessor.processPayment(request);
            return switch (response.getStatus()) {
                case 200 -> {
                    errorCounter.remove(newPaymentRequest.correlationId());
                    yield Optional.of(RemotePaymentName.DEFAULT.toPayment(request));
                }
                case 500 -> errorCounter
                        .computeIfAbsent(newPaymentRequest.correlationId(), k -> new AtomicInteger())
                        .incrementAndGet() > retries ? fallbackSendPayment(newPaymentRequest) : Optional.empty();
                default -> Optional.empty();
            };
        } catch (RuntimeException e) {
            errorCounter
                    .computeIfAbsent(newPaymentRequest.correlationId(), k -> new AtomicInteger())
                    .incrementAndGet();
            return Optional.empty();
        }
    }

    public Optional<Payment> fallbackSendPayment(NewPaymentRequest newPaymentRequest) {
        final RemotePaymentRequest request = newPaymentRequest.toNewPayment();
        try {
            var response = fallbackRemotePaymentProcessor.processPayment(request);
            return switch (response.getStatus()) {
                case 200 -> {
                    errorCounter.remove(newPaymentRequest.correlationId());
                    yield Optional.of(RemotePaymentName.FALLBACK.toPayment(request));
                }
                default -> Optional.empty();
            };
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

}