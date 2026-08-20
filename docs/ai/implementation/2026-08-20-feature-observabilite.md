---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: complete
created: 2026-08-20
updated: 2026-08-20
feature: observabilite
phase1: 2026-08-20
phase3: 2026-08-20
phase5: 2026-08-20
phase7: 2026-08-20
phase8: 2026-08-20
phase9: 2026-08-20
requirements_ref: docs/ai/requirements/2026-08-20-feature-observabilite.md
design_ref: docs/ai/design/2026-08-20-feature-observabilite.md
planning_ref: docs/ai/planning/2026-08-20-feature-observabilite.md
---

# Implementation Guide — `observabilite`

Phase 5 livrée (M1–M5). Phases 7–9 **fermées** : alignement design,
evidence tests fraîche, revue finale sans finding bloquant. Hexagone :
Micrometer / MDC / actuator uniquement dans `infrastructure`.

## Development Setup

- Java 25 / Boot 4.1 / `./mvnw` — `spring-boot-starter-actuator` +
  `micrometer-registry-prometheus`.
- Frontend : Vue 3 `<script setup lang="ts">`, Vitest + Playwright.
  Pas de `chart.js`.
- Tests MockMvc : `MANAGEMENT_PORT=-1` (désactive le serveur management HTTP).
  IT actuator : `MANAGEMENT_PORT=0` + `RANDOM_PORT` + `HttpClient`.

## Code Structure

```
com.flowforge.infrastructure
├── observability/     # MdcScope, MdcTenantFilter, seuils, binders âge, façade
├── web/observability/ # ObservabilityController GET /api/observability
├── web/dossier/       # audit GET Q1=B + MDC dossierId
├── pdf/               # Timer flowforge.pdf.render
├── notification/      # jauge SSE + count DLQ + MDC relay
└── flowable/          # FlowableJobMetricsBinder + MDC OutboxRelayScheduler
```

SPA : `ObservabilityAdminView`, `ObservabilityKpiCard`/`Grid`,
`useObservabilitySnapshot`, `api/observability.ts`, route
`/admin/observabilite` (`meta.roles: ['TENANT_ADMIN']`).

## Journal Phase 5

| Zone | Fichiers |
|---|---|
| Actuator / ECS | `pom.xml`, `application.yml` (`management.server.port`, exposure `health,prometheus`, `logging.structured.format.console=ecs`) |
| MDC | `MdcScope`, `MdcTenantFilter` ; jobs `OutboxRelayScheduler`, `NotificationOutboxRelay`, `PurgeOutboxJob`, `EvaluateDossierRetentionJob` |
| Ports | `oldestPendingCreatedAt` / `oldestUnfinishedCreatedAt` / `countByChannelAndStatus` + Liquibase `012-observability-indexes.yaml` |
| Binders | `OutboxAgeMeterBinder`, `FlowableJobMetricsBinder`, `NotificationDlqMeterBinder`, jauge SSE, timer PDF |
| Snapshot | `ObservabilitySnapshotFacade` + `ObservabilityController` `@PreAuthorize TENANT_ADMIN` |
| Audit | `SecurityAuditLog.dossierRead` / `elementContentRead` après succès GET |
| ArchUnit | `ArchitectureRulesTest` : domaine ↛ micrometer / slf4j / actuator |
| SPA | route + AppShell + E2E-OBS-1..4 |

## Design deviations

| Id | Écart | Sévérité |
|---|---|---|
| T2.4 Flowable async MDC | Pas d’API Community 8 pour un `FlowableMdcConfigurer` ; MDC posé sur les jobs applicatifs (relais outbox, purge, rétention). Relais déclenchement pose `processInstanceId` **après** `start` / confirm. | Faible — fallback prévu au plan |
| Purge `dossierId` par item | `PurgeOutboxJob` enveloppe le **batch** avec `tenantId` seulement. `ProcessPurgeOutboxUseCaseImpl` est du domaine → MDC interdit. | Faible — même famille que fallback Flowable |
| Unexposed actuator | `beans`/`env`/`heapdump` : 302 OIDC ou 404, jamais JSON actuator. Port app `/actuator/prometheus` : SPA 200 HTML sans `flowforge_*`. | Faible — objectif « non exposé » respecté |
| Filtre MDC servlet | `MdcTenantFilter` `@Order(HIGHEST_PRECEDENCE)` auto-servlet, pas seulement « après tenant » dans la chaîne BFF. | Faible — `tenantId` présent plus tôt, pas de fuite (`finally`) |
| Test ECS stdout | `MdcTenantFilterTest` vérifie MDC pendant la chaîne, pas une ligne JSON ECS `tenantId`. | Faible — format `ecs` configuré ; smoke manuel reste |

