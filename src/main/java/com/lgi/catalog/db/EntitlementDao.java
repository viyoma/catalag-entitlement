package com.lgi.catalog.db;

import org.jooq.DSLContext;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.value;

/**
 * CURRENT STATE (legacy). Data access via jOOQ DSL over self-managed PostgreSQL.
 *
 * AWS Transform target: Spring Data JPA repository (or Spring Data JDBC) against
 * Amazon Aurora PostgreSQL. The fluent jOOQ query below is rewritten to a
 * derived query method or @Query on a repository interface.
 */
public class EntitlementDao {

    private final DSLContext dsl;

    public EntitlementDao(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * True if the subscriber holds an ACTIVE entitlement for the product,
     * effective now.
     */
    public boolean hasActiveEntitlement(String subscriberId, String productId) {
        return dsl.fetchExists(
                dsl.selectOne()
                   .from(table("entitlements"))
                   .where(field("subscriber_id").eq(value(subscriberId)))
                   .and(field("product_id").eq(value(productId)))
                   .and(field("status").eq(value("ACTIVE")))
                   .and(field("valid_from").le(field("now()")))
                   .and(field("valid_to").ge(field("now()"))));
    }
}
