---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: completed
created: 2026-08-03
updated: 2026-08-03
phase5: 2026-08-03
phase6: 2026-08-03
phase7: 2026-08-03
phase7_result: aligned
---

# Implementation Guide

## Phase 7 — Check Implementation

**Résultat :** aligné sur design/requirements `reviewed` (écarts mineurs
documentés). Suite : **Phase 8** (`dev-testing`).

### Evidence (fresh)

```text
JAVA_HOME=…/jdk-25.0.4+7 ./mvnw verify
→ Tests run: 271, Failures: 0, Errors: 0
→ Jacoco INSTRUCTION 9352/10935 = 85.52%
→ npx ai-devkit lint --feature connecteurs → All checks passed
→ task CLI : unavailable (`unknown command 'task'`)
```

### Design coverage matrix

| Décision / surface | Impl | Statut |
|---|---|---|
| Registry + `http-rest` TRIGGER + `flowable-signal` BOTH | builtins + ConnectorRegistry | OK |
| DD-JSONPATH parser maison | SimpleJsonPathMapper | OK |
| DD-CIPHER EnvMasterKeyCipher + DEK | EnvMasterKeyCipher + tenant_connector_dek | OK |
| DD-NAME-UQ unique partiel / H2 active_name | 003-connector-tables.yaml | OK |
| DD-SOFT soft delete + PATCH restore | Manage + AdminController | OK |
| DD-MASK secrets `********` | ConnectorResponse | OK |
| DD-HEADERS HMAC / token / Idempotency-Key | authenticators + WebhookController | OK |
| DD-HARDEN 1 MiB / 120/min / TTL 24h | ConnectorProperties + rate limiter | OK |
| DD-404 disabled/deleted webhook | filter enabled + find non-deleted | OK |
| DD-EXECUTE-ROUTE dual URL | ConnectorExecute + M2mConnectorExecute | OK |
| DD-M2M Order 1 JWT API_SERVICE + tenant_id | SecurityConfig + M2mTenantValidationFilter | OK |
| Webhook Order 2 / BFF Order 3 | SecurityConfig | OK |
| DD-SIGNAL-HOOK ACTIVITY_SIGNALED | FlowableSignalEventListener | OK (O5 clos) |
| ConnectorServiceTask | `${connectorServiceTask}` | OK |
| Outbox only (pas start synchrone) | ConnectorTriggerRuntimeImpl | OK |
| Surface `/api/connector-definitions` | ConnectorDefinitionController | OK |
| Admin CRUD paths | ConnectorAdminController | OK |
| ProblemDetail 401/404/409/413/429 | ConnectorExceptionHandler | OK |

### Deviations (severity)

| # | Écart | Sévérité | Disposition |
|---|---|---|---|
| D1 | Design diagramme « un » `ConnectorExecutionController` ; code = 2 controllers | basse | Accepté — même use case, URLs duales respectées |
| D2 | Body execute expose `processInstanceId` ; SERVICE n’utilise que `signalName` + `variables` | basse | Documenté ; ciblage executionId hors V1 |
| D3 | Idempotence webhook = find+store (pas reserve atomique) ; course concurrente possible | moyenne | Follow-up phase 8 / ops ; PK limite le double-store |
| D4 | Smoke IT SERVICE catch OK ; TRIGGER listener → outbox seulement en unit | basse | Couverture partielle acceptée V1 |
| D5 | Doublon `/api/connectors/definitions` retiré en phase 7 (catalogue = `/api/connector-definitions` only) | — | Corrigé |

### Gaps tests (non bloquants phase 7)

- MockMvc HMAC happy / auth 401 E2E (unit authenticators OK)
- MockMvc dual execute JWT M2M
- MockMvc 409 nom dupliqué / soft-delete list 404
→ à traiter en **Phase 8** (`dev-testing`)

## Phase 5–6 progress

| Task | Statut | Notes |
|---|---|---|
| T1–T13 | done | voir planning `completed` |

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

- Headers : `X-Signature-256` (`sha256=<hex>`) ou `X-Webhook-Token` ;
  `Idempotency-Key` **requis**
- Auth KO → **401** ; unknown/disabled/deleted → **404**
- Max body 1 MiB → 413 ; rate 120/min/instance → 429
- Runtime → outbox only

### Dual execute (T9)

- BFF `POST /api/connectors/{id}/execute` (`TENANT_ADMIN` + CSRF)
- M2M `POST /api/m2m/connectors/{id}/execute` (`API_SERVICE`)
- Même `ExecuteConnectorUseCaseImpl`

### flowable-signal (T10–T11)

- Capability **BOTH** ; config `signalName`, `processKey`, `dossierTypeCode`
- SERVICE : `RuntimeService.signalEventReceivedWithTenantId`
- `ConnectorServiceTask` lit `connectorInstanceId` / `connectorInput`
- TRIGGER : `FlowableSignalEventListener` sur `ACTIVITY_SIGNALED`
- Smoke : `signal-smoke.bpmn20.xml`

## Non-goals / DEFERRED

- smtp / s3 / scheduler builtins ; http-rest SERVICE (SSRF)
- IP allowlist ; Vault/KMS réel ; UI admin
- Dedup TRIGGER signal `(signalName, executionId, instanceId)`
- Rate-limit multi-pod ; reserve atomique idempotency (D3)
- Ciblage `processInstanceId` / executionId sur execute (D2)

## Follow-ups

1. Phase 8 — combler gaps MockMvc listés ci-dessus
2. Phase 9 — `dev-review`
3. Ops : documenter rotation DEK / Vault adapter
