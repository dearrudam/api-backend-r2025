package fish.payara.payments.resources;

import fish.payara.payments.domain.PaymentRequest;
import fish.payara.payments.domain.PaymentsService;
import jakarta.inject.Inject;
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
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PaymentsResource {

    @Inject
    private PaymentsService paymentsService;

    @POST
    @Path("/payments")
    public Response pay(PaymentRequest paymentRequest) {
        paymentsService.accept(paymentRequest);
        return Response.status(Response.Status.ACCEPTED).build();
    }

    @POST
    @Path("/purge-payments")
    public Response purge(){
        paymentsService.purge();
        return Response.status(Response.Status.NO_CONTENT).build();
    }


    public record QueryParams (
        @QueryParam("from") Instant from,
        @QueryParam("to") Instant to
    ) {}
    @GET
    @Path("/payments-summary")
    public Response summary(QueryParams queryParams) {
        return Response.ok(paymentsService.summary(queryParams.from(),queryParams.to())).build();
    }

}