## Implementation Notes

- Constructeur Spring 4 : `@Autowired` obligatoire dès qu’il existe un
  second constructeur package-private (`ObservabilitySnapshotFacade`).
- Binders : catch `RuntimeException` → WARN + `0`.
- `MdcScope` restaure seulement les clés posées (pas de `MDC.clear()`).
- Port management : `EndpointRequest.toAnyEndpoint()` permitAll.
- Timer PDF : `Timer.Sample` + `finally` (succès **et** exception).
- `NotificationOutboxRelay.dossierIdFromPayload` parse le JSON payload
  par index (évite d’échouer le relais si le tree Jackson n’est pas
  encore lu).

## Error Handling

Binder : catch + WARN + `0`. Relais métier inchangé.

## Performance Considerations

`MIN(created_at)` + index `(status, created_at)` sur `notification_outbox`.
Timer PDF uniquement.

## Security Notes

Port management non routé publiquement. Q1=B : ids seulement, pas de
log read si 403. Snapshot sans secrets.

## Phase 7 — revue fichier par fichier

| Zone | Verdict | Preuve |
|---|---|---|
| Hexagone : pas de Micrometer / MDC / actuator dans `domain` | MATCH | `ArchitectureRulesTest` ban `io.micrometer..`, `org.slf4j..`, `org.springframework.boot.actuate..` |
| `MdcScope` put/clear clés posées, ignore null/blank | MATCH | `MdcScope.java`, `MdcScopeTest` |
| Filtre HTTP `tenantId` + `finally` | MATCH | `MdcTenantFilter` |
| MDC jobs : relais déclenchement (tenant + dossier + PI après start) | MATCH | `OutboxRelayScheduler` + tests MDC |
| MDC jobs : relais notification (tenant + dossierId payload) | MATCH | `NotificationOutboxRelay` |
| MDC jobs : purge / rétention (tenant batch) | ÉCART BAS | `PurgeOutboxJob`, `EvaluateDossierRetentionJob` — pas de `dossierId` par item |
| Flowable async executor MDC | ÉCART BAS | Pas de `FlowableMdcConfigurer` (API Community 8) — fallback documenté |
| Ports lecture âge / counts (pas de use case observabilité) | MATCH | `oldestPendingCreatedAt`, `oldestUnfinishedCreatedAt`, `countByChannelAndStatus` |
| Index notification `(status, created_at)` | MATCH | `012-observability-indexes.yaml` |
| Noms Prometheus `flowforge.*` sans label tenant/dossierId | MATCH | binders + `ObservabilityActuatorIT` |
| Âge vide → `0` ; purge PENDING∪PROCESSING∪FAILED | MATCH | `OutboxAgeMeterBinder.ageSeconds` ; adapter purge unfinished |
| Flowable DLQ + `withException` (job + timer) | MATCH | `FlowableJobMetricsBinder` |
| Timer PDF succès et exception | MATCH | `FlyingSaucerPdfAdapter.render` `finally` |
| Jauge SSE `activeEmitterCount` | MATCH | `ObservabilityMeterConfig.SSE_ACTIVE` |
| Seuils serveur DD-THRESHOLDS | MATCH | `ObservabilityThresholds` |
| Snapshot JSON figé + `TENANT_ADMIN` 403 autres | MATCH | `ObservabilitySnapshotResponse`, `ObservabilityApiTest` |
| Actuator port management `health`+`prometheus`, permitAll Order 0 | MATCH | `application.yml`, `SecurityConfig` Order 0, IT |
| Endpoints sensibles non exposés | MATCH* | Jamais JSON actuator ; statut 302/404 (pas 404 strict) |
| Port app ne sert pas les métriques | MATCH* | Body sans `flowforge_*` (SPA peut répondre 200 HTML) |
| Audit Q1=B après succès, pas sur liste / 403 | MATCH | `DossierController`, `DossierElementContentController`, `SecurityAuditLog` |
| OpenAPI `GET /api/observability` si flag on | MATCH | `OpenApiEnabledApiTest` |
| SPA route / nav / cartes / refresh / pas d’action métier | MATCH | `routes.ts`, `AppShell`, `ObservabilityAdminView`, `ObservabilityKpiCard` `kpi-card--ok\|warn\|crit` |
| Playwright E2E-OBS-1..4 | MATCH | `frontend/e2e/pilotage.spec.ts` mock `**/api/observability**` |
| Logs ECS configurés | MATCH | `logging.structured.format.console=ecs` |

