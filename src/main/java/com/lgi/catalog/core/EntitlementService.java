package com.lgi.catalog.core;

import com.lgi.catalog.cache.HollowCatalogCache;
import com.lgi.catalog.db.EntitlementDao;
import com.lgi.catalog.geo.GeoIpService;
import com.lgi.catalog.messaging.PlaybackEventProducer;
import com.lgi.catalog.storage.ArtworkStore;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

/**
 * CURRENT STATE (legacy). Core entitlement decision logic.
 *
 * Resilience4j circuit-breaker + retry wrap the DB call. AWS Transform RETAINS
 * Resilience4j (it is already the target library) but rewires how it is configured
 * (programmatic -> Spring Boot starter + application.yml).
 */
public class EntitlementService {

    private final EntitlementDao dao;
    private final HollowCatalogCache catalogCache;
    private final GeoIpService geoIp;
    private final ArtworkStore artworkStore;
    private final PlaybackEventProducer producer;

    private final CircuitBreaker dbBreaker;
    private final Retry dbRetry;

    private final MeterRegistry meterRegistry;
    private final Counter allowedCounter;
    private final Timer decisionTimer;

    public EntitlementService(EntitlementDao dao,
                              HollowCatalogCache catalogCache,
                              GeoIpService geoIp,
                              ArtworkStore artworkStore,
                              PlaybackEventProducer producer,
                              MeterRegistry meterRegistry) {
        this.dao = dao;
        this.catalogCache = catalogCache;
        this.geoIp = geoIp;
        this.artworkStore = artworkStore;
        this.producer = producer;
        this.meterRegistry = meterRegistry;

        this.dbBreaker = CircuitBreaker.of("entitlement-db", CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .slidingWindowSize(20)
                .build());
        this.dbRetry = Retry.of("entitlement-db", RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(200))
                .build());

        this.allowedCounter = Counter.builder("entitlement_decisions_total")
                .tag("outcome", "allowed")
                .register(meterRegistry);
        this.decisionTimer = Timer.builder("entitlement_decision_duration_seconds")
                .register(meterRegistry);

        // Gauge for circuit breaker state: 0=closed, 1=open, 2=half_open
        meterRegistry.gauge("entitlement_db_circuit_state", dbBreaker, cb -> {
            switch (cb.getState()) {
                case OPEN: return 1.0;
                case HALF_OPEN: return 2.0;
                default: return 0.0;
            }
        });
    }

    public EntitlementDecision decide(String titleId, String subscriberId,
                                      String clientIp, String deviceType) {
        return decisionTimer.record(() -> doDecide(titleId, subscriberId, clientIp, deviceType));
    }

    private EntitlementDecision doDecide(String titleId, String subscriberId,
                                         String clientIp, String deviceType) {
        // 1. Catalog check (Hazelcast-backed Hollow cache)
        Title title = catalogCache.findTitle(titleId);
        if (title == null) {
            incrementDenied("TITLE_NOT_IN_CATALOG");
            return EntitlementDecision.deny(titleId, subscriberId, "TITLE_NOT_IN_CATALOG");
        }

        // 2. Region check (MaxMind GeoIP)
        String country = geoIp.countryIso(clientIp);
        if (!title.allowedCountries().contains(country)) {
            incrementDenied("REGION_BLOCKED");
            return EntitlementDecision.deny(titleId, subscriberId, "REGION_BLOCKED:" + country);
        }

        // 3. Device check
        if (!isDeviceAllowed(title, deviceType)) {
            incrementDenied("DEVICE_NOT_ALLOWED");
            return EntitlementDecision.deny(titleId, subscriberId, "DEVICE_NOT_ALLOWED:" + deviceType);
        }

        // 4. Subscription entitlement (PostgreSQL via jOOQ, guarded by Resilience4j)
        Supplier<Boolean> entitled = Retry.decorateSupplier(dbRetry,
                CircuitBreaker.decorateSupplier(dbBreaker,
                        () -> dao.hasActiveEntitlement(subscriberId, title.productId())));

        if (!entitled.get()) {
            incrementDenied("NO_ACTIVE_ENTITLEMENT");
            return EntitlementDecision.deny(titleId, subscriberId, "NO_ACTIVE_ENTITLEMENT");
        }

        // 5. Resolve artwork (NFS) and emit a playback-authorization event (Kafka)
        String artworkUri = artworkStore.artworkPath(titleId);
        EntitlementDecision decision =
                EntitlementDecision.allow(titleId, subscriberId, country, artworkUri);
        producer.publishAuthorization(decision);
        allowedCounter.increment();
        return decision;
    }

    private void incrementDenied(String reason) {
        Counter.builder("entitlement_decisions_total")
                .tag("outcome", "denied")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    private boolean isDeviceAllowed(Title title, String deviceType) {
        Set<String> allowed = title.allowedDevices();
        return allowed.isEmpty() || allowed.contains(deviceType);
    }
}
