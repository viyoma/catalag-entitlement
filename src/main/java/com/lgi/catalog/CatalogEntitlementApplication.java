package com.lgi.catalog;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.config.Config;
import com.lgi.catalog.api.EntitlementResource;
import com.lgi.catalog.cache.HollowCatalogCache;
import com.lgi.catalog.core.EntitlementService;
import com.lgi.catalog.db.EntitlementDao;
import com.lgi.catalog.geo.GeoIpService;
import com.lgi.catalog.health.DatabaseHealthCheck;
import com.lgi.catalog.messaging.PlaybackEventProducer;
import com.lgi.catalog.storage.ArtworkStore;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import javax.sql.DataSource;
import java.io.IOException;

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
        // Micrometer Prometheus registry
        PrometheusMeterRegistry promRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        // jOOQ over a raw JDBC datasource
        DataSource dataSource = config.getDatabase().build(environment.metrics(), "catalog-db");
        DSLContext dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
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

        EntitlementService service = new EntitlementService(dao, catalogCache, geoIp, artworkStore, producer, promRegistry);

        environment.jersey().register(new EntitlementResource(service));

        // Register database health check
        environment.healthChecks().register("database", new DatabaseHealthCheck(dataSource));

        // Expose Prometheus metrics on admin connector at /prometheus
        environment.admin().addServlet("prometheus", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.setContentType("text/plain; version=0.0.4; charset=utf-8");
                resp.getWriter().write(promRegistry.scrape());
            }
        }).addMapping("/prometheus");

        environment.lifecycle().manage(new io.dropwizard.lifecycle.Managed() {
            @Override public void start() { /* connections opened above */ }
            @Override public void stop() { hz.shutdown(); producer.close(); geoIp.close(); }
        });
    }
}
