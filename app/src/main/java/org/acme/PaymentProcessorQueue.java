package org.acme;

import io.quarkus.runtime.Startup;
import io.quarkus.virtual.threads.VirtualThreads;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

@ApplicationScoped
public class PaymentProcessorQueue {

    private final LinkedBlockingQueue<PaymentRequest> queue = new LinkedBlockingQueue<>();
    private final ExecutorService executorService;
    private final Integer queueSize;
    private final Integer workers;
    private final RemotePaymentProcessor remotePaymentProcessor;
    private final Semaphore semaphore;

    public PaymentProcessorQueue(
            @VirtualThreads
            ExecutorService executorService,
            @ConfigProperty(name = "queue.size")
            Optional<Integer> queueSize,
            @ConfigProperty(name = "workers")
            Optional<Integer> workers,
            RemotePaymentProcessor remotePaymentProcessor) {
        this.executorService = executorService;
        this.queueSize = queueSize.orElse(1000);
        this.workers = workers.orElse(10);
        this.remotePaymentProcessor = remotePaymentProcessor;
        this.semaphore = new Semaphore(this.workers);
    }

    @Startup
    public void init() {
        // Start a thread to process payments from the queue
        for (int i = 0; i < semaphore.availablePermits(); i++) {
            executorService.submit(this::processPayment);
        }
    }

    public boolean acceptPayment(PaymentRequest paymentRequest) {
        return queue.offer(paymentRequest);
    }

    private void processPayment() {
        while (true) {
            try {
                PaymentRequest paymentRequest = queue.take(); // Blocking call
                try {
                    semaphore.acquire();
                    remotePaymentProcessor.process(paymentRequest);
                } catch (RuntimeException e) {
                    // ignore the exception, it will be retried later
                    queue.offer(paymentRequest);
                } finally {
                    semaphore.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupted status
                break; // Exit the loop if interrupted
            }
        }
    }
}
