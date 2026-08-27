package com.lgi.catalog.health;

import com.codahale.metrics.health.HealthCheck;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Health check that verifies PostgreSQL reachability by validating a connection.
 */
public class DatabaseHealthCheck extends HealthCheck {

    private final DataSource dataSource;

    public DatabaseHealthCheck(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected Result check() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(2)) {
                return Result.healthy();
            }
            return Result.unhealthy("Database connection is not valid");
        }
    }
}
