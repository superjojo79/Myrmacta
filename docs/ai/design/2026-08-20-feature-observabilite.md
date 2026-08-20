---
phase: design
title: System Design & Architecture
description: Define the technical architecture, components, and data models
status: draft
created: 2026-08-20
updated: 2026-08-20
feature: observabilite
phase1: 2026-08-20
requirements_ref: docs/ai/requirements/2026-08-20-feature-observabilite.md
todo_ref: TODO.md §29
---

# System Design & Architecture — `observabilite`

> Draft Phase 1 (esquisse). **D-FE acté** : écran admin. Revue design =
> Phase 3 après requirements `reviewed`. Q1–Q6 encore ouvertes.

## Architecture Overview

```mermaid
flowchart LR
  http[HTTP request] --> mdcFilter[MdcTenantFilter]
  mdcFilter --> ctrl[Controllers]
  ctrl -->|put dossierId / processInstanceId| mdc[MDC]
  jobs[Relays / Flowable / purge] --> mdc
  mdc --> jsonLogs[JSON stdout]
  binders[MeterBinders infra]
  binders --> registry[MeterRegistry]
  pdf[FlyingSaucerPdfAdapter] -->|Timer| registry
  sse[SseNotificationAdapter] -->|Gauge| registry
  flowable[Flowable ManagementService] -->|failed / DLQ| registry
  outbox[(workflow outbox)] -->|oldest pending age| binders
  notifDlq[(notification_outbox WEBHOOK DEAD)] --> binders
  registry --> prom["/actuator/prometheus"]
  scrape[Prometheus ops] --> prom
  spa[Admin SPA /admin/observabilite] --> snapApi["GET /api/observability"]
  snapApi --> registry
```

- **Couche :** tout dans `com.flowforge.infrastructure` (observability,
  web filters, adapters existants). **Aucun** type Micrometer / MDC /
  actuator dans `com.flowforge.domain`.
- **Stack :** Spring Boot 4.1 actuator + Micrometer Prometheus ;
  logs structurés Boot (`logging.structured.format.console`).
- **Tenant :** `TenantProperties.id()` — jamais résolu par requête.

## Data Models

Pas de nouvelle table métier **sauf si Q1 = C** (écarté en reco).

### MDC

| Clé | Source | Cycle de vie |
|---|---|---|
| `tenantId` | `app.tenant.id` | Filtre HTTP + wrapper jobs ; clear `finally` |
| `dossierId` | path / use case / payload outbox | Put au plus près du contexte dossier ; clear |
| `processInstanceId` | lien workflow / exécution Flowable | Idem |

### Métriques (noms provisoires — figer Phase 3)

| SLI §29 | Type | Source |
|---|---|---|
| Âge plus ancien PENDING outbox déclenchement | Gauge (secondes) | Nouvelle lecture `OutboxRepository` |
| Jobs Flowable en échec | Gauge (count) | `ManagementService` dead-letter (+ failed si distinct) |
| Durée rendu PDF | Timer | `PdfRenderer` adapter |
| Webhooks DLQ | Gauge (count) | Count `WEBHOOK`+`DEAD` |
| `SseEmitter` actifs | Gauge (count) | Somme des sets dans `SseNotificationAdapter` |

### Audit lecture (si Q1 = B)

Événement `SECURITY_AUDIT` INFO, même logger que les 403 :

- `event=dossier_read` — `GET /api/dossiers/{id}` et `?ref=`
- `event=element_content_read` — `GET /api/dossier-elements/{id}/content`

Champs : `principal`, `path`, `dossierId` / `elementId`. **Jamais** le
contenu ni les `fields`.

## API Design

### Actuator (D-ACTUATOR, Q2 reco A)

- Exposer uniquement `health`, `prometheus`.
- Probes `liveness` / `readiness` Boot.
- **Port management** distinct (`management.server.port`) : scrape
  Prometheus **sans** OIDC ; le reverse-proxy public **ne route pas**
  ce port.
- `GET /api/**` métier : **+** snapshot JSON pour le SPA (D-API-UI).
  OpenAPI : inclure ce GET si flag on (discipline §24).

### Snapshot SPA (D-FE, D-API-UI)

