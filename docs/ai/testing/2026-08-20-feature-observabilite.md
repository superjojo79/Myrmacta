---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: complete
created: 2026-08-20
updated: 2026-08-20
feature: observabilite
phase1: 2026-08-20
phase2: 2026-08-20
phase3: 2026-08-20
phase8: 2026-08-20
requirements_ref: docs/ai/requirements/2026-08-20-feature-observabilite.md
design_ref: docs/ai/design/2026-08-20-feature-observabilite.md
---

# Testing Strategy — `observabilite`

> Aligné design **reviewed**. Vitest **et** Playwright **obligatoires**
> (D-FE). Contrat snapshot **figé**. Phase 8 **complete**.

## Test Coverage Goals

- Backend : Jacoco ≥ seuil projet sur le code **nouveau/modifié**
  (`./mvnw test` / `verify`).
- ArchUnit : domaine sans Micrometer / MDC / actuator.
- Frontend unit : `cd frontend && npm run test`.
- Frontend E2E : `cd frontend && npm run test:e2e`.

## Unit Tests

### MDC

- [x] Filtre HTTP : `tenantId` = `app.tenant.id` pendant la chaîne ;
      MDC vide après (y compris si le controller jette).
- [x] Job / relay : put `dossierId` / `processInstanceId` autour d’un
      item ; clear entre deux items du batch.
- [x] Requête sans dossier : pas de clé `dossierId` fantôme.
- [x] `MdcScope` ne `clear()` pas les clés d’un scope parent.

### Métriques

- [x] Âge outbox : PENDING ancien → gauge ≈ now - createdAt (secondes
      floor) ; vide → `0` (DD-EMPTY-AGE).
- [x] Purge : PROCESSING / FAILED comptent dans l’âge unfinished
      (DD-PURGE-AGE) ; COMPLETED exclus.
- [x] Timer PDF : `render` succès **et** exception observés
      (`flowforge.pdf.render`).
- [x] Jauge SSE : register + unregister met à jour le compteur.
- [x] DLQ webhooks : count seulement `WEBHOOK`+`DEAD` (EMAIL/BROWSER
      DEAD exclus).
- [x] Flowable : mock `ManagementService` — DLQ et
      `withException` (job + timer) alimentent **deux** gauges
      (DD-FLOWABLE-API).
- [x] Échec collecte binder → WARN, valeur `0`, pas d’exception
      propagée.

### Snapshot API

Contrat : `docs/ai/design/2026-08-20-feature-observabilite.md` (JSON
figé, y compris `severity` et `purgeOutboxOldestUnfinishedAgeSeconds`).

- [x] `GET /api/observability` : `TENANT_ADMIN` → 200 + trois âges +
      deux compteurs Flowable + `pdfRender` + webhook + SSE +
      `severity`.
- [x] `DOSSIER_MANAGER` / `WORKFLOW_ADMIN` / viewer → 403.
- [x] `pdfRender.count = 0` → `meanMs` / `maxMs` JSON `null`.
- [x] Corps sans secrets / payloads.
- [x] Seuils : âge 60 / 300 ; DLQ 0 / 10 ; webhook 0 / 5 ; PDF 2000 /
      5000 ; SSE 50 / 200 (table DD-THRESHOLDS).

### Audit lecture (**Q1 = B**)

- [x] GET dossier par id / par ref → `SECURITY_AUDIT`
      `event=dossier_read` (ListAppender, pattern `DossierApiTest`).
- [x] GET content élément **servi** → `element_content_read`.
- [x] GET liste paginée dossiers : **pas** d’événement read.
- [x] 403 dossier : `access_denied` seulement (pas de `dossier_read`).
- [x] Content non téléchargeable : pas de `element_content_read`.
- [x] Pas de valeur de champ / bytes dans le message.

### Actuator (**Q2 = A**)

- [x] Port management : `prometheus` contient les noms
      `flowforge_outbox_trigger_oldest_pending_age_seconds`,
      `flowforge_outbox_notification_oldest_pending_age_seconds`,
      `flowforge_outbox_purge_oldest_unfinished_age_seconds`,
      `flowforge_flowable_jobs_dead_letter`,
      `flowforge_flowable_jobs_exception`,
      `flowforge_pdf_render_seconds_count`,
      `flowforge_notification_webhook_dead`,
      `flowforge_sse_emitters_active`.
