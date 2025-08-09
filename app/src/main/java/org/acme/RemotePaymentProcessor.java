package org.acme;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
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

    private final PaymentService paymentService;
    private final URI defaultURL;
    private final URI fallbackURL;
    private final Jsonb jsonb;
    private final HttpClient httpClient;
    private final Map<String, AtomicLong> defaultErrorCount;
    private final Map<String, AtomicLong> fallbackErrorCount;
    private final Long retriesBeforeFallback;

    @Inject
    public RemotePaymentProcessor(PaymentService paymentService,
                                  @ConfigProperty(name = "default.payment.url")
                                  URI defaultURL,
                                  @ConfigProperty(name = "fallback.payment.url")
                                  URI fallbackURL,
                                  @ConfigProperty(name = "retries.before.fallback")
                                  Optional<Long> retriesBeforeFallback,
                                  Jsonb jsonb) {
        this.paymentService = paymentService;
        this.defaultURL = defaultURL;
        this.fallbackURL = fallbackURL;
        this.jsonb = jsonb;
        this.retriesBeforeFallback = retriesBeforeFallback.orElse(16L);
        this.defaultErrorCount = new ConcurrentHashMap<>();
        this.fallbackErrorCount = new ConcurrentHashMap<>();
        this.httpClient = builHttpClient();
    }

    public void process(PaymentRequest paymentRequest) {
        Payment payment = Payment.create(paymentRequest);
        try {
            var request = createRequest(defaultURL, payment);
            var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 200) {
                paymentService.saveAsDefaultPayment(payment);
                defaultErrorCount.remove(payment.correlationId());
            } else {
                if (response.statusCode() == 500) {
                    long errors = defaultErrorCount
                            .computeIfAbsent(payment.correlationId(), k -> new AtomicLong(0))
                            .incrementAndGet();
                    if (errors >= retriesBeforeFallback) {
                        processFallback(payment);
                        defaultErrorCount.remove(payment.correlationId());
                    } else {
                        throw new RuntimeException(
                                "payment processing on the default processor failed with status code: %s - correlationId: %s - attempts: %s"
                                        .formatted(response.statusCode(), payment.correlationId(), errors));
                    }
                }
                throw new UnsupportedOperationException("payment processing on the default processor failed with status code: " + response.statusCode());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new UnsupportedOperationException(e);
        } finally {
            sleepIfNeed(payment);
        }
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


    private void processFallback(Payment payment) throws IOException, InterruptedException {
        System.out.printf("Processing fallback payment for correlationId %s%n", payment.correlationId());
        // Fallback to the secondary URL
        var request = createRequest(fallbackURL, payment);
        var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() == 200) {
            paymentService.saveAsFallbackPayment(payment);
        } else {
            long errors = fallbackErrorCount
                    .computeIfAbsent(payment.correlationId(), k -> new AtomicLong(0))
                    .incrementAndGet();
            throw new RuntimeException("payment processing on the fallback processor failed with status code: %s - correlationId: %s - attempts: %s"
                    .formatted(response.statusCode(), payment.correlationId(), errors));
        }
    }

    private HttpRequest createRequest(URI defaultURL, Payment payment) {
        return HttpRequest.newBuilder(defaultURL)
                .header("Content-Type", "application/json")
                .timeout(ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(jsonb.toJson(payment)))
                .build();
    }

    private static HttpClient builHttpClient() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .executor(Runnable::run)
                .build();
    }
}