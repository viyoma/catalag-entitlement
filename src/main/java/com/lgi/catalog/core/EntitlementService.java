package com.lgi.catalog.core;

import com.lgi.catalog.cache.CatalogCache;
import com.lgi.catalog.db.EntitlementRepository;
import com.lgi.catalog.geo.GeoIpService;
import com.lgi.catalog.messaging.PlaybackEventProducer;
import com.lgi.catalog.storage.ArtworkStore;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * MODERNIZED (target state). Core entitlement decision logic.
 *
 * Business logic is UNCHANGED (5-step decision) — the modernization is structural:
 *   - Programmatic Resilience4j CircuitBreaker/Retry construction  ->  declarative
 *     @CircuitBreaker / @Retry annotations, configured in application.yml.
 *   - Hand-wired constructor  ->  Spring @Service constructor injection.
 *   - Hazelcast-backed cache  ->  ElastiCache (Redis) behind CatalogCache.
 *   - jOOQ DAO  ->  Spring Data EntitlementRepository.
 */
@Service
public class EntitlementService {

    private final EntitlementRepository repository;
    private final CatalogCache catalogCache;
    private final GeoIpService geoIp;
    private final ArtworkStore artworkStore;
    private final PlaybackEventProducer producer;

    public EntitlementService(EntitlementRepository repository,
                              CatalogCache catalogCache,
                              GeoIpService geoIp,
                              ArtworkStore artworkStore,
                              PlaybackEventProducer producer) {
        this.repository = repository;
        this.catalogCache = catalogCache;
        this.geoIp = geoIp;
        this.artworkStore = artworkStore;
        this.producer = producer;
    }

    public EntitlementDecision decide(String titleId, String subscriberId,
                                      String clientIp, String deviceType) {
        // 1. Catalog check (ElastiCache/Redis-backed)
        Title title = catalogCache.findTitle(titleId);
        if (title == null) {
            return EntitlementDecision.deny(titleId, subscriberId, "TITLE_NOT_IN_CATALOG");
        }

        // 2. Region check (geo lookup)
        String country = geoIp.countryIso(clientIp);
        if (!title.allowedCountries().contains(country)) {
            return EntitlementDecision.deny(titleId, subscriberId, "REGION_BLOCKED:" + country);
        }

        // 3. Device check
        if (!isDeviceAllowed(title, deviceType)) {
            return EntitlementDecision.deny(titleId, subscriberId, "DEVICE_NOT_ALLOWED:" + deviceType);
        }

        // 4. Subscription entitlement (Aurora PostgreSQL via Spring Data,
        //    guarded by declarative Resilience4j — see hasActiveEntitlement)
        if (!hasActiveEntitlement(subscriberId, title.productId())) {
            return EntitlementDecision.deny(titleId, subscriberId, "NO_ACTIVE_ENTITLEMENT");
        }

        // 5. Resolve artwork (S3) and emit playback-authorization event (MSK/Kafka)
        String artworkUri = artworkStore.artworkPath(titleId);
        EntitlementDecision decision =
                EntitlementDecision.allow(titleId, subscriberId, country, artworkUri);
        producer.publishAuthorization(decision);
        return decision;
    }

    @CircuitBreaker(name = "entitlement-db", fallbackMethod = "entitlementUnavailable")
    @Retry(name = "entitlement-db")
    protected boolean hasActiveEntitlement(String subscriberId, String productId) {
        return repository.hasActiveEntitlement(subscriberId, productId);
    }

    /** Fail-safe: default-deny when the entitlement store is unavailable (BR-ENT-01). */
    @SuppressWarnings("unused")
    protected boolean entitlementUnavailable(String subscriberId, String productId, Throwable t) {
        return false;
    }

    private boolean isDeviceAllowed(Title title, String deviceType) {
        Set<String> allowed = title.allowedDevices();
        return allowed.isEmpty() || allowed.contains(deviceType);
    }
}
