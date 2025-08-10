package org.acme.domain;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "internal-payments-management")
public interface InternalPaymentsManagement {

    @Path("/internal/payments-summary")
    @GET
    @Produces("application/json")
    @Consumes("application/json")
    PaymentsSummary getSummary(
            @QueryParam("from") @DefaultValue("") String fromStr,
            @QueryParam("to") @DefaultValue("") String toStr);

    @Path("/internal/purge-payments")
    @POST
    @Produces("application/json")
    @Consumes("application/json")
    void purgeInternalPayments();

    @Path("/q/health/ready")
    @GET
    @Produces("application/json")
    @Consumes("application/json")
    String healthReadyCheck();

}
