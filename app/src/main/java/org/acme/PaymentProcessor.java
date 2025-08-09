package org.acme;

import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.list.ReactiveListCommands;
import io.quarkus.runtime.Startup;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;

@ApplicationScoped
public class PaymentProcessor {

    private final ReactiveListCommands<String, PaymentRequest> list;
    private final RemotePaymentProcessor remotePaymentProcessor;
    private final int workers;

    public PaymentProcessor(
            ReactiveRedisDataSource redis,
            RemotePaymentProcessor remotePaymentProcessor,
            @ConfigProperty(name = "workers")
            Optional<Integer> workers) {
        ;
        this.list = redis.list(PaymentRequest.class);
        this.remotePaymentProcessor = remotePaymentProcessor;
        this.workers = workers.orElse(10);
    }

    @Startup
    public void init() {
        for (int i = 0; i < workers; i++)
            this.listen();
    }

    public void listen() {
        list.lpop("payment:request")
                .onItem().transformToUni(res -> {
                    if (res == null) {
                        return Uni.createFrom().voidItem();
                    }
                    return process(res);
                })
                .subscribe()
                .with(data -> listen(),
                        failure -> {
                            System.out.printf("%s unexpected issue happened: %s%n", "💥", failure.getMessage());
                            listen();
                        });
    }

    private Uni<?> process(PaymentRequest paymentRequest) {
        if (paymentRequest == null)
            return Uni.createFrom().voidItem();
        return remotePaymentProcessor.process(paymentRequest);
    }

    public Uni<?> acceptPayment(PaymentRequest paymentRequest) {
        if (paymentRequest == null)
            return Uni.createFrom().item(false);
        return list.lpush("payment:request", paymentRequest)
                .onItem().transform(response -> {
                    if (response == null || response.toString().isEmpty()) {
                        System.out.println("Failed to accept payment request, retrying...");
                        return false; // Retry if failed to accept
                    }
                    System.out.println("Payment request accepted successfully.");
                    return true; // Successfully accepted the payment request
                })
                .onFailure().recoverWithItem(false);
    }
}
