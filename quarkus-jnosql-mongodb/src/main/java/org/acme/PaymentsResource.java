package org.acme;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
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

    private final PaymentService paymentService;
    private final PaymentsRepository paymentsRepository;
    private final PaymentProcessor paymentProcessor;

    public PaymentsResource(
            PaymentService paymentService,
            PaymentsRepository paymentsRepository,
            PaymentProcessor paymentProcessor) {
        this.paymentService = paymentService;
        this.paymentsRepository = paymentsRepository;
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
    public Object getPaymentsSummary(
            @QueryParam("from") Instant from,
            @QueryParam("to") Instant to,
            @QueryParam("useRepository") @DefaultValue("false") boolean useRepository) {
        return useRepository ? paymentsRepository.getSummary(from, to) : paymentService.getSummary(from, to);
    }

    @POST
    @RunOnVirtualThread
    @Path("/purge-payments")
    public void purge() {
        paymentsRepository.deleteAll();
    }

}
