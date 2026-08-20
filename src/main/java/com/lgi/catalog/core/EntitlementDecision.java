package com.lgi.catalog.core;

import java.time.Instant;

/**
 * CURRENT STATE (legacy). Result of an entitlement check; also the payload
 * published to Kafka as a plain-JSON playback-authorization event.
 */
public record EntitlementDecision(
        String titleId,
        String subscriberId,
        boolean allowed,
        String reason,
        String country,
        String artworkUri,
        Instant decidedAt) {

    public static EntitlementDecision allow(String titleId, String subscriberId,
                                            String country, String artworkUri) {
        return new EntitlementDecision(titleId, subscriberId, true, "ALLOWED",
                country, artworkUri, Instant.now());
    }

    public static EntitlementDecision deny(String titleId, String subscriberId, String reason) {
        return new EntitlementDecision(titleId, subscriberId, false, reason,
                null, null, Instant.now());
    }
}
