---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: draft
created: 2026-08-20
updated: 2026-08-20
feature: observabilite
phase1: 2026-08-20
---

# Implementation Guide — `observabilite`

> Squelette Phase 1. **Remplir en Phase 5/7** après le plan. Rappel
> TDD : test qui échoue → code. Hexagone non négociable.

## Development Setup

- Java 25 / Boot 4.1 / `./mvnw` — ajouter actuator + registry
  Prometheus **en Phase 5**, pas avant design reviewed.
- Frontend : Vue 3 `<script setup lang="ts">`, Vitest + Playwright
  (D-FE). Pas de `chart.js` (**Q4 = A**).

## Code Structure (cible)

```
com.flowforge.infrastructure
├── observability/     # binders, config actuator/MDC
├── web/               # filtre MDC ; audit GET Q1=B
├── pdf/               # timer autour de render
├── notification/      # jauge SSE + count DLQ
└── flowable/          # métriques jobs
```

Domaine : au plus une méthode lecture âge sur `OutboxRepository`.

SPA : `frontend/src/views/admin/ObservabilityAdminView.vue`,
`frontend/src/components/admin/observability/`,
`frontend/src/api/observability.ts`, route + `AppShell`.

## Implementation Notes

Voir design draft. Interdit : `@Timed` / `MDC` dans `domain`.

## Integration Points

- `TenantProperties`, `OutboxRelayScheduler`,
  `NotificationOutboxRelay`, `SseNotificationAdapter`,
  `FlyingSaucerPdfAdapter`, `SecurityAuditLog`, `SecurityConfig`,
  GET snapshot + SPA admin.

## Error Handling

Binder : catch + WARN. Relais métier inchangé.

## Performance Considerations

Pas de scan unbounded. Timer PDF uniquement.

## Security Notes

Port management non routé publiquement (Q2). Pas de secrets dans
MDC/labels. Q1=B : ids seulement.