- [x] `health/liveness` et `health/readiness` → 2xx sans auth.
- [x] `beans` / `env` / `heapdump` → **non exposés** (302 OIDC ou 404 ;
      jamais JSON actuator `"contexts"`).
- [x] Port applicatif : `/actuator/prometheus` **ne sert pas** les
      métriques `flowforge_*` (SPA peut répondre 200 HTML).
- [x] Scrape management **sans** session OIDC.

### Frontend Vitest

- [x] `ObservabilityAdminView` affiche les KPI y compris trois âges
      (mock API).
- [x] État erreur ProblemDetail.
- [x] Bouton Rafraîchir re-fetch (**Q6 = A**).
- [x] `AppShell` : lien « Observabilité » si `TENANT_ADMIN` ; absent
      pour `WORKFLOW_ADMIN` et viewer.
- [x] `count = 0` PDF → n/a (pas de crash).
- [x] Classes sévérité `kpi-card--ok|warn|crit` selon `severity` JSON.
- [x] Aucun bouton retry/force.

## Integration Tests

- [x] MockMvc snapshot + MDC pendant un GET dossier.
- [x] Persistence : `MIN(created_at)` sur les **trois** outbox (H2
      MODE=PostgreSQL) ; index notification présent.
- [x] ArchUnit : `..domain..` ↛ `io.micrometer..`, `org.slf4j.MDC`,
      `org.springframework.boot.actuate..`.
- [x] OpenAPI flag on : `$.paths['/api/observability']` existe.

## End-to-End Tests

Pattern File purge (`E2E-PURGE-*` dans `frontend/e2e/pilotage.spec.ts`) :

- [x] **E2E-OBS-1** Admin : nav « Observabilité » → heading ;
      KPI mockés (trois âges, deux Flowable, PDF, webhooks DEAD, SSE)
      via `page.route('**/api/observability**')`.
- [x] **E2E-OBS-2** Non-admin : pas de lien nav ; accès direct route
      redirigé (garde `meta.roles`).
- [x] **E2E-OBS-3** Pas de bouton d’action métier.
- [x] **E2E-OBS-4** Bouton Rafraîchir déclenche un second GET.
- [x] Régression suite existante verte.

## Test Data

- Outbox : une ligne PENDING datée dans le passé ; base vide.
- Notification : mix EMAIL/WEBHOOK × PENDING/DEAD.
- Purge : PROCESSING ancien (âge unfinished).
- PDF : HTML minimal des tests de rendu.
- SSE : emitters mockés (`SseNotificationAdapterTest`).
- Snapshot FE : fixture JSON alignée sur le contrat figé.
- Audit : `oidcLogin()` existant.
- Actuator : `management.server.port=0`.

## Test Reporting & Coverage

```bash
./mvnw verify -Dmaven.compiler.release=21
cd frontend && npm run test
cd frontend && npm run test:e2e
```

Phase 8 : evidence fraîche collée ci-dessous. Jacoco : bundle ≥ 80 % ;
classes observabilité nouvelles à 100 % lignes.

Evidence Phase 8 (2026-08-20) :

- `./mvnw verify -Dmaven.compiler.release=21 -Dskip.frontend=true` → BUILD SUCCESS, Tests run **908**, Failures 0, Jacoco check met.
- `cd frontend && npm run test` → 47 files, **135** tests.
- `cd frontend && npm run lint` → OK.
- `cd frontend && npm run test:e2e` → **39** passed (E2E-OBS-1..4 inclus).
- Actuator non exposé (`beans`/`env`/`heapdump`) : 302 OIDC ou 404, jamais JSON actuator.
- Port app `/actuator/prometheus` : body sans `flowforge_*`.

## Manual Testing

- `curl` prometheus sur le port management (sans cookie).
- UI admin : valeurs après génération PDF / webhook DEAD.
- Grep logs ECS : `tenantId`.
- DevTools : aucun appel SPA vers `/actuator`.

## Performance Testing

Hors lot (§28). Smoke revue : SQL `MIN` indexé, pas de full scan.

## Bug Tracking

Écarts vs §29 = bloquants Phase 9. Fuite MDC = bloquant. SPA sans
Playwright = non-prêt Phase 8/9.
