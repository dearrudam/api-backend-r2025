package org.acme;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;

@Path("/")
@RunOnVirtualThread
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
    @RunOnVirtualThread
    @Path("/payments")
    public Response processPayment(PaymentRequest paymentRequest) {
        paymentProcessor.acceptPayment(paymentRequest);
        return Response.status(Response.Status.ACCEPTED).build();
    }

    @GET
    @RunOnVirtualThread
    @Path("/payments-summary")
    public Response getPaymentsSummary(
            @QueryParam("from") Instant from,
            @QueryParam("to") Instant to) {
        return Response.ok(paymentRepository.summary(from, to)).build();
    }

    @POST
    @RunOnVirtualThread
    @Path("/purge-payments")
    public void purge() {
        paymentRepository.deleteAll();
    }

}
