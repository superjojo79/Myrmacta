---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: completed
created: 2026-08-03
updated: 2026-08-03
requirements_aligned: 2026-08-03
design_aligned: 2026-08-03
phase6_evidence: 2026-08-03
phase8: 2026-08-03
phase9: 2026-08-03
---

# Testing Strategy

## Test Coverage Goals
**What level of testing do we aim for?**

- **Unitaires domaine :** mapping déclaratif ; `ConnectorTriggerRuntime`
  (create / existing / batch / disabled) ; validation config auth types ;
  masquage secrets ; cipher encrypt/decrypt round-trip (env key).
- **Unitaires infra :** HMAC + shared secret authenticators ; idempotency
  store ; rate limit / max body ; registry discovery ; `flowable-signal`
  execute (RuntimeService mock).
- **IT H2 MODE=PostgreSQL :** Liquibase tables connector/DEK/idempotency ;
  MockMvc admin CRUD ; webhook happy/fail ; execute BFF + M2M mock ;
  smoke ServiceTask / signal si faisable.
- **Régression :** dossiers, workflow/outbox, auth BFF, ArchUnit, Jacoco ≥ 80 %.
- **Hors scope :** Testcontainers PG, Keycloak réel, charge multi-pod rate
  limit, E2E navigateur, Vault réel.

Alignement requirements success criteria + design DD-*.

## Unit Tests
**What individual components need testing?**

### Mapping déclaratif (HttpRestTriggerConnector / mapper)
- [x] Objet JSON → 1 `WorkflowTriggerEvent` avec fields/variables
- [x] Tableau / `itemsPath` → N événements
- [x] `existingDossierReferencePath` renseigné → pas de create fields requis
- [x] `processKeyPath` override le défaut config
- [x] Chemin manquant / invalide → erreur métier claire

### ConnectorTriggerRuntime
- [x] Create dossier + `outbox.append` par événement
- [x] Existing ref → `getByReference` + `AccessContext.system`
- [x] Ref inconnue → erreur ; pas d’outbox partiel non transactionnel
- [x] Batch : claim atomique avant runtime *(phase 9 `beginOrGetCompleted`)*
- [x] Aucun appel `WorkflowEnginePort.start` / RuntimeService.start

### SimpleJsonPathMapper
- [x] `$.a.b` et `$.items[0].x` résolus
- [x] Chemin invalide → erreur métier
- [x] Pas de support filtres avancés (documenté)

### Registry
- [x] `http-rest` et `flowable-signal` présents
- [x] Type inconnu → `UnknownConnectorException`

### ConnectorSecretCipher (Env)
- [x] Round-trip PASSWORD fields
- [x] Champs non-PASSWORD inchangés
- [x] DEK créée à la première utilisation tenant

### Webhook authenticators
- [x] HMAC valide → OK
- [x] HMAC invalide / header absent → exception
- [x] Shared secret header/token valide / invalide
- [x] Comparaison temps constant (`MessageDigest.isEqual`)

### Idempotency
- [x] Première clé → miss, store après succès *(store IT)*
- [x] Replay même clé → hit, pas de second runtime
- [x] Clé absente → 400 (header requis — webhook T8)

### Rate limit / max body
- [x] Corps > max → 413 (filtre/unit)
- [x] Au-delà du quota/min → 429
- [x] Sous quota → passe

### FlowableSignal SERVICE
- [x] `execute` appelle signal API avec nom config / override
- [x] Instance disabled → 404 (`ExecuteConnectorUseCase` + MockMvc)
- [x] Soft-deleted → 404 (MockMvc)
- [x] Type non-SERVICE → rejet

### FlowableSignalEventListener (TRIGGER)
- [x] Signal name match → runtime appelé
- [x] Instance soft-deleted / disabled ignorée
- [x] Signal name sans instance → no-op

## Integration Tests
**What component interactions need testing?**

### Liquibase / JPA
- [x] Tables `connector_instance`, `tenant_connector_dek`,
      `webhook_idempotency` créées
- [x] Persist instance : secrets chiffrés en colonne JSON (pas clair)
- [x] Load → `ConnectorInstanceData` config déchiffrée

### MockMvc — admin
- [x] CRUD create + list definitions `TENANT_ADMIN` ; CSRF requis mutateurs
- [x] Non-admin → 403 (`ConnectorAdminApiTest`)
- [x] GET/create masque secret / `secretConfigured`
- [x] Config `IP_ALLOWLIST` → 400
- [x] DELETE soft-delete : hors liste ; GET → 404
- [x] PATCH restore soft-deleted → visible à nouveau
- [x] Non-admin PATCH restore → 403
- [x] Nom dupliqué actif → 409 ; réemploi après soft delete OK

