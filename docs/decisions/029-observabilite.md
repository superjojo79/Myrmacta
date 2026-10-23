# §29 — Observabilité

> **Statut :** clos · **Section :** [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) · ancien `TODO.md` §29 · [registre](README.md)

**Livré (2026-08-20 — feature `observabilite`, PR #59, Phase 9 READY) :**
logs ECS + MDC (`tenantId` / `dossierId` / `processInstanceId`) ;
Micrometer `flowforge.*` (3 âges outbox, Flowable DLQ+exception, timer
PDF, webhooks DEAD, SSE) sur le **port management** ; audit lecture
`SECURITY_AUDIT` Q1=B ; `GET /api/observability` + SPA
`/admin/observabilite` (`TENANT_ADMIN`). Voir
[`docs/ai/implementation/2026-08-20-feature-observabilite.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-08-20-feature-observabilite.md).

Follow-ups non bloquants : wrapper MDC async Flowable Community 8 ;
`dossierId` MDC par item de purge.

Périmètre figé : logs ECS + MDC ; Micrometer (`flowforge.*`) ; écran
admin `/admin/observabilite`. Détail : requirements + design.
