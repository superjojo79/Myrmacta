---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: reviewed
created: 2026-08-20
updated: 2026-08-20
feature: observabilite
phase1: 2026-08-20
phase3: 2026-08-20
phase4: 2026-08-20
phase5: 2026-08-20
requirements_ref: docs/ai/requirements/2026-08-20-feature-observabilite.md
design_ref: docs/ai/design/2026-08-20-feature-observabilite.md
testing_ref: docs/ai/testing/2026-08-20-feature-observabilite.md
---

# Project Planning & Task Breakdown — `observabilite`

> Phase 4 **reviewed**. Phase 5 **done** (T1.1–T5.2). Design figé.

## Milestones

- [x] **M1** — Actuator port management + logs ECS + MDC `tenantId`
- [x] **M2** — Ports lecture + binders (3 âges, Flowable ×2, PDF, webhook DEAD, SSE) + MDC jobs
- [x] **M3** — Q1 audit lecture `SECURITY_AUDIT`
- [x] **M4** — `GET /api/observability` + SPA `/admin/observabilite`
- [x] **M5** — ArchUnit + OpenAPI + `./mvnw verify` + Vitest + Playwright

## Task Breakdown

TDD : test rouge → code → vert. Validation : commandes ci-dessous.

### M1 — Socle runtime

#### T1.1 Dépendances + config actuator / ECS
- **Outcome :** `spring-boot-starter-actuator` + `micrometer-registry-prometheus` ;
  `application.yml` (`management.server.port`, exposure `health,prometheus`,
  probes, `logging.structured.format.console=ecs`). Tests :
  `MANAGEMENT_PORT=-1` (mock) ; IT dédié `MANAGEMENT_PORT=0`.
- **Deps :** aucune.
- **Tests :** T1.3, T5.2.
- **Status :** done

#### T1.2 `MdcScope` (infra)
- **Outcome :** `AutoCloseable` put/clear **uniquement** des clés posées ;
  ignore valeurs null ; pas de `MDC.clear()` global.
- **Deps :** aucune.
- **Tests :** `MdcScopeTest` (parent scope préservé ; null non posé).
- **Status :** done

#### T1.3 Filtre HTTP `tenantId` + chaîne management
- **Outcome :** `MdcTenantFilter` (`tenantId` = `TenantProperties`) en
  `finally` ; `SecurityFilterChain` `@Order(0)` `EndpointRequest` permitAll ;
  filtre MDC dans la chaîne BFF (après tenant).
- **Deps :** T1.1, T1.2.
- **Tests :** `MdcTenantFilterTest` (tenant présent pendant la chaîne,
  vide après, y compris exception) ; `ObservabilityActuatorIT` (prometheus
  sans OIDC sur port management ; 404 prometheus sur port app ;
  health liveness/readiness ; beans/env 404).
- **Status :** done

### M2 — Métriques

#### T2.1 Méthodes ports + adapters + index
- **Outcome :**
  - `OutboxRepository.oldestPendingCreatedAt()`
  - `NotificationOutboxRepository.oldestPendingCreatedAt()` +
    `countByChannelAndStatus`
  - `PurgeOutboxRepository.oldestUnfinishedCreatedAt()`
    (PENDING∪PROCESSING∪FAILED)
  - Liquibase `012-observability-indexes.yaml`
- **Deps :** aucune (domaine pur).
- **Tests :** étendre `WorkflowPersistenceTest`,
  `NotificationPersistenceIT`, `PurgeGraphPersistenceIT` /
  `RetentionSchemaIT` (index) ; vide → empty Optional ; count WEBHOOK+DEAD.
- **Status :** done

#### T2.2 `ObservabilityThresholds` + binders âge
- **Outcome :** constantes DD-THRESHOLDS ; `OutboxAgeMeterBinder` trois
  gauges `flowforge.outbox.*.age` (secondes floor, vide=0) ; Clock
  injectable ; exception → WARN + 0.
- **Deps :** T2.1, T1.1.
- **Tests :** `ObservabilityThresholdsTest` ; `OutboxAgeMeterBinderTest`
  (SimpleMeterRegistry + mocks ports).
- **Status :** done

#### T2.3 Flowable jobs + webhook DLQ + SSE + PDF timer
- **Outcome :**
  - `FlowableJobMetricsBinder` (DLQ + withException job/timer)
  - `NotificationDlqMeterBinder`
  - `SseNotificationAdapter.activeEmitterCount()` + gauge
  - Timer `flowforge.pdf.render` dans `FlyingSaucerPdfAdapter`
    (succès et exception)
  - `ObservabilityMeterConfig` enregistre les binders
- **Deps :** T1.1, T2.1.
- **Tests :** `FlowableJobMetricsBinderTest` (mock ManagementService) ;
  `NotificationDlqMeterBinderTest` ; `SseNotificationAdapterTest` count
  register/unregister ; `FlyingSaucerPdfAdapterTest` timer count (succès +
  HTML invalide / exception).
- **Status :** done

#### T2.4 MDC jobs
- **Outcome :** `MdcScope` autour de `OutboxRelayScheduler.processOne`
  (`dossierId`) ; `NotificationOutboxRelay.processOne` (tenant + dossierId
  JSON si présent) ; `PurgeOutboxJob` (tenantId batch) ;
  `FlowableMdcConfigurer` si API Flowable 8 dispo, sinon tenantId jobs
  applicatifs seulement (écart noté implementation).
