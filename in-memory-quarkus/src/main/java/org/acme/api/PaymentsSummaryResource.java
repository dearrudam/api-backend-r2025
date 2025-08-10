package org.acme.api;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.domain.PaymentService;
import org.acme.domain.PaymentsSummary;

import java.time.Instant;
import java.util.function.BiFunction;

@Path("/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class PaymentsSummaryResource {

    private final PaymentService paymentService;

    @Inject
    public PaymentsSummaryResource(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @POST
    @Path("/purge-payments")
    public void purge() {
        this.paymentService.purgePayments();
    }

    @POST
    @Path("/internal/purge-payments")
    public void internalPurge() {
        this.paymentService.purgeInternalPayments();
    }

    @GET
    @Path("/payments-summary")
    public Response get(@QueryParam("from") Instant from,
                        @QueryParam("to") Instant to) {
        return getSummary(from, to, paymentService::getSummary);
    }

    @Path("/internal/payments-summary")
    @GET
    public Response getInternalSummary(@QueryParam("from") Instant from,
                                       @QueryParam("to") Instant to) {
        return getSummary(from, to, paymentService::getInternalSummary);
    }

    private Response getSummary(Instant from, Instant to, BiFunction<Instant, Instant, PaymentsSummary> summaryFunction) {
        return Response.ok(summaryFunction.apply(from, to)).build();
    }

}
