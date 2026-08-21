package com.lgi.catalog.api;

import com.lgi.catalog.core.EntitlementDecision;
import com.lgi.catalog.core.EntitlementService;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * CURRENT STATE (legacy). JAX-RS (javax.ws.rs) resource.
 *
 * AWS Transform rewrites this to a Spring Boot @RestController with
 * @GetMapping and ResponseEntity - note the javax.* -> jakarta.* / Spring
 * annotation migration is exactly the kind of change the tool automates.
 */
@Path("/entitlement")
@Produces(MediaType.APPLICATION_JSON)
public class EntitlementResource {

    private final EntitlementService service;

    public EntitlementResource(EntitlementService service) {
        this.service = service;
    }

    /**
     * Answers: "is this subscriber allowed to watch this title, in this region, on this device?"
     */
    @GET
    @Path("/{titleId}")
    public Response check(@PathParam("titleId") String titleId,
                          @QueryParam("subscriberId") String subscriberId,
                          @QueryParam("clientIp") String clientIp,
                          @QueryParam("deviceType") String deviceType) {

        EntitlementDecision decision =
                service.decide(titleId, subscriberId, clientIp, deviceType);

        Response.Status status = decision.allowed()
                ? Response.Status.OK
                : Response.Status.FORBIDDEN;

        return Response.status(status).entity(decision).build();
    }
}
