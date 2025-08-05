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
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class RemotePaymentProcessor {

    private final PaymentsRepository paymentsRepository;
    private final URI defaultURL;
    private final URI fallbackURL;
    private final Jsonb jsonb;
    private final HttpClient httpClient;
    private final Map<String, AtomicLong> errorCount;
    private final Optional<Long> retriesBeforeFallback;

    @Inject
    public RemotePaymentProcessor(PaymentsRepository paymentsRepository,
                                  @ConfigProperty(name = "default.payment.url")
                                  URI defaultURL,
                                  @ConfigProperty(name = "fallback.payment.url")
                                  URI fallbackURL,
                                  @ConfigProperty(name = "retries.before.fallback")
                                  Optional<Long> retriesBeforeFallback,
                                  Jsonb jsonb) {
        this.paymentsRepository = paymentsRepository;
        this.defaultURL = defaultURL;
        this.fallbackURL = fallbackURL;
        this.jsonb = jsonb;
        this.retriesBeforeFallback = retriesBeforeFallback;
        this.errorCount = new ConcurrentHashMap<>();
        this.httpClient = builHttpClient();
    }


    public void process(PaymentRequest paymentRequest) {
        Payment payment = Payment.create(paymentRequest);
        try {
            var request = createRequest(defaultURL, payment);
            var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 200) {
                paymentsRepository.saveAsDefaultPayment(payment);
                errorCount.remove(payment.correlationId());
            } else {
                long errors = errorCount
                        .computeIfAbsent(payment.correlationId(), k -> new AtomicLong(0))
                        .incrementAndGet();
                if (retriesBeforeFallback.orElse(16l) <= errors) {
                    processFallback(payment);
                    errorCount.remove(payment.correlationId());
                } else {
                    throw new RuntimeException("Payment processing failed with status code: " + response.statusCode());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void processFallback(Payment payment) throws IOException, InterruptedException {
        System.out.printf("Processing fallback payment for correlationId %s%n", payment.correlationId());
        // Fallback to the secondary URL
        var request = createRequest(fallbackURL, payment);
        var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() == 200) {
            paymentsRepository.saveAsFallbackPayment(payment);
        } else {
            throw new RuntimeException("Payment processing failed with status code: " + response.statusCode());
        }
    }

    private HttpRequest createRequest(URI defaultURL, Payment payment) {
        return HttpRequest.newBuilder(defaultURL)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
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
