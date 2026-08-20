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
requirements_ref: docs/ai/requirements/2026-08-20-feature-observabilite.md
design_ref: docs/ai/design/2026-08-20-feature-observabilite.md
---

# Implementation Guide — `observabilite`

> Pointeur vers le design **reviewed**. **Remplir le journal en
> Phase 5/7**. Rappel TDD. Hexagone non négociable. Ne pas coder
> avant le plan Phase 4.

## Development Setup

- Java 25 / Boot 4.1 / `./mvnw` — actuator +
  `micrometer-registry-prometheus` **en Phase 5**.
- Frontend : Vue 3 `<script setup lang="ts">`, Vitest + Playwright.
  Pas de `chart.js`.

## Code Structure (cible, design reviewed)

```
com.flowforge.infrastructure
├── observability/     # MdcScope, binders âge, façade snapshot, seuils
├── web/observability/ # ObservabilityController
├── web/dossier/       # audit GET Q1=B + MDC dossierId
├── pdf/               # timer autour de render
├── notification/      # jauge SSE + count DLQ + MDC relay
└── flowable/          # métriques jobs + MDC async
```

Domaine : méthodes lecture seulement sur les trois ports outbox
(+ `countByChannelAndStatus`). **Zéro** MDC / Micrometer.

SPA :

- `frontend/src/views/admin/ObservabilityAdminView.vue`
- `frontend/src/components/admin/observability/`
- `frontend/src/api/observability.ts`
- `frontend/src/composables/useObservabilitySnapshot.ts`
- route + `AppShell`

Noms Prometheus, JSON snapshot, seuils : **design reviewed** (ne pas
renommer en cours de route).

## Implementation Notes

Interdit : `@Timed` / `MDC` dans `domain`. Binder : catch + WARN.

## Integration Points

`TenantProperties`, `OutboxRelayScheduler`, `NotificationOutboxRelay`,
`PurgeOutboxJob`, `SseNotificationAdapter`, `FlyingSaucerPdfAdapter`,
`SecurityAuditLog`, `SecurityConfig`, `ArchitectureRulesTest`,
`OpenApiEnabledApiTest`.

## Error Handling

Binder : catch + WARN + `0`. Relais métier inchangé.

## Performance Considerations

`MIN(created_at)` indexé. Timer PDF uniquement.

## Security Notes

Port management non routé publiquement. Q1=B : ids seulement.
