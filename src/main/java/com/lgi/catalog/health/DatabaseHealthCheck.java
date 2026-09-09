package com.lgi.catalog.health;

import com.codahale.metrics.health.HealthCheck;

import javax.sql.DataSource;
import java.sql.Connection;

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
            return Result.unhealthy("Connection is not valid");
        }
    }
}
