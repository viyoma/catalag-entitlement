# Proposal Spec — Catalog & Entitlement, modernised under AIDLC, operated by a DevOps agent

Status: **proposed** · Space: `catalog-entitlement` · Hero workload: `APP-CATALOG`
Author: Viyoma Sachdeva · Raised: 2026-08-26

---

## 0. Why this document exists

The factory can already modernise this application. It did: `APP-CATALOG` has a real
agent-generated pull request against a real repository. But the run produced **code without
recorded intent**. A reviewer at the gate is handed a diff and asked to approve it. The
specification the agent worked to — what the modernised service must do, what must not
change, what "done" means — lived only inside the model's context and evaporated when the
run ended.

That is the gap this proposal closes, and it is the argument for AIDLC in one sentence:

> **The factory's output should be a reviewable specification that a human approves, and
> code that provably implements it — not a diff a human is asked to trust.**

Three workstreams follow. They are sequential in value but only partly sequential in time:
W1 must land before W2 can be honest, W3 depends on W2 existing.

| | Workstream | Produces | State today |
|---|---|---|---|
| **W1** | Modernisation governed by AIDLC | Checked-in spec lineage for the modernised service | Nothing — this is new |
| **W2** | DevOps agent onboarding + monitoring tools as agent tools | Real signals from real monitoring, not seeded rows | Partly — flywheel exists, signal *source* is synthetic |
| **W3** | Continuous modernisation as Day-2 | The loop closing without a human starting it | Built and proven once; needs W2 to be genuine |

---

## W1 — Modernisation governed by AIDLC

### The problem, concretely

Today's construction phase does this:

```
Orchestrate_Assessment → Approval_Assessment → Orchestrate_Design → … → ATX transform → PR
```

A human approves at each gate. What they see is an artifact the agent wrote *as a
by-product* of doing the work. Nothing states, before the work starts, what the modernised
`EntitlementResource` is required to do — so nothing can be checked against it afterwards.
When the transform silently half-completed on 2026-08-20 (`transformation_steps` empty, the
workflow still reporting SUCCESS), there was no specification for that success to be
measured against. The masking bug was findable only by reading agent logs.

### What AIDLC adds

AIDLC's inception phase produces artifacts *before* construction, and they are the thing
reviewed at the gate:

| AIDLC artifact | For this workload | Gate that consumes it |
|---|---|---|
| `requirements.md` | What Catalog & Entitlement must do after modernisation — behaviour held invariant, behaviour deliberately changed | `assessment_review` |
| `user-stories.md` | Entitlement check, catalogue lookup, bulk entitlement sync — as stories with acceptance criteria | `assessment_review` |
| `design.md` | Java 17 → 21; Dropwizard 2.1.10 → 4.0.7 (the Jakarta line); `javax.*` → `jakarta.*` | `design_review` |
| `units.md` | The transform decomposed into independently verifiable units, each mapped to files | `migration_plan_review` |
| `audit.md` | Append-only log of every decision and who made it | `governance` view |

**The invariant that makes this more than paperwork:** the ATX transform is instructed from
`units.md`, and the validation phase asserts the acceptance criteria in `user-stories.md`.
A unit that cannot be verified is not a unit. So a half-completed transform fails a named
assertion instead of returning SUCCESS with an empty step list.

### Scope of the modernisation itself

Held invariant — these are the acceptance criteria, not aspirations:

- `GET /entitlements/{subscriberId}` response schema byte-identical
- Entitlement decision semantics unchanged for the full existing decision matrix
- Existing PostgreSQL 14 schema untouched (it is shared with `APP-METADATA` — the
  dependency graph shows the coupling, and breaking it breaks a second application)

Deliberately changed:

- Java 17 → 21 (virtual threads available to the entitlement check path)
- Dropwizard 2.1.10 → **4.0.7** — the first Dropwizard line on `jakarta.*`
- `javax.*` → `jakarta.*` (surface is exactly two files)
- Metrics gain a Prometheus endpoint and a real health check, **so that W2 has something to
  scrape**

> **Correction to the target, recorded rather than quietly resolved.** This document
> originally stated the target as Spring Boot 3, matching the demo narrative. Inspection of
> the transform catalogue shows **no definition performs Dropwizard → Spring Boot**, so that
> target is not reachable by factory automation. PR #2 was correct output for the instruction
> it received. Spring Boot is scoped as `units.md` U5 — harness-assisted, high risk, not
> claimed as automation. See `design.md` §D2 and `audit.md` 04:14Z.

That last line is the seam between W1 and W2 and is the reason the workstreams are ordered
this way. The modernised service must emit signals a DevOps agent can consume; if
modernisation ignores observability, W2 has nothing real to ingest and we are back to
seeded rows.

### Deliverable

`aidlc/spaces/catalog-entitlement/` containing the five artifacts above, **checked in**,
with the modernisation PR referencing the spec commit that authorised it.

---

## W2 — DevOps agent onboarding, monitoring tools as agent tools

### Honest starting position

The Day-2 flywheel is real: `OPS_SIGNAL#` / `OPS_EVENT#` / `OPS_HEALTH` records, four
operations tools, a fail-closed risk gate, and an operations state machine that has run
end-to-end. `APP-CATALOG` currently reads `at_risk` with 2 high-severity signals open and
2 items auto-modernised.

But **the signals were written by hand.** Nothing observed the application. The flywheel is
proven; its input is not. A demo that claims autonomous operations while the signals are
seeded is a demo with a hole in it, and it is the first thing a sceptical engineer will ask
about.

### What onboarding a DevOps agent means

The agent is not new Python. Consistent with the harness architecture — one declarative
Bedrock AgentCore Harness, behaviour composed from markdown playbooks, no agent code —
onboarding means:

