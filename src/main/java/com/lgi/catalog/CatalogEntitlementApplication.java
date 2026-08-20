package com.lgi.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MODERNIZED (target state). Spring Boot 3 entrypoint.
 *
 * Legacy: io.dropwizard.Application<CatalogEntitlementConfiguration> with a run()
 * method registering Jersey resources and health checks. Target: @SpringBootApplication
 * with component-scanned @RestController / @Service beans and Actuator health.
 */
@SpringBootApplication
public class CatalogEntitlementApplication {
    public static void main(String[] args) {
        SpringApplication.run(CatalogEntitlementApplication.class, args);
    }
}