`GET /api/observability` — `@PreAuthorize("hasRole('TENANT_ADMIN')")`
(Q5 reco A). Même origine, CSRF session BFF. **Pas** de texte
Prometheus.

Forme provisoire (figer Phase 3) :

```json
{
  "tenantId": "tenant_dev",
  "observedAt": "2026-08-20T07:41:00Z",
  "triggerOutboxOldestPendingAgeSeconds": 12.5,
  "flowableFailedJobCount": 0,
  "pdfRender": { "count": 10, "meanMs": 80.0, "maxMs": 200.0 },
  "webhookDeadCount": 2,
  "sseActiveCount": 3
}
```

Lecture seule. 403 si rôle insuffisant.

## Component Breakdown

| Composant | Responsabilité |
|---|---|
| `MdcTenantFilter` (web) | Put/clear `tenantId` ; clear all en finally |
| Helpers MDC dossier/PI | Try/finally autour des controllers / relays concernés |
| `OutboxAgeMeterBinder` | Gauge âge PENDING déclenchement |
| `FlowableJobMetrics` | Gauges failed / DLQ |
| `NotificationDlqMeterBinder` | Gauge WEBHOOK DEAD |
| Timer PDF | Micrometer autour de `render` |
| Jauge SSE | `activeEmitterCount()` sur l’adapter existant |
| `ObservabilityController` | GET snapshot — mappe registry / ports → JSON |
| `SecurityAuditLog` | + méthodes read si Q1 = B |
| `SecurityConfig` | Ne pas authentifier le port management comme le BFF |

### Frontend (D-FE) — carte composants

| Pièce | Responsabilité |
|---|---|
| Route `/admin/observabilite` | `meta.roles: ['TENANT_ADMIN']` (Q5 reco A) |
| `AppShell` | Lien Admin « Observabilité » si `isTenantAdmin` |
| `ObservabilityAdminView` | Surface de composition : chargement, erreur, refresh |
| `ObservabilityKpiGrid` | Grille des cinq SLI |
| `ObservabilityKpiCard` | Une métrique + sévérité visuelle (Q4 reco A) |
| `api/observability.ts` | Client GET snapshot |
| `useObservabilitySnapshot` | Fetch / état (pas de store Pinia global sauf besoin) |

Pas de `chart.js` si Q4 = A. PrimeVue déjà dans le projet (Tag /
Message / Card — choix exact Phase 3, aligné maquette `11`).

## Design Decisions

| Décision | Choix draft | Alternative écartée |
|---|---|---|
| Où vivent les métriques | Infrastructure binders | Annotation domaine / AOP use case |
| Âge outbox | Port lecture + binder | SQL natif dans le binder sans port |
| Flowable | Adapter infra seulement | Port domaine « JobMetrics » (YAGNI) |
| Logs | Structured logging Boot natif | logstash-logback-encoder additionnel |
| Q1 | Reco B logs | Table d’audit (C) trop large pour §29 |
| D-FE | Écran admin snapshot | SPA scrape Prometheus (casse Q2 / CORS / auth) |
| D-API-UI | JSON BFF | Exposer actuator sur le port app pour le SPA |

## Non-Functional Requirements

- **Perf :** MDC put/clear = négligeable. Binders : requêtes COUNT /
  MIN(`created_at`) bornées, intervalle d’échantillonnage Micrometer
  par défaut. Timer PDF : une observation / rendu.
- **Sécurité :** pas de secrets dans les labels/logs ; prometheus
  non public (Q2). SSRF / PJ hors lot.
- **Fiabilité :** échec d’une collecte de jauge = log WARN, pas de
  crash du relais métier.
- **Scalabilité :** une JVM / tenant (décision n° 13) — jauge SSE
  locale = correcte V1.

## Points à figer en Phase 3

- Noms Prometheus définitifs + labels (`tenant` redondant avec
  instance : **éviter** un label haute cardinalité).
- Exact failed vs dead-letter Flowable 8 API.
- Format JSON `ecs` vs `logstash`.
- Comportement jauge outbox vide (0 vs NaN).
- Seuils ok/warn/crit des cartes (alignés runbook monitoring).
- Path exact `GET /api/observability` vs `/api/admin/observability`.
