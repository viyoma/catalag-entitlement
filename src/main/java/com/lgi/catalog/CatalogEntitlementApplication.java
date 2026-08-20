package com.lgi.catalog;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.config.Config;
import com.lgi.catalog.api.EntitlementResource;
import com.lgi.catalog.cache.HollowCatalogCache;
import com.lgi.catalog.core.EntitlementService;
import com.lgi.catalog.db.EntitlementDao;
import com.lgi.catalog.geo.GeoIpService;
import com.lgi.catalog.messaging.PlaybackEventProducer;
import com.lgi.catalog.storage.ArtworkStore;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

/**
 * CURRENT STATE (legacy). Dropwizard entrypoint.
 *
 * All dependencies are hand-wired here in run() - there is no dependency-injection
 * container and no framework-managed lifecycle. This is one of the things AWS Transform
 * replaces with Spring Boot auto-configuration and managed beans.
 */
public class CatalogEntitlementApplication extends Application<CatalogEntitlementConfiguration> {

    public static void main(String[] args) throws Exception {
        new CatalogEntitlementApplication().run(args);
    }

    @Override
    public String getName() {
        return "catalog-entitlement-service";
    }

    @Override
    public void initialize(Bootstrap<CatalogEntitlementConfiguration> bootstrap) {
        // no-op
    }

    @Override
    public void run(CatalogEntitlementConfiguration config, Environment environment) throws Exception {
        // jOOQ over a raw JDBC datasource
        DSLContext dsl = DSL.using(
                config.getDatabase().build(environment.metrics(), "catalog-db"),
                SQLDialect.POSTGRES);
        EntitlementDao dao = new EntitlementDao(dsl);

        // Hazelcast in-cluster cache (simulated Hollow catalog)
        Config hzConfig = new Config();
        hzConfig.setClusterName(config.getHazelcastClusterName());
        HazelcastInstance hz = Hazelcast.newHazelcastInstance(hzConfig);
        HollowCatalogCache catalogCache = new HollowCatalogCache(hz, config.getHazelcastCatalogMap());

        // MaxMind GeoIP off the NFS-mounted database file
        GeoIpService geoIp = new GeoIpService(config.getGeoIpDatabasePath());

        // Artwork served directly from the NFS mount
        ArtworkStore artworkStore = new ArtworkStore(config.getArtworkNfsPath());

        // Raw Kafka producer, plain-JSON events
        PlaybackEventProducer producer = new PlaybackEventProducer(
                config.getKafkaBootstrapServers(), config.getPlaybackEventsTopic());

        EntitlementService service = new EntitlementService(dao, catalogCache, geoIp, artworkStore, producer);

        environment.jersey().register(new EntitlementResource(service));
        environment.lifecycle().manage(new io.dropwizard.lifecycle.Managed() {
            @Override public void start() { /* connections opened above */ }
            @Override public void stop() { hz.shutdown(); producer.close(); geoIp.close(); }
        });
    }
}
