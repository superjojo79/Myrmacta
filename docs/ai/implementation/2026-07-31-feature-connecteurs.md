---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: completed
created: 2026-08-03
updated: 2026-08-03
phase5: 2026-08-03
phase6: 2026-08-03
---

# Implementation Guide

## Phase 5–6 progress

| Task | Statut | Notes |
|---|---|---|
| T1 Models | done | records + enums + exceptions + definition interfaces |
| T2 Ports + properties | done | ports in/out ; `ConnectorProperties` ; yaml |
| T3 Liquibase | done | `003-connector-tables.yaml` ; H2 generated `active_name` |
| T4 JPA + cipher + idempotency | done | EnvMasterKeyCipher AES-GCM ; secrets `enc:v1:` at rest |
| T5 Registry + JsonPath + http-rest | done | SimpleJsonPathMapper ; HttpRestTriggerConnector |
| T6 TriggerRuntime | done | outbox only ; ConnectorCatalog port (ArchUnit OK) |
| T7 Security chains | done | Order 1 M2M JWT / 2 webhooks / 3 BFF |
| T8 Webhook pipeline | done | HMAC + shared secret ; rate/body ; Idempotency-Key |
| T9 Admin + dual execute | done | TENANT_ADMIN CRUD ; BFF + M2M execute |
| T10 flowable-signal SERVICE | done | RuntimeService + `connectorServiceTask` |
| T11 Signal TRIGGER + smoke | done | `ACTIVITY_SIGNALED` listener ; signal-smoke BPMN |
| T12 verify | done | 271/0 ; Jacoco instructions 85,52 % |
| T13 docs | done | ce fichier + planning completed |

## Development Setup

- Java 25 (Temurin) — `JAVA_HOME=/home/ubuntu/java/jdk-25.0.4+7`.
- Worktree : `.worktrees/feature-connecteurs` (branche `feature-connecteurs`).
- `app.connector.crypto.master-key` : requis en prod ; clé test Base64 32 bytes
  dans `src/test/resources/application.properties`.
- M2M tests : `app.security.m2m.jwt-secret` (HMAC HS256) si pas d’issuer-uri.

## Code Structure

```
domain/connector/model|port|usecase/
infrastructure/connector/{builtin,crypto,mapping,registry}/
infrastructure/persistence/connector/
infrastructure/web/connector/          # admin, webhook, execute BFF/M2M
infrastructure/flowable/               # ConnectorServiceTask, FlowableSignalEventListener
infrastructure/security/               # SecurityConfig Order 1/2/3 + M2M JWT
db/changelog/changes/003-connector-tables.yaml
processes/signal-smoke.bpmn20.xml
```

## Implementation Notes

### Security chains (T7)

1. `@Order(1)` `/api/m2m/**` — oauth2 resource-server JWT, authority `API_SERVICE`,
   `M2mTenantValidationFilter` (claim `tenant_id` == `app.tenant.id`), CSRF off.
2. `@Order(2)` `/api/webhooks/**` — permitAll Spring ; auth métier HMAC/token.
3. `@Order(3)` BFF OIDC — inchangé.

`JwtDecoder` : issuer-uri si présent, sinon HMAC `app.security.m2m.jwt-secret`,
sinon refuse.

### Crypto (T4)

- Master key Base64 32 bytes (`app.connector.crypto.master-key`)
- DEK per tenant in `tenant_connector_dek` (wrapped with master key)
- Password keys: `*secret` / `*password` / `webhookSecret` / `clientSecret`
- Wire format: `enc:v1:<base64(iv||ciphertext)>`
- API responses mask secrets as `********` + `secretConfigured` map

### Webhooks (T8)

- Headers : `X-Signature-256` (HMAC) ou `X-Webhook-Token` ; `Idempotency-Key` **requis**
- Auth KO → **401** (pas 404) ; unknown/disabled/deleted → **404**
- Max body 1 MiB → 413 ; rate 120/min/instance → 429
- Runtime → outbox only (pas de start Flowable synchrone)

### Dual execute (T9)

- BFF `POST /api/connectors/{id}/execute` (session + CSRF)
- M2M `POST /api/m2m/connectors/{id}/execute` (JWT)
- Même `ExecuteConnectorUseCaseImpl`

### flowable-signal (T10–T11)

- Capability **BOTH** ; config `signalName`, `processKey`, `dossierTypeCode`
- SERVICE : `RuntimeService.signalEventReceivedWithTenantId` (tenant-scoped deployments)
- `ConnectorServiceTask` (`${connectorServiceTask}`) lit `connectorInstanceId` /
  `connectorInput` → use case ; écrit `connectorResult`
- TRIGGER : `FlowableSignalEventListener` sur `ACTIVITY_SIGNALED` ; match
  `config.signalName` ; ignore disabled ; `isFailOnException=false`
- Smoke : `signal-smoke.bpmn20.xml` (`signalCatchSmoke` / `signalEmitSmoke`)

### Liquibase unique name (DD-NAME-UQ)

- PostgreSQL : partial unique index `WHERE deleted_at IS NULL`
- H2 : generated `active_name` + unique `(tenant_id, active_name)`

## Non-goals / DEFERRED

- smtp / s3 / scheduler builtins
- http-rest SERVICE (SSRF)
- IP allowlist webhook auth
- Vault / KMS réel (port prêt ; env master key only)
- UI admin
- Dedup TRIGGER signal `(signalName, executionId, instanceId)` — nice-to-have
- Rate-limit multi-pod (in-memory V1)

## Deviations

- Aucune vs design tranché pour T1–T13.
- O5 événement Flowable clos : `ACTIVITY_SIGNALED` (pas Event Registry).

## Validation evidence (T12)

```text
JAVA_HOME=…/jdk-25.0.4+7 ./mvnw verify
→ Tests run: 271, Failures: 0, Errors: 0
→ Jacoco INSTRUCTION 9352/10935 = 85.52%
```
