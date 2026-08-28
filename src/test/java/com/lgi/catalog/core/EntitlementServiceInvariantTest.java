package com.lgi.catalog.core;

import com.lgi.catalog.cache.HollowCatalogCache;
import com.lgi.catalog.db.EntitlementDao;
import com.lgi.catalog.geo.GeoIpService;
import com.lgi.catalog.messaging.PlaybackEventProducer;
import com.lgi.catalog.storage.ArtworkStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntitlementServiceInvariantTest {

    private static final String TITLE_ID = "title-123";
    private static final String SUBSCRIBER_ID = "subscriber-456";
    private static final String CLIENT_IP = "203.0.113.10";
    private static final String DEVICE = "TV";

    @Mock private EntitlementDao dao;
    @Mock private HollowCatalogCache catalogCache;
    @Mock private GeoIpService geoIp;
    @Mock private ArtworkStore artworkStore;
    @Mock private PlaybackEventProducer producer;

    private EntitlementService service;

    @BeforeEach
    void setUp() {
        service = new EntitlementService(
                dao, catalogCache, geoIp, artworkStore, producer, new SimpleMeterRegistry());
    }

    @Test
    void deniesWhenTitleIsMissingFromCatalog() {
        when(catalogCache.findTitle(TITLE_ID)).thenReturn(null);

        EntitlementDecision decision = decide(DEVICE);

        assertDenied(decision, "TITLE_NOT_IN_CATALOG");
        verifyNoInteractions(geoIp, dao, artworkStore, producer);
    }

    @Test
    void deniesWhenViewerRegionIsBlocked() {
        when(catalogCache.findTitle(TITLE_ID)).thenReturn(title(Set.of("GB"), Set.of(DEVICE)));
        when(geoIp.countryIso(CLIENT_IP)).thenReturn("US");

        EntitlementDecision decision = decide(DEVICE);

        assertDenied(decision, "REGION_BLOCKED:US");
        verifyNoInteractions(dao, artworkStore, producer);
    }

    @Test
    void deniesWhenDeviceIsNotAllowed() {
        when(catalogCache.findTitle(TITLE_ID)).thenReturn(title(Set.of("US"), Set.of("MOBILE")));
        when(geoIp.countryIso(CLIENT_IP)).thenReturn("US");

        EntitlementDecision decision = decide(DEVICE);

        assertDenied(decision, "DEVICE_NOT_ALLOWED:TV");
        verifyNoInteractions(dao, artworkStore, producer);
    }

    @Test
    void deniesWhenSubscriberHasNoActiveEntitlement() {
        when(catalogCache.findTitle(TITLE_ID)).thenReturn(title(Set.of("US"), Set.of(DEVICE)));
        when(geoIp.countryIso(CLIENT_IP)).thenReturn("US");
        when(dao.hasActiveEntitlement(SUBSCRIBER_ID, "product-789")).thenReturn(false);

        EntitlementDecision decision = decide(DEVICE);

        assertDenied(decision, "NO_ACTIVE_ENTITLEMENT");
        verify(producer, never()).publishAuthorization(decision);
        verifyNoInteractions(artworkStore);
    }

    @Test
    void allowsAndPublishesWhenEveryInvariantPasses() {
        when(catalogCache.findTitle(TITLE_ID)).thenReturn(title(Set.of("US"), Set.of(DEVICE)));
        when(geoIp.countryIso(CLIENT_IP)).thenReturn("US");
        when(dao.hasActiveEntitlement(SUBSCRIBER_ID, "product-789")).thenReturn(true);
        when(artworkStore.artworkPath(TITLE_ID)).thenReturn("file:///artwork/title-123/poster.jpg");

        EntitlementDecision decision = decide(DEVICE);

        assertTrue(decision.allowed());
        assertEquals("ALLOWED", decision.reason());
        assertEquals("US", decision.country());
        assertEquals("file:///artwork/title-123/poster.jpg", decision.artworkUri());
        verify(producer).publishAuthorization(decision);
    }

    private EntitlementDecision decide(String device) {
        return service.decide(TITLE_ID, SUBSCRIBER_ID, CLIENT_IP, device);
    }

    private Title title(Set<String> countries, Set<String> devices) {
        return new Title(TITLE_ID, "product-789", countries, devices);
    }

    private void assertDenied(EntitlementDecision decision, String reason) {
        assertFalse(decision.allowed());
        assertEquals(reason, decision.reason());
        assertNull(decision.country());
        assertNull(decision.artworkUri());
    }
}
