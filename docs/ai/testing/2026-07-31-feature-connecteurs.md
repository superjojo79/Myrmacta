---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: draft
created: 2026-08-03
updated: 2026-08-03
requirements_aligned: 2026-08-03
design_aligned: 2026-08-03
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
- [ ] Objet JSON → 1 `WorkflowTriggerEvent` avec fields/variables
- [ ] Tableau / `itemsPath` → N événements
- [ ] `existingDossierReferencePath` renseigné → pas de create fields requis
- [ ] `processKeyPath` override le défaut config
- [ ] Chemin manquant / invalide → erreur métier claire

### ConnectorTriggerRuntime
- [ ] Create dossier + `outbox.append` par événement
- [ ] Existing ref → `getByReference` + `AccessContext.system`
- [ ] Ref inconnue → erreur ; pas d’outbox partiel non transactionnel
- [ ] Batch : tout-ou-rien transactionnel
- [ ] Aucun appel `WorkflowEnginePort.start` / RuntimeService.start

### ConnectorSecretCipher (Env)
- [ ] Round-trip PASSWORD fields
- [ ] Champs non-PASSWORD inchangés
- [ ] DEK créée à la première utilisation tenant

### Webhook authenticators
- [ ] HMAC valide → OK
- [ ] HMAC invalide / header absent → exception
- [ ] Shared secret header/token valide / invalide
- [ ] Comparaison temps constant (`MessageDigest.isEqual`)

### Idempotency
- [ ] Première clé → miss, store après succès
- [ ] Replay même clé → hit, pas de second runtime
- [ ] Clé absente → traité sans dédup (ou 400 si on impose header —
    **imposer header requis** sur webhook)

### Rate limit / max body
- [ ] Corps > max → 413 (filtre/unit)
- [ ] Au-delà du quota/min → 429
- [ ] Sous quota → passe

### FlowableSignal SERVICE
- [ ] `execute` appelle signal API avec nom config / override
- [ ] Instance disabled → rejet
- [ ] Type non-SERVICE → rejet

### SimpleJsonPathMapper
- [ ] `$.a.b` et `$.items[0].x` résolus
- [ ] Chemin invalide → erreur métier
- [ ] Pas de support filtres avancés (documenté)

### FlowableSignalEventListener (TRIGGER)
- [ ] Signal name match → runtime appelé
- [ ] Instance soft-deleted / disabled ignorée
- [ ] Signal name sans instance → no-op

### Registry
- [ ] `http-rest` et `flowable-signal` présents
- [ ] Type inconnu → `UnknownConnectorException`

## Integration Tests
**What component interactions need testing?**

### Liquibase / JPA
- [ ] Tables `connector_instance`, `tenant_connector_dek`,
      `webhook_idempotency` créées
- [ ] Persist instance : secrets chiffrés en colonne JSON (pas clair)
- [ ] Load → `ConnectorInstanceData` config déchiffrée

### MockMvc — admin
- [ ] CRUD `TENANT_ADMIN` OK ; CSRF requis mutateurs
- [ ] Non-admin → 403 ProblemDetail
- [ ] GET masque secret / `secretConfigured`
- [ ] Config `IP_ALLOWLIST` → 400
- [ ] DELETE soft-delete : hors liste ; GET → 404
- [ ] PATCH restore soft-deleted → visible à nouveau (`TENANT_ADMIN`)
- [ ] Non-admin PATCH restore → 403
- [ ] Nom dupliqué actif → 409 ; réemploi après soft delete OK

### MockMvc — webhook
- [ ] HMAC happy (`X-Signature-256`) → 202 ; outbox rows = N
- [ ] Signature KO → **401** ; 0 outbox
- [ ] Shared secret via `X-Webhook-Token` happy path
- [ ] Replay `Idempotency-Key` → 202 ; outbox inchangé
- [ ] Sans `Idempotency-Key` → 400 (pas d’alias `X-Delivery-Id`)
- [ ] `enabled=false` / soft-deleted / id inconnu → 404
- [ ] Body trop gros → 413
- [ ] Rate limit → 429
- [ ] Existing dossier ref → outbox sans create (seed dossier)

### MockMvc / Security — execute (dual URL)
- [ ] BFF `POST /api/connectors/{id}/execute` `TENANT_ADMIN` + CSRF → 200
- [ ] BFF non-admin → 403
- [ ] M2M `POST /api/m2m/connectors/{id}/execute` JWT `API_SERVICE` + tenant OK → 200
- [ ] M2M mauvais tenant / sans rôle → 401/403
- [ ] M2M path n’exige pas CSRF ; BFF path l’exige

### Flowable smoke (si câblé)
- [ ] Process classpath avec `ConnectorServiceTask` émet signal
- [ ] Trigger signal → outbox (chemin TRIGGER)

## End-to-End Tests
**Hors scope navigateur.** Parcours « API » couverts en IT MockMvc + outbox
relay existant (rejeu seed) :

- [ ] Webhook → outbox PENDING → (relais existant) start workflow smoke

## Performance Testing
N/A — vérifier uniquement le respect des bornes config en tests
fonctionnels (413/429).

## Security Testing
- [ ] Pas de secret clair dans JSON réponses admin
- [ ] Pas de secret clair dans logs assertés (si capture)
- [ ] Chaînes Order 1/2/3 : webhook sans CSRF ; BFF avec CSRF
- [ ] ArchUnit : domaine connector sans dépendance infra JPA/web

## Test Data & Fixtures
- Clé maître test fixe dans `application-test.yml`
- Instance `http-rest` fixture (secret connu pour HMAC vectors)
- Dossier seed pour existing-ref
- JWT M2M test (même pattern que resource server de test à introduire)

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
