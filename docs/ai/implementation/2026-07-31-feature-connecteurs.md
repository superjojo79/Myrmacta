---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: in-progress
created: 2026-08-03
updated: 2026-08-03
phase5: 2026-08-03
---

# Implementation Guide

## Phase 5 progress

| Task | Statut | Notes |
|---|---|---|
| T1 Models | done | records + enums + exceptions + definition interfaces |
| T2 Ports + properties | done | ports in/out ; `ConnectorProperties` ; yaml |
| T3 Liquibase | done | `003-connector-tables.yaml` ; H2 generated `active_name` |
| T4 JPA + cipher + idempotency | done | EnvMasterKeyCipher AES-GCM ; secrets `enc:v1:` at rest |
| T5 Registry + JsonPath + http-rest | done | SimpleJsonPathMapper ; HttpRestTriggerConnector |
| T6 TriggerRuntime | done | outbox only ; ConnectorCatalog port (ArchUnit OK) |
| T7+ | pending | |

**Preuve T4 :** `./mvnw -Dtest=EnvMasterKeyCipherTest,ConnectorPersistenceIT,ConnectorSchemaIT test` → **9 / 0**.

## Development Setup

- Java 25 (Temurin) — installer si agent n’a que JDK 21.
- Worktree : `.worktrees/feature-connecteurs`.
- `app.connector.crypto.master-key` : vide en yaml principal ; clé test en T4.

## Code Structure

```
domain/connector/model/
domain/connector/port/in|out/
infrastructure/config/ConnectorProperties.java
db/changelog/changes/003-connector-tables.yaml
```

## Implementation Notes

### Liquibase unique name (DD-NAME-UQ)

- PostgreSQL : partial unique index `WHERE deleted_at IS NULL`
- H2 : generated `active_name` + unique `(tenant_id, active_name)`

### ConfigFieldType

Séparé de `dossier.FieldType` (PASSWORD/URL/INTEGER dédiés connecteurs).

## Deviations

Aucune vs design pour T1–T3.

### Crypto (T4)

- Master key Base64 32 bytes (`app.connector.crypto.master-key`)
- DEK per tenant in `tenant_connector_dek` (wrapped with master key)
- Password keys: `*secret` / `*password` / `webhookSecret` / `clientSecret`
- Wire format: `enc:v1:<base64(iv||ciphertext)>`

## Next

T5 — ConnectorRegistry + SimpleJsonPathMapper + http-rest TRIGGER.
