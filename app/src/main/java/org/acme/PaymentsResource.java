package org.acme;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestMulti;

import java.time.Instant;

@Path("/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PaymentsResource {


    private final PaymentRepository paymentRepository;
    private final PaymentProcessor paymentProcessor;

    public PaymentsResource(
            PaymentRepository paymentRepository,
            PaymentProcessor paymentProcessor) {
        this.paymentRepository = paymentRepository;
        this.paymentProcessor = paymentProcessor;
    }

    @POST
    @Path("/payments")
    public RestMulti<?> processPayment(PaymentRequest paymentRequest) {
        return RestMulti
                .fromMultiData(Multi.createFrom().uni(paymentProcessor.acceptPayment(paymentRequest)))
                .status(Response.Status.ACCEPTED.getStatusCode()).build();
    }

    @GET
    @Path("/payments-summary")
    public Uni<?> getPaymentsSummary(
            @QueryParam("from") Instant from,
            @QueryParam("to") Instant to) {
        return paymentRepository.summary(from, to);
    }

    @POST
    @Path("/purge-payments")
    public Uni<?> purge() {
        return paymentRepository.deleteAll();
    }

}