1. **A playbook.** `operations.md` exists and already forbids working around a refusal.
   It gains an ingestion section: how to read monitoring, how to classify what it reads,
   and what it is never allowed to conclude on its own.
2. **Monitoring tools registered as MCP tools.** A 7th gateway target, `monitoring`,
   alongside the existing six (portfolio, strategy, planning, documents, execution,
   operations). Proposed tools, each read-only:

   | Tool | Reads | Produces |
   |---|---|---|
   | `query_metrics` | CloudWatch metrics for the service | latency/error/saturation series |
   | `query_logs` | CloudWatch Logs Insights | error-class aggregates |
   | `query_traces` | X-Ray | slow dependency paths |
   | `list_alarms` | CloudWatch alarms in ALARM | active breaches |
   | `query_deployments` | ECS deployment history | change correlation |

   Bring-your-own is the pattern, not a hardcoded vendor: LGI runs Grafana and Dynatrace.
   The target is an adapter boundary — the tool contract is `(service, window) → normalised
   signal`, so a Dynatrace adapter satisfies the same contract as the CloudWatch one, and
   the playbook does not change when the backend does.

3. **Signal derivation, not signal invention.** The agent's job is `observation → OPS_SIGNAL`
   with a stated tier and stated evidence. The record carries the query that produced it so
   a human at the gate can re-run it. **An unevidenced signal is refused, not tiered** —
   this mirrors the existing rule that a missing `risk_tier` is treated as high.

### The safety invariant must survive onboarding

Adding monitoring tools widens what the agent can *see*. It must not widen what the agent
can *do*. The existing three-place defence holds unchanged:

1. `open_scoped_run` refuses high-risk and treats a missing tier as high
2. ASL `RiskChoice` accepts only the literal `'low'`; a failed risk lookup catches to
   `Approval_HighRisk` rather than past it
3. `ResolveRiskTier` reads the **stored** tier from DynamoDB, never the model's own account
   of it

All five monitoring tools are read-only by construction. The agent can observe production
and *propose*; it cannot act on production without passing the gate. That is the sentence
to say out loud when someone asks whether this is safe.

---

## W3 — Continuous modernisation as Day-2

### The claim

Modernisation is not a project that ends at cutover. The estate drifts: a dependency goes
EOL, a base image gains a CVE, a runtime version falls out of support. Each of those is a
signal, and each is a modernisation trigger. The factory that modernised the application
is the same factory that keeps it modern — the only difference is who starts the run.

### The loop, and what is already true

```
monitoring (W2)
   → OPS_SIGNAL with tier + evidence
      → risk gate ──low──→ scoped AMF run → PR → validation → deploy
      │                                                          │
      └──high/unevidenced──→ human approval ─────────────────────┘
                                                                 ↓
                                                     new signals, loop closes
```

Already built and demonstrable: the operations state machine, the fail-closed gate, the
scoped-run tool, and the Operations page showing both halves — what is open *and* what the
flywheel already fixed without being asked. The `auto_triggered` count exists precisely
because showing only open signals displayed **0** at the moment the flywheel had
succeeded, which read as failure when it was the opposite.

What W2 changes: the trigger becomes an observation rather than a fixture.

### The three-tier story to tell

This is the spectrum Abdel asked about — not just version upgrades:

| Tier | Trigger | Autonomy | Example |
|---|---|---|---|
| Keep current | CVE, EOL dependency, patch | Auto, low-risk gate | Spring Boot 3.2.1 → 3.2.5 |
| Keep modern | Runtime EOL, framework major | Human gate, agent proposes | Java 21 → 25 |
| Re-architect | Sustained signal pattern | Human decides, agent evidences | Entitlement hot path → separate service |

The honest line for the third row: the agent proposes and evidences; a human decides.
Nothing in this design re-architects a production service autonomously, and claiming
otherwise would not survive the first hard question.

---

## Sequencing

| Stage | Work | Verifiable by |
|---|---|---|
| 1 | W1 AIDLC artifacts authored and checked in | Files exist; `units.md` maps to real files |
| 2 | E2E run to a modernisation PR, gates reviewing the spec | PR exists, references spec commit |
| 3 | W2 monitoring target + tools + playbook section | Tools listed in gateway; ruff/tsc/terraform clean |
| 4 | Real signal derived from real monitoring | An `OPS_SIGNAL` whose evidence query re-runs |
| 5 | W3 loop closes from a derived signal | Operations page `auto_triggered` increments from a real trigger |
| 6 | Full verification | 146+ backend tests, 22 Lambda, ruff, tsc, terraform validate |

## Known constraints, stated rather than discovered later

- **The AgentCore control plane is not applied.** New runs execute on the previous engine.
  W2's gateway target is therefore written and validated but will not be live until that
  stack is applied. The demo must not claim otherwise.
- **AIDLC has no harness on this branch** — it was deleted in the consolidation. These
  artifacts are hand-maintained and say so. Reinstating the harness is out of scope here.
- **The WAF allowlist gates the live URL.** Whatever network the demo runs from must be on
  it; the preflight script now names the offending IP.
- **Chat is not demonstrable** — it needs the AgentCore runtime and returns a polite
  unavailable message.

## Open question for the reviewer

Only one, and it is a scope question rather than a technical one:

**Does the modernisation PR need to be re-generated under the new spec, or is it acceptable
to retrofit the spec to the existing PR for the demo?** Re-generating is the honest
demonstration and costs an ATX run. Retrofitting is faster and still shows the artifacts,
but the causal claim ("the spec drove the code") would be false, and I would not say it
out loud on a call.

Recommendation: re-generate. The whole point of W1 is that intent precedes code, and a
demo that inverts that is arguing against itself.
