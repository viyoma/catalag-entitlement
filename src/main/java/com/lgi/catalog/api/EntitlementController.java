package com.lgi.catalog.api;

import com.lgi.catalog.core.EntitlementDecision;
import com.lgi.catalog.core.EntitlementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MODERNIZED (target state). Spring Boot 3 @RestController.
 *
 * Legacy -> target changes (automatable by AWS Transform):
 *   javax.ws.rs.@Path / @GET / @Produces  ->  Spring @RestController / @GetMapping
 *   @PathParam / @QueryParam               ->  @PathVariable / @RequestParam
 *   javax.ws.rs.core.Response              ->  org.springframework.http.ResponseEntity
 *   constructor wiring by hand             ->  Spring dependency injection
 */
@RestController
@RequestMapping("/entitlement")
public class EntitlementController {

    private final EntitlementService service;

    public EntitlementController(EntitlementService service) {
        this.service = service;
    }

    /** Is this subscriber allowed to watch this title, in this region, on this device? */
    @GetMapping("/{titleId}")
    public ResponseEntity<EntitlementDecision> check(
            @PathVariable String titleId,
            @RequestParam String subscriberId,
            @RequestParam String clientIp,
            @RequestParam String deviceType) {

        EntitlementDecision decision = service.decide(titleId, subscriberId, clientIp, deviceType);

        return ResponseEntity
                .status(decision.allowed() ? HttpStatus.OK : HttpStatus.FORBIDDEN)
                .body(decision);
    }
}
