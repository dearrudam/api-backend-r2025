package org.acme;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static java.time.Duration.ofSeconds;

@ApplicationScoped
public class RemotePaymentProcessor {

    private final PaymentRepository paymentRepository;
    private final URI defaultURL;
    private final URI fallbackURL;
    private final Jsonb jsonb;
    private final HttpClient httpClient;
    private final Map<String, AtomicLong> defaultErrorCount;
    private final Map<String, AtomicLong> fallbackErrorCount;
    private final Long retriesBeforeFallback;

    @Inject
    public RemotePaymentProcessor(
            PaymentRepository paymentRepository,
            @ConfigProperty(name = "default.payment.url")
            URI defaultURL,
            @ConfigProperty(name = "fallback.payment.url")
            URI fallbackURL,
            @ConfigProperty(name = "retries.before.fallback")
            Optional<Long> retriesBeforeFallback,
            Jsonb jsonb) {
        this.paymentRepository = paymentRepository;
        this.defaultURL = defaultURL;
        this.fallbackURL = fallbackURL;
        this.jsonb = jsonb;
        this.retriesBeforeFallback = retriesBeforeFallback.orElse(16L);
        this.defaultErrorCount = new ConcurrentHashMap<>();
        this.fallbackErrorCount = new ConcurrentHashMap<>();
        this.httpClient = builHttpClient();
    }

    public Uni<?> process(PaymentRequest paymentRequest) {
        Payment payment = Payment.create(paymentRequest);
        return Uni.createFrom().completionStage(
                httpClient.sendAsync(createRequest(defaultURL, payment), HttpResponse.BodyHandlers.ofString())
                        .thenApply(response -> {
                            if (response.statusCode() == 200) {
                                return paymentRepository.saveAsDefaultPayment(payment)
                                        .onItem()
                                        .invoke(() -> defaultErrorCount.remove(payment.correlationId())).await().indefinitely();
                            } else {
                                if (response.statusCode() == 500) {
                                    long errors = defaultErrorCount
                                            .computeIfAbsent(payment.correlationId(), k -> new AtomicLong(0))
                                            .incrementAndGet();
                                    if (errors >= retriesBeforeFallback) {
                                        return processFallback(payment).await().indefinitely();
                                    } else {
                                        throw new RuntimeException(
                                                "payment processing on the default processor failed with status code: %s - correlationId: %s - attempts: %s"
                                                        .formatted(response.statusCode(), payment.correlationId(), errors));
                                    }
                                }
                                return null;
                            }
                        }));
    }

    private void sleepIfNeed(Payment payment) {
        long errorNumber = defaultErrorCount
                .computeIfAbsent(payment.correlationId(), k -> new AtomicLong(0))
                .get();
        long delay = errorNumber * 100;
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                // Restore the interrupted status
            }
        }
    }


    private Uni<?> processFallback(Payment payment) {
        System.out.printf("Processing fallback payment for correlationId %s%n", payment.correlationId());
        return Uni.createFrom().completionStage(
                httpClient.sendAsync(createRequest(fallbackURL, payment), HttpResponse.BodyHandlers.ofString())
                        .thenApply(response -> {
                            if (response.statusCode() == 200) {
                                return paymentRepository.saveAsFallbackPayment(payment)
                                        .onItem()
                                        .invoke(() -> fallbackErrorCount.remove(payment.correlationId()));
                            } else {
                                long errors = fallbackErrorCount
                                        .computeIfAbsent(payment.correlationId(), k -> new AtomicLong(0))
                                        .incrementAndGet();
                                throw new RuntimeException("payment processing on the fallback processor failed with status code: %s - correlationId: %s - attempts: %s"
                                        .formatted(response.statusCode(), payment.correlationId(), errors));
                            }
                        }));
    }

    private HttpRequest createRequest(URI defaultURL, Payment payment) {
        String json = jsonb.toJson(payment);
        return HttpRequest.newBuilder(defaultURL)
                .header("Content-Type", "application/json")
                .timeout(ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
    }

    private HttpClient builHttpClient() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }
}
