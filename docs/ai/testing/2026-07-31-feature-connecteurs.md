---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: draft
created: 2026-08-03
updated: 2026-08-03
requirements_aligned: 2026-08-03
design_aligned: 2026-08-03
phase6_evidence: 2026-08-03
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
- [ ] Batch : tout-ou-rien transactionnel *(TX au controller T8)*
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
- [x] Instance disabled → rejet *(ExecuteConnectorUseCase)*
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
- [ ] Non-admin → 403 ProblemDetail *(partiel — PreAuthorize présent)*
- [x] GET/create masque secret / `secretConfigured`
- [ ] Config `IP_ALLOWLIST` → 400 *(validé côté builtin validate)*
- [ ] DELETE soft-delete : hors liste ; GET → 404 *(use case softDelete unit OK)*
- [x] Restore soft-deleted → visible à nouveau *(ManageConnectorInstanceUseCaseImplTest)*
- [ ] Non-admin PATCH restore → 403
- [ ] Nom dupliqué actif → 409 ; réemploi après soft delete OK

### MockMvc — webhook
- [ ] HMAC happy (`X-Signature-256`) → 202 ; outbox rows = N *(HMAC unit OK)*
- [ ] Signature KO → **401** ; 0 outbox *(auth unit OK)*
- [x] Shared secret via `X-Webhook-Token` happy path → 202
- [x] Replay `Idempotency-Key` → status stocké ; pas de second runtime
- [x] Sans `Idempotency-Key` → 400
- [ ] `enabled=false` / soft-deleted / id inconnu → 404
- [x] Body trop gros → 413
- [x] Rate limit → 429
- [ ] Existing dossier ref → outbox sans create (seed dossier)

### MockMvc / Security — execute (dual URL)
- [ ] BFF `POST /api/connectors/{id}/execute` `TENANT_ADMIN` + CSRF → 200 *(controllers + use case unit OK)*
- [ ] BFF non-admin → 403
- [ ] M2M `POST /api/m2m/connectors/{id}/execute` JWT `API_SERVICE` + tenant OK → 200
- [ ] M2M mauvais tenant / sans rôle → 401/403
- [x] Chaînes Order 1/2/3 bootent avec le contexte Spring (IT admin/webhook)

### Flowable smoke
- [x] Process classpath `signalCatchSmoke` réveillé par SERVICE `signalEventReceivedWithTenantId`
- [x] Listener TRIGGER unit : match signalName → runtime

## End-to-End Tests
**Hors scope navigateur.** Parcours « API » couverts en IT MockMvc + outbox
relay existant (rejeu seed) :

- [ ] Webhook → outbox PENDING → (relais existant) start workflow smoke *(runtime unit outbox OK)*

## Performance Testing
N/A — vérifier uniquement le respect des bornes config en tests
fonctionnels (413/429).

## Security Testing
- [x] Pas de secret clair dans JSON réponses admin (create masks)
- [ ] Pas de secret clair dans logs assertés (si capture)
- [x] Chaînes Order 1/2/3 : webhook sans CSRF ; BFF avec CSRF
- [x] ArchUnit : domaine connector sans dépendance infra JPA/web

## Test Data & Fixtures
- Clé maître test fixe dans `src/test/resources/application.properties`
- Instance `http-rest` fixture (secret connu pour HMAC vectors)
- JWT M2M test via `app.security.m2m.jwt-secret` HMAC

## Evidence T12

```text
./mvnw verify → 271 tests / 0 fail ; Jacoco INSTRUCTION 85.52%
```


## Success criteria → scenarios

| Critère requirements | Couverture tests |
|---|---|
| Catalogue 2 builtins | Registry unit + IT list definitions |
| CRUD admin + masquage | MockMvc admin |
| HMAC + shared secret | Authenticator unit + MockMvc |
| Mapping + batch + existing | Mapper unit + runtime + MockMvc |
| Outbox only | Runtime unit verify no start |
| Idempotency + harden | Unit + MockMvc |
| ServiceTask + execute BFF/M2M | Unit signal + MockMvc execute |
| Security 3 chaînes | Security IT / MockMvc |
| Jacoco ≥ 80 % / ArchUnit | `./mvnw verify` |

## Mocks & tools

- Mockito : RuntimeService, OutboxRepository, dossier use cases
- Spring Security test : `oidcLogin()`, JWT mock M2M
- H2 MODE=PostgreSQL + Liquibase (existant)
