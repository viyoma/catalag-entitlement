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

    public EntitlementService(EntitlementDao dao,
                              HollowCatalogCache catalogCache,
                              GeoIpService geoIp,
                              ArtworkStore artworkStore,
                              PlaybackEventProducer producer) {
        this.dao = dao;
        this.catalogCache = catalogCache;
        this.geoIp = geoIp;
        this.artworkStore = artworkStore;
        this.producer = producer;

        this.dbBreaker = CircuitBreaker.of("entitlement-db", CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .slidingWindowSize(20)
                .build());
        this.dbRetry = Retry.of("entitlement-db", RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(200))
                .build());
    }

    public EntitlementDecision decide(String titleId, String subscriberId,
                                      String clientIp, String deviceType) {
        // 1. Catalog check (Hazelcast-backed Hollow cache)
        Title title = catalogCache.findTitle(titleId);
        if (title == null) {
            return EntitlementDecision.deny(titleId, subscriberId, "TITLE_NOT_IN_CATALOG");
        }

        // 2. Region check (MaxMind GeoIP)
        String country = geoIp.countryIso(clientIp);
        if (!title.allowedCountries().contains(country)) {
            return EntitlementDecision.deny(titleId, subscriberId, "REGION_BLOCKED:" + country);
        }

        // 3. Device check
        if (!isDeviceAllowed(title, deviceType)) {
            return EntitlementDecision.deny(titleId, subscriberId, "DEVICE_NOT_ALLOWED:" + deviceType);
        }

        // 4. Subscription entitlement (PostgreSQL via jOOQ, guarded by Resilience4j)
        Supplier<Boolean> entitled = Retry.decorateSupplier(dbRetry,
                CircuitBreaker.decorateSupplier(dbBreaker,
                        () -> dao.hasActiveEntitlement(subscriberId, title.productId())));

        if (!entitled.get()) {
            return EntitlementDecision.deny(titleId, subscriberId, "NO_ACTIVE_ENTITLEMENT");
        }

        // 5. Resolve artwork (NFS) and emit a playback-authorization event (Kafka)
        String artworkUri = artworkStore.artworkPath(titleId);
        EntitlementDecision decision =
                EntitlementDecision.allow(titleId, subscriberId, country, artworkUri);
        producer.publishAuthorization(decision);
        return decision;
    }

    private boolean isDeviceAllowed(Title title, String deviceType) {
        Set<String> allowed = title.allowedDevices();
        return allowed.isEmpty() || allowed.contains(deviceType);
    }
}
