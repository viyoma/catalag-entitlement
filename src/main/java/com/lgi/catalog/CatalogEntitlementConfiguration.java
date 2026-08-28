package com.lgi.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.core.Configuration;
import io.dropwizard.db.DataSourceFactory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * CURRENT STATE (legacy). Dropwizard configuration bound from config.yml.
 * Under Spring Boot this becomes application.yml + @ConfigurationProperties.
 */
public class CatalogEntitlementConfiguration extends Configuration {

    @Valid
    @NotNull
    @JsonProperty("database")
    private DataSourceFactory database = new DataSourceFactory();

    @JsonProperty private String hazelcastClusterName = "catalog-cluster";
    @JsonProperty private String hazelcastCatalogMap = "catalog-titles";
    @JsonProperty private String geoIpDatabasePath;
    @JsonProperty private String artworkNfsPath;
    @JsonProperty private String kafkaBootstrapServers;
    @JsonProperty private String playbackEventsTopic;

    public DataSourceFactory getDatabase() { return database; }
    public String getHazelcastClusterName() { return hazelcastClusterName; }
    public String getHazelcastCatalogMap() { return hazelcastCatalogMap; }
    public String getGeoIpDatabasePath() { return geoIpDatabasePath; }
    public String getArtworkNfsPath() { return artworkNfsPath; }
    public String getKafkaBootstrapServers() { return kafkaBootstrapServers; }
    public String getPlaybackEventsTopic() { return playbackEventsTopic; }
}
