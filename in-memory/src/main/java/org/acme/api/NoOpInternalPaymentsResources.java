package org.acme.api;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.acme.domain.PaymentsSummary;

@ApplicationScoped
@Path("/no-op")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class NoOpInternalPaymentsResources {

    @GET
    @Path("/internal/payments-summary")
    public PaymentsSummary getSummary() {
        return PaymentsSummary.ZERO;
    }

    @Path("/internal/purge-payments")
    @POST
    public void purgeInternalPayments() {
        // do nothing
    }

    @Path("/q/health/ready")
    @GET
    public String healthReadyCheck() {
        return "{}";
    }

}
