---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: reviewed
created: 2026-08-03
updated: 2026-08-13
checked: 2026-08-03
reviewed: 2026-08-03
phase5: 2026-08-03
phase6: 2026-08-03
phase7: 2026-08-03
phase7_result: aligned
phase8: 2026-08-03
phase9: 2026-08-03
phase9_result: ready-with-followups
---

# Implementation Guide

## Phase 9 — Code Review

**Résultat :** prêt à merger avec follow-ups non bloquants
(`phase9_result: ready-with-followups`).

### Evidence (fresh)

```text
JAVA_HOME=…/jdk-25.0.4+7 ./mvnw verify
→ Tests run: 291, Failures: 0, Errors: 0
→ Jacoco INSTRUCTION 9707/11079 = 87.62%
→ npx ai-devkit lint --feature connecteurs → All checks passed
→ task CLI : unavailable
```

### Blocking findings — fixed in phase 9

| # | Finding | Fix |
|---|---|---|
| B1 | Idempotency find→process→store race | `beginOrGetCompleted` atomic INSERT status=0 + `complete` |
| B2 | `DataIntegrityViolation` always « name exists » | Message narrowed; in-flight → 409 dédié |
| B3 | Execute soft-deleted/disabled untested | Unit + MockMvc 404 |
| B4 | PATCH body null = restore footgun | Body required ; restore via `{"restore":true}` |

### Important follow-ups (non-blocking)

| # | Item | Recommendation |
|---|---|---|
| I1 | SERVICE signal → TRIGGER listener side effects | Dedup `(signalName, executionId)` or suppress reentrancy |
| I2 | Max-body after full buffer | Container/filter limit ≤ 1 MiB |
| I3 | Rate-limit before auth | Count after auth / separate unauth budget |
| I4 | DEK create race | UPSERT / catch unique then re-read |
| I5 | Liquibase 003 sans rollback | Add rollback blocks |
| I6 | M2M/BFF Security 401/403 corps vide | Align ProblemDetail if product wants RFC7807 everywhere |

### Nice-to-have

- Heuristique crypto `*secret`/`*password` → baser sur `ConfigFieldType.PASSWORD`
- Purge TTL idempotency (O3)
- Factoriser `problem()` / `isSecretKey` avec dossier/workflow

### Checklist phase 9

- [x] Design match (DD-* + surface API)
- [x] Logic gaps bloquants corrigés (idempotency, 409, PATCH, execute 404)
- [x] Security addressed (3 chaînes, HMAC, mask, soft-delete)
- [x] Integration points verified (outbox, Flowable signal smoke)
- [x] Tests cover changes (`./mvnw verify` 291/0)
- [x] Docs updated (implementation + testing + ce rapport)

## Phase 7 — Check Implementation

**Résultat :** aligné sur design/requirements `reviewed`.

### Design coverage matrix

| Décision / surface | Impl | Statut |
|---|---|---|
| Registry + builtins | ConnectorRegistry + http-rest + flowable-signal | OK |
| DD-JSONPATH / DD-CIPHER / DD-NAME-UQ / DD-SOFT / DD-MASK | OK | OK |
| DD-HEADERS / DD-HARDEN / DD-404 / DD-EXECUTE-ROUTE / DD-M2M | OK | OK |
| DD-SIGNAL-HOOK ACTIVITY_SIGNALED | FlowableSignalEventListener | OK |
| DD-IDEM atomic claim | beginOrGetCompleted + complete | OK (phase 9) |

## Development Setup

- Java 25 — `JAVA_HOME=/home/ubuntu/java/jdk-25.0.4+7`
- Worktree `.worktrees/feature-connecteurs`
- `app.connector.crypto.master-key` + `app.security.m2m.jwt-secret` en test

## Code Structure

```
domain/connector/model|port|usecase/
infrastructure/connector/{builtin,crypto,mapping,registry}/
infrastructure/persistence/connector/
infrastructure/web/connector/
infrastructure/flowable/
infrastructure/security/
db/changelog/changes/003-connector-tables.yaml
processes/signal-smoke.bpmn20.xml
```

## Implementation Notes

### Idempotency (phase 9)

1. `beginOrGetCompleted` : INSERT `response_status=0` (in-flight) or return completed status
2. Concurrent claim → PK conflict → re-read ; in-flight → `WebhookIdempotencyInFlightException` (409)
3. After runtime success → `complete(..., 202)`

### Security / webhooks / dual execute / flowable-signal

Voir notes phases 5–8 (inchangées hors idempotency et PATCH).

## Non-goals / DEFERRED

- smtp / s3 / scheduler ; http-rest SERVICE ; IP allowlist ; Vault ; UI
- Dedup TRIGGER signal (I1)
- Rate-limit multi-pod ; filter max-body amont (I2/I3)

## Follow-ups post-merge

1. I1–I6 ci-dessus
2. Ops : rotation DEK / Vault adapter
