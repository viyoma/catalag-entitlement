# APP-CATALOG Modernization Changelog

This changelog maps each AIDLC delivery unit to one reviewable commit. Every entry explains the technical change, customer impact, delivery mode, and verification evidence.

## U1 — Runtime and framework uplift

- **Why:** Java 17, Dropwizard 2 and `javax.*` were below the approved modernization baseline.
- **What changed:**
  - Upgraded Java 17 to Java 21.
  - Upgraded Dropwizard 2.1.10 to 4.0.7.
  - Migrated JAX-RS and validation imports from `javax.*` to `jakarta.*`.
  - Removed the unused `dropwizard-jdbi3` dependency and added `dropwizard-db`.
  - Added Java 21 Maven and Docker build packaging.
  - Checked in the AIDLC proposal, requirements, design, units, and audit evidence.
- **User impact:** No entitlement decision logic or API outcome changed. The service moves onto a supported runtime and framework baseline.
- **Delivery mode:** AWS Transform automation using AIDLC U1 context.
- **Verification:** Java 21 compilation and Maven package verification.
