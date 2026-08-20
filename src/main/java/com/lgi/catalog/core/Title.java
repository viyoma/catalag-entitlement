package com.lgi.catalog.core;

import java.util.Set;

/**
 * CURRENT STATE (legacy). Catalog title as held in the Hollow/Hazelcast cache.
 */
public record Title(
        String titleId,
        String productId,
        Set<String> allowedCountries,
        Set<String> allowedDevices) {
}
