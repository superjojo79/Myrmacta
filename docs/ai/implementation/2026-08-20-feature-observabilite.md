---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: draft
created: 2026-08-20
updated: 2026-08-20
feature: observabilite
phase1: 2026-08-20
phase3: 2026-08-20
phase5: 2026-08-20
requirements_ref: docs/ai/requirements/2026-08-20-feature-observabilite.md
design_ref: docs/ai/design/2026-08-20-feature-observabilite.md
---

# Implementation Guide — `observabilite`

Phase 5 livrée (M1–M5). Hexagone : Micrometer / MDC / actuator uniquement
dans `infrastructure`.

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
| T2.4 Flowable async MDC | Pas d’API Community 8 pour un `FlowableMdcConfigurer` ; MDC posé sur les jobs applicatifs (relais outbox, purge, rétention). | Faible — noté, fallback prévu au plan |
| Unexposed actuator | `beans`/`env`/`heapdump` : 302 OIDC ou 404, jamais JSON actuator. | Faible — objectif « non exposé » respecté |

## Implementation Notes

- Constructeur Spring 4 : `@Autowired` obligatoire dès qu’il existe un
  second constructeur package-private (`ObservabilitySnapshotFacade`).
- Binders : catch `RuntimeException` → WARN + `0`.
- `MdcScope` restaure seulement les clés posées (pas de `MDC.clear()`).
- Port management : `EndpointRequest.toAnyEndpoint()` permitAll.

## Error Handling

Binder : catch + WARN + `0`. Relais métier inchangé.

## Performance Considerations

`MIN(created_at)` + index `(status, created_at)` sur `notification_outbox`.
Timer PDF uniquement.

## Security Notes

Port management non routé publiquement. Q1=B : ids seulement, pas de
log read si 403. Snapshot sans secrets.