- **Deps :** T1.2.
- **Tests :** `OutboxRelaySchedulerTest` / `NotificationOutboxRelayTest`
  — MDC vide après item (spy MDC via test dédié `JobMdcTest` si plus
  simple).
- **Status :** done

### M3 — Audit lecture

#### T3.1 `SECURITY_AUDIT` GET succès
- **Outcome :** `SecurityAuditLog.dossierRead` /
  `elementContentRead` ; appels **après** succès dans
  `DossierController` GET id/`ref` et
  `DossierElementContentController` après retrieve.
- **Deps :** T1.2 (MDC dossierId sur GET id).
- **Tests :** `DossierApiTest` (id + ref → `dossier_read` ; liste paginée
  sans read ; 403 sans `dossier_read`) ; `DossierElementContentApiTest`
  (content servi → `element_content_read` ; non downloadable sans event) ;
  `SecurityAuditLogTest` (pas de throw, message sans bytes).
- **Status :** done

### M4 — Snapshot + SPA

#### T4.1 `GET /api/observability`
- **Outcome :** `ObservabilitySnapshotFacade` +
  `ObservabilityController` `@PreAuthorize("hasRole('TENANT_ADMIN')")` ;
  JSON figé design (severity serveur ; pdf mean/max null si count=0).
- **Deps :** T2.2, T2.3, T1.3.
- **Tests :** `ObservabilityApiTest` (admin 200 + champs ; manager /
  workflow admin / viewer 403 ; pas de payload secret) ;
  `ObservabilitySnapshotFacadeTest` (seuils, pdf n/a).
- **Status :** done

#### T4.2 OpenAPI
- **Outcome :** chemin documenté via springdoc ; assertion
  `OpenApiEnabledApiTest` `$.paths['/api/observability']`.
- **Deps :** T4.1.
- **Tests :** `OpenApiEnabledApiTest`.
- **Status :** done

#### T4.3 SPA admin
- **Outcome :** `api/observability.ts`, `useObservabilitySnapshot`,
  `ObservabilityKpiCard` / `Grid`, `ObservabilityAdminView`, route
  `meta.roles: ['TENANT_ADMIN']`, lien AppShell « Observabilité »,
  bouton Rafraîchir, `data-testid` design, pas de bouton métier.
- **Deps :** T4.1 (contrat).
- **Tests Vitest :** `observability.spec.ts` (client) ; vue KPI + n/a
  PDF ; refresh re-fetch ; AppShell admin vs workflow vs viewer ;
  `apiCoverage.spec.ts`.
- **Tests Playwright :** E2E-OBS-1..4 dans `pilotage.spec.ts` (mock
  `**/api/observability**`).
- **Status :** done

### M5 — Qualité

#### T5.1 ArchUnit
- **Outcome :** `..domain..` ↛ `io.micrometer..`, `org.slf4j.MDC`,
  `org.springframework.boot.actuate..`.
- **Deps :** tout le code domaine.
- **Tests :** `ArchitectureRulesTest`.
- **Status :** done

#### T5.2 Verify
- **Outcome :** `./mvnw verify -Dmaven.compiler.release=21` ;
  `cd frontend && npm run test` ; `cd frontend && npm run test:e2e`.
- **Deps :** M1–M4, T5.1.
- **Tests :** evidence Phase 5/8.
- **Status :** done

## Mapping tests ↔ tâches

| Scénario testing | Tâches |
|---|---|
| MDC HTTP / jobs / pas de fantôme | T1.2, T1.3, T2.4 |
| Âge outbox vide=0 / unfinished purge | T2.1, T2.2 |
| Timer PDF succès+exception | T2.3 |
| SSE register/unregister | T2.3 |
| WEBHOOK+DEAD only | T2.1, T2.3 |
| Flowable deux gauges | T2.3 |
| Snapshot 200/403 + trois âges + severity | T4.1 |
| Audit Q1=B | T3.1 |
| Actuator Q2=A | T1.1, T1.3 |
| ArchUnit | T5.1 |
| Vitest vue/nav/refresh | T4.3 |
| E2E-OBS-* | T4.3 |
| OpenAPI path | T4.2 |

Tous les scénarios testing ont une tâche.

## Dependencies (ordre)

T1.1 → T1.2 → T1.3 → T2.* → T3.1 → T4.1 → T4.2 / T4.3 → T5.1 → T5.2.

T2.1 peut commencer en parallèle de T1.2.

## Timeline & Estimates

Pas d’estimation calendaire. Lot unique Phase 5 : toutes les tâches.

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| Port management vs MockMvc | `MANAGEMENT_PORT=-1` tests mock ; IT RANDOM_PORT |
| API Flowable async MDC | Fallback jobs applicatifs ; noter écart |
| Jacoco 80 % nouveaux fichiers | tests binders/façade/filtre/seuils |
| SSE count package-private unregister | `activeEmitterCount()` public |

## Resources Needed

Design reviewed, testing, ports JPA existants, `SecurityAuditLog`,
File purge SPA comme patron UI/E2E.

## Progress (Phase 5)

Toutes les tâches T1.1–T5.2 **done** (2026-08-20).

- Backend : `./mvnw verify -Dmaven.compiler.release=21 -Dskip.frontend=true` → **BUILD SUCCESS**, Tests run **906**, Failures **0**, Jacoco check met.
- Frontend : `npm run test` → 47 files / 135 tests ; `npm run test:e2e` → 39 passed (dont E2E-OBS-1..4).
- Écart noté : pas de `FlowableMdcConfigurer` async (API Community 8) — MDC jobs applicatifs seulement (`implementation.md`).
