---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: draft
created: 2026-08-20
updated: 2026-08-20
feature: observabilite
phase1: 2026-08-20
requirements_ref: docs/ai/requirements/2026-08-20-feature-observabilite.md
---

# Testing Strategy — `observabilite`

> Draft Phase 1. Cases dérivés des success criteria §29. SPA **non**
> touché : Playwright = régression suite existante, **pas** « hors
> scope » pour contourner Phase 8 — simplement **aucun nouveau** spec
> actuator (contrat non consommé par le SPA).

## Test Coverage Goals

- Backend : Jacoco ≥ seuil projet sur le code **nouveau/modifié**
  (`./mvnw test` / `verify`).
- ArchUnit : domaine sans Micrometer / MDC / actuator (étendre la
  règle existante si besoin).
- Frontend unit : Vitest inchangé (`cd frontend && npm run test`).
- Frontend E2E : `cd frontend && npm run test:e2e` vert (régression).

## Unit Tests

### MDC

- [ ] Filtre HTTP : `tenantId` = `app.tenant.id` pendant la chaîne ;
      MDC vide après (y compris si le controller jette).
- [ ] Job / relay : put `dossierId` / `processInstanceId` autour d’un
      item ; clear entre deux items du batch.
- [ ] Requête sans dossier : pas de clé `dossierId` fantôme.

### Métriques

- [ ] Âge outbox : PENDING ancien → gauge ≈ now - createdAt ;
      outbox vide → 0 (A-EMPTY-AGE).
- [ ] Timer PDF : `render` succès **et** exception observés.
- [ ] Jauge SSE : register + unregister (timeout/erreur) met à jour
      le compteur.
- [ ] DLQ webhooks : count seulement `WEBHOOK`+`DEAD` (EMAIL/BROWSER
      DEAD exclus).
- [ ] Flowable : gauge reflète le count dead-letter (mock
      `ManagementService`).

### Audit lecture (si Q1 = B)

- [ ] GET dossier par id / par ref → `SECURITY_AUDIT`
      `event=dossier_read` (ListAppender, pattern existant
      `DossierApiTest`).
- [ ] GET content élément → `element_content_read`.
- [ ] GET liste paginée dossiers : **pas** d’événement read.
- [ ] Pas de valeur de champ / bytes dans le message.

### Actuator

- [ ] `prometheus` contient les noms de métriques §29.
- [ ] Endpoints non listés (`beans`, `env`, `heapdump`) → 404.
- [ ] Q2 = A : scrape sur port management **sans** session OIDC ;
      port applicatif ne sert pas prometheus (ou le refuse).

## Integration Tests

- [ ] MockMvc (ou test Boot) : une requête dossier pose MDC observable
      via appender **pendant** l’appel.
- [ ] Persistence : requête âge outbox sur H2 MODE=PostgreSQL
      (pattern `WorkflowPersistenceTest`).
- [ ] ArchUnit étendu : `..domain..` ↛ `io.micrometer..`,
      `org.slf4j.MDC`, `org.springframework.boot.actuate..`.

## End-to-End Tests

- [ ] Régression Playwright existante verte (`npm run test:e2e`).
- [ ] **Pas** de nouveau spec SPA (D-FE).
- [ ] Mocks `frontend/e2e/**` inchangés (pas de nouveau shape API
      métier).

## Test Data

- Outbox : une ligne PENDING datée dans le passé ; base vide.
- Notification outbox : mix EMAIL/WEBHOOK × PENDING/DEAD.
- PDF : HTML minimal déjà utilisé par les tests de rendu.
- SSE : emitters mockés (`SseNotificationAdapterTest`).
- Audit : utilisateur `oidcLogin()` existant.

## Test Reporting & Coverage

```bash
./mvnw verify -Dmaven.compiler.release=21
cd frontend && npm run test
cd frontend && npm run test:e2e
```

Phase 8 : coller les sorties fraîches (counts). Jacoco : justifier
tout fichier infra nouveau sous le seuil.

## Manual Testing

- Scrape local `curl` prometheus (port management).
- Une génération PDF + une notif webhook forcée DEAD → métriques
  bougent.
- Grep logs JSON : `tenantId` présent.

Hors CI : Keycloak / PG réels (comme les autres lots).

## Performance Testing

Hors lot (§28). Smoke : binder n’exécute pas de full table scan
non borné (vérifier le SQL en revue design).

## Bug Tracking

Écarts vs §29 = bugs bloquants Phase 9. Fuite MDC = bloquant
sécurité/corrélation.
