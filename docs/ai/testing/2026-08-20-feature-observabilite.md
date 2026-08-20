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

> Draft Phase 1. **D-FE acté** : Vitest **et** Playwright **obligatoires**
> (écran admin + contrat API consommé par le SPA). Ne pas déclarer
> Playwright hors scope.

## Test Coverage Goals

- Backend : Jacoco ≥ seuil projet sur le code **nouveau/modifié**
  (`./mvnw test` / `verify`).
- ArchUnit : domaine sans Micrometer / MDC / actuator (étendre la
  règle existante si besoin).
- Frontend unit : `cd frontend && npm run test` (vue KPI + nav AuthZ).
- Frontend E2E : `cd frontend && npm run test:e2e` — **nouveau** spec
  admin + régression.

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

### Snapshot API

- [ ] `GET /api/observability` : `TENANT_ADMIN` → 200 + cinq SLI.
- [ ] `DOSSIER_MANAGER` / viewer → 403.
- [ ] Corps sans secrets / payloads.

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

### Frontend Vitest

- [ ] `ObservabilityAdminView` affiche les cinq KPI (mock API).
- [ ] État erreur ProblemDetail.
- [ ] Bouton Rafraîchir re-fetch (Q6 reco A).
- [ ] `AppShell` : lien « Observabilité » si `TENANT_ADMIN` ; absent
      sinon (étendre `AppShell.spec.ts`).
- [ ] Aucun bouton retry/force.

## Integration Tests

- [ ] MockMvc snapshot + MDC pendant un GET dossier.
- [ ] Persistence : requête âge outbox sur H2 MODE=PostgreSQL
      (pattern `WorkflowPersistenceTest`).
- [ ] ArchUnit étendu : `..domain..` ↛ `io.micrometer..`,
      `org.slf4j.MDC`, `org.springframework.boot.actuate..`.

## End-to-End Tests

Pattern File purge (`E2E-PURGE-*` dans `frontend/e2e/pilotage.spec.ts`) :

- [ ] Admin : nav « Observabilité » → heading visible ; KPI mockés
      (âge outbox, jobs, PDF, webhooks DEAD, SSE) via `page.route`.
- [ ] Non-admin : pas de lien nav ; pas d’écran.
- [ ] Pas de bouton d’action métier (force / relancer).
- [ ] Régression suite existante verte.
- [ ] Mock `**/api/observability**` aligné sur le contrat réel.

## Test Data

- Outbox : une ligne PENDING datée dans le passé ; base vide.
- Notification outbox : mix EMAIL/WEBHOOK × PENDING/DEAD.
- PDF : HTML minimal déjà utilisé par les tests de rendu.
- SSE : emitters mockés (`SseNotificationAdapterTest`).
- Snapshot FE : fixture JSON des cinq SLI.
- Audit : utilisateur `oidcLogin()` existant.

## Test Reporting & Coverage

```bash
./mvnw verify -Dmaven.compiler.release=21
cd frontend && npm run test
cd frontend && npm run test:e2e
```

Phase 8 : coller les sorties fraîches (counts Vitest **et**
Playwright). Jacoco : justifier tout fichier infra nouveau sous le
seuil.

## Manual Testing

- Scrape local `curl` prometheus (port management).
- UI admin : valeurs bougent après une génération PDF / webhook DEAD.
- Grep logs JSON : `tenantId` présent.
- Vérifier qu’aucun appel réseau SPA n’atteint `/actuator`.

Hors CI : Keycloak / PG réels (comme les autres lots).

## Performance Testing

Hors lot (§28). Smoke : binder n’exécute pas de full table scan
non borné (vérifier le SQL en revue design). GET snapshot = lectures
bornées, pas de N+1.

## Bug Tracking

Écarts vs §29 = bugs bloquants Phase 9. Fuite MDC = bloquant.
Écran admin sans Playwright = non-prêt Phase 8/9.