**Pas d’écart fonctionnel bloquant.** Pas de retour Phase 3/5.

### Follow-ups (non bloquants)

1. Wrapper Flowable async (`ExecuteAsyncRunnable`) si une API Community
   stable apparaît — aujourd’hui jobs applicatifs seulement.
2. `dossierId` MDC par item de purge : exigerait d’itérer les records
   côté job infra (le use case domaine ne doit pas poser le MDC).
3. IT stdout ECS : assert qu’une ligne JSON contient `tenantId`.
4. Contrôleurs workflow HTTP (`WorkflowTaskController.complete`) : PI
   connu mais pas encore poussé dans le MDC (filtre `tenantId` seulement).

## Preuve Phase 8 (session fraîche 2026-08-20)

| Commande | Exit | Résultat |
|---|---|---|
| `./mvnw verify -Dskip.frontend=true -Dmaven.compiler.release=21` | 0 | BUILD SUCCESS, **908** tests, Jacoco bundle ≥ 80 % met |
| `cd frontend && npm run test` | 0 | **135** passed (47 files) |
| `cd frontend && npm run lint` | 0 | OK |
| `cd frontend && npm run test:e2e` | 0 | **39** passed (E2E-OBS-1..4 inclus) |

Jacoco classes observabilité nouvelles : 100 % lignes (binders, façade,
filtre, seuils, controller, `OutboxRelayScheduler`). Binder webhook
DEAD : branche catch WARN couverte.

## Phase 9 — revue finale

**Checklist :** design match · pas de trou logique bloquant · sécu ·
intégration SPA · tests · docs.

### Findings

Aucun finding bloquant.

| Sévérité | Sujet | Recommandation |
|---|---|---|
| Low | Pas de `FlowableMdcConfigurer` async Community 8 | Fallback jobs applicatifs + PI après start outbox ; follow-up si API stable |
| Low | Purge MDC `tenantId` batch, pas `dossierId` par item | Use case domaine pur ; itérer côté job infra si besoin ops |
| Low | Actuator `beans`/`env`/`heapdump` 302/404 ; port app SPA 200 HTML | Objectif « pas de JSON actuator » tenu ; 404 strict non garanti avec SPA |
| Low | `MdcTenantFilter` servlet `HIGHEST_PRECEDENCE` | `tenantId` plus tôt que « après tenant BFF » ; `finally` OK |
| Info | `WorkflowTaskController.complete` ne pose pas PI dans le MDC | Filtre tenant seulement ; hors chemin critique outbox |

### Sécurité

- Port management `EndpointRequest` `permitAll` : **Q2 = A**, protection
  **réseau** (reverse-proxy public ne route pas `8081`). Residual : un
  scrape interne sans OIDC est voulu.
- `GET /api/observability` : `@PreAuthorize TENANT_ADMIN` + CSRF GET
  (méthode sûre) ; FE `meta.roles` + lien AppShell `isTenantAdmin`.
- Audit lecture : ids seulement (`dossierId` / `elementId`), pas de
  bytes / fields / payloads. Pas de log read si 403.
- Snapshot JSON : compteurs et sévérités, **aucun** secret.
- MDC : `tenantId` / `dossierId` / `processInstanceId` — pas de payload
  outbox ni HMAC.
- SSRF PDF / webhooks : inchangé (`RestrictedResourceUserAgent`,
  `OutboundUrlGuard`).
- ArchUnit : domaine ↛ micrometer / slf4j / actuator.
- Index Liquibase lecture seule (pas de table d’audit, **Q1 = B**).

### Breaking change / rollback

- Additive (deps actuator, index notification, nouvelles routes).
- Rollback = redeploy artefact précédent ; l’index peut rester.

### Intégration

SPA `/admin/observabilite` consomme uniquement `GET /api/observability`
(même origine). Aucun appel `/actuator/**` depuis le frontend.

Task tracing `npx ai-devkit@latest task` : **indisponible**.
Lint feature : docs OK (warning worktree hors nom `feature-observabilite`
car branche `cursor/observabilite-9800`).
