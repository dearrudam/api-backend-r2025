package org.acme.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

@ApplicationScoped
public class PaymentProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentProcessor.class);
    private final DefaultPaymentProcessor defaultPaymentProcessor;
    private final FallbackPaymentProcessor fallbackPaymentProcessor;

    @Inject
    public PaymentProcessor(
            @RestClient
            DefaultPaymentProcessor defaultPaymentProcessor,
            @RestClient
            FallbackPaymentProcessor fallbackPaymentProcessor) {
        this.defaultPaymentProcessor = defaultPaymentProcessor;
        this.fallbackPaymentProcessor = fallbackPaymentProcessor;
    }


    @Retry(maxRetries = 16)
    @Fallback(fallbackMethod = "fallbackSendPayment")
    public void sendPayment(NewPaymentRequest newPaymentRequest,
                            Consumer<Payment> prepareConsumer,
                            Consumer<Payment> commitConsumer,
                            BiConsumer<Payment, Throwable> rollbackConsumer,
                            Consumer<NewPaymentRequest> retryConsumer) {
        RemotePaymentRequest request = newPaymentRequest.toNewPayment();
        Payment payment = RemotePaymentName.DEFAULT.toPayment(request);
        try {
            prepareConsumer.accept(payment);
            var response = defaultPaymentProcessor.processPayment(request);
            switch (Response.Status.fromStatusCode(response.getStatus()).getFamily()) {
                case SUCCESSFUL -> commitConsumer.accept(payment);
                case CLIENT_ERROR -> rollbackConsumer.accept(payment, new WebApplicationException(
                                "Client error while processing payment: " + response.getStatus(),
                                response.getStatus()
                        )
                );
                case SERVER_ERROR -> throw new WebApplicationException(
                        "Server error while processing payment: " + response.getStatus(),
                        response.getStatus()
                );
                default -> rollbackConsumer.accept(payment,
                        new WebApplicationException(
                                "Unexpected response status while processing payment: " + response.getStatus(),
                                response.getStatus()
                        ));
            }
        } catch (Exception e) {
            rollbackConsumer.accept(payment, e);
            LOGGER.warn("ProcessingException occurred while sending payment: {}", e.getMessage(), e);
            throw e; // trigger the fallback
        }
    }

    public void fallbackSendPayment(NewPaymentRequest newPaymentRequest,
                                    Consumer<Payment> prepareConsumer,
                                    Consumer<Payment> commitConsumer,
                                    BiConsumer<Payment, Throwable> rollbackConsumer,
                                    Consumer<NewPaymentRequest> retryConsumer) {
        final RemotePaymentRequest request = newPaymentRequest.toNewPayment();
        Payment payment = RemotePaymentName.FALLBACK.toPayment(request);
        try {
            prepareConsumer.accept(payment);
            var response = fallbackPaymentProcessor.processPayment(request);
            switch (Response.Status.fromStatusCode(response.getStatus()).getFamily()) {
                case SUCCESSFUL -> commitConsumer.accept(payment);
                case CLIENT_ERROR -> rollbackConsumer.accept(payment, new WebApplicationException(
                        "Client error while processing payment: " + response.getStatus(),
                        response.getStatus()
                ));
                case SERVER_ERROR -> rollbackConsumer.accept(payment, new WebApplicationException(
                        "Server error while processing payment: " + response.getStatus(),
                        response.getStatus()
                ));
                default -> new WebApplicationException(
                        "Unexpected response status while processing payment: " + response.getStatus(),
                        response.getStatus()
                );
            }
        } catch (RuntimeException e) {
            rollbackConsumer.accept(payment, e);
            LOGGER.warn("ProcessingException occurred while sending payment: {}", e.getMessage(), e);
            throw e;
        }
    }

}