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
import java.util.Map;

@Path("/")
@RunOnVirtualThread
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PaymentsResource {


    private final PaymentService paymentService;
    private final PaymentsRepository paymentsRepository;
    private final PaymentProcessorQueue paymentProcessorQueue;

    public PaymentsResource(
            PaymentService paymentService,
            PaymentsRepository paymentsRepository,
            PaymentProcessorQueue paymentProcessorQueue) {
        this.paymentService = paymentService;
        this.paymentsRepository = paymentsRepository;
        this.paymentProcessorQueue = paymentProcessorQueue;
    }

    @POST
    @RunOnVirtualThread
    @Path("/payments")
    public Response processPayment(PaymentRequest paymentRequest) {
        paymentProcessorQueue.acceptPayment(paymentRequest);
        return Response.status(Response.Status.ACCEPTED).build();
    }

    @GET
    @RunOnVirtualThread
    @Path("/payments-summary")
    public Map<String, Map<String, Object>> getPaymentsSummary(
            @QueryParam("from") Instant from,
            @QueryParam("to") Instant to) {
        return paymentService.getSummary(from, to);
    }

    @POST
    @RunOnVirtualThread
    @Path("/purge-payments")
    public void purge() {
        paymentsRepository.deleteAll();
    }

}
