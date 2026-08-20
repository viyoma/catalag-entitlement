package com.lgi.catalog.geo;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CountryResponse;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

/**
 * CURRENT STATE (legacy). MaxMind GeoIP lookup from a .mmdb file on the NFS mount.
 *
 * AWS Transform RETAINS MaxMind (geo logic is unchanged) but the database file
 * moves off NFS to Amazon S3, loaded at startup instead of read from a mount.
 */
public class GeoIpService {

    private final DatabaseReader reader;

    public GeoIpService(String mmdbPath) {
        try {
            this.reader = new DatabaseReader.Builder(new File(mmdbPath)).build();
        } catch (IOException e) {
            throw new IllegalStateException("Could not open GeoIP DB at " + mmdbPath, e);
        }
    }

    public String countryIso(String clientIp) {
        try {
            CountryResponse resp = reader.country(InetAddress.getByName(clientIp));
            return resp.getCountry().getIsoCode();
        } catch (Exception e) {
            return "ZZ"; // unknown
        }
    }

    public void close() {
        try {
            reader.close();
        } catch (IOException ignored) {
        }
    }
}
