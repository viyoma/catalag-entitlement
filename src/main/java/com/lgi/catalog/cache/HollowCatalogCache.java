package com.lgi.catalog.cache;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.lgi.catalog.core.Title;

/**
 * CURRENT STATE (legacy). Hazelcast IMap standing in for the Netflix Hollow
 * in-memory product catalog.
 *
 * AWS Transform target: Amazon ElastiCache for Redis (Spring Data Redis).
 * The IMap.get(...) call becomes a RedisTemplate / repository lookup.
 */
public class HollowCatalogCache {

    private final IMap<String, Title> titles;

    public HollowCatalogCache(HazelcastInstance hz, String mapName) {
        this.titles = hz.getMap(mapName);
    }

    public Title findTitle(String titleId) {
        return titles.get(titleId);
    }

    public void put(Title title) {
        titles.put(title.titleId(), title);
    }
}