### MockMvc — webhook
- [x] HMAC happy (`X-Signature-256`) → 202
- [x] Signature KO → **401** ; 0 runtime
- [x] Shared secret via `X-Webhook-Token` happy path → 202
- [x] Replay `Idempotency-Key` → status stocké ; pas de second runtime
- [x] Sans `Idempotency-Key` → 400
- [x] `enabled=false` / id inconnu → 404
- [x] Body trop gros → 413
- [x] Rate limit → 429
- [ ] Existing dossier ref → outbox sans create (seed dossier)
  *(couvert unit `ConnectorTriggerRuntimeImplTest`)*

### MockMvc / Security — execute (dual URL)
- [x] BFF `POST /api/connectors/{id}/execute` `TENANT_ADMIN` + CSRF → 200
- [x] BFF non-admin → 403
- [x] M2M `POST /api/m2m/connectors/{id}/execute` JWT `API_SERVICE` + tenant OK → 200
- [x] M2M mauvais tenant / sans rôle → 403 ; anonyme → 401
- [x] Chaînes Order 1/2/3 bootent avec le contexte Spring

### Flowable smoke
- [x] Process classpath `signalCatchSmoke` réveillé par SERVICE
- [x] Listener TRIGGER unit : match signalName → runtime

## End-to-End Tests
**Hors scope navigateur.**

- [ ] Webhook → outbox PENDING → relais start workflow *(runtime unit outbox OK ;
  E2E full relay hors scope V1)*

## Performance Testing
N/A — bornes 413/429 couvertes en MockMvc.

## Security Testing
- [x] Pas de secret clair dans JSON réponses admin
- [ ] Pas de secret clair dans logs assertés (si capture) — hors scope V1
- [x] Chaînes Order 1/2/3 : webhook sans CSRF ; BFF avec CSRF ; M2M JWT
- [x] ArchUnit : domaine connector sans dépendance infra JPA/web

## Test files (phase 8)

| Fichier | Focus |
|---|---|
| `ConnectorAdminApiTest` | definitions, mask, IP_ALLOWLIST, 409, soft-delete/restore, 403 |
| `WebhookControllerTest` | shared secret, HMAC, 401, 404, idempotency, 413, 429 |
| `ConnectorExecuteApiTest` | BFF + M2M execute, tenant mismatch, authority |
| `SignalSmokeIT` | BPMN catch + SERVICE signal |
| `*UseCase*Test`, `*AuthenticatorTest`, `*Cipher*`, registry, mapper | unit |

## Test Data & Fixtures
- Clé maître test fixe dans `src/test/resources/application.properties`
- `app.security.m2m.jwt-secret` HMAC pour JwtDecoder local
- Spring Security Test : `oidcLogin()`, `jwt()`

## Evidence

### T12 / phase 7
```text
./mvnw verify → 271 tests / 0 fail ; Jacoco INSTRUCTION 85.52%
```

### Phase 8
```text
./mvnw -Dtest=WebhookControllerTest,ConnectorAdminApiTest,ConnectorExecuteApiTest test
→ 23 / 0 (puis étendu)
```

### Phase 9
```text
./mvnw verify → 291 tests / 0 fail ; Jacoco INSTRUCTION 87.62%
Fixes: atomic idempotency, execute 404 coverage, PATCH restore body required
```

## Success criteria → scenarios

| Critère requirements | Couverture tests |
|---|---|
| Catalogue 2 builtins | Registry unit + IT list definitions |
| CRUD admin + masquage | `ConnectorAdminApiTest` |
| HMAC + shared secret | Authenticator unit + `WebhookControllerTest` |
| Mapping + batch + existing | Mapper unit + runtime unit |
| Outbox only | Runtime unit verify no start |
| Idempotency + harden | `WebhookControllerTest` |
| ServiceTask + execute BFF/M2M | Unit signal + `ConnectorExecuteApiTest` + smoke |
| Security 3 chaînes | Security IT / MockMvc execute |
| Jacoco ≥ 80 % / ArchUnit | `./mvnw verify` |

## Mocks & tools

- Mockito : RuntimeService, OutboxRepository, dossier use cases
- Spring Security test : `oidcLogin()`, `jwt()` M2M
- H2 MODE=PostgreSQL + Liquibase (existant)
