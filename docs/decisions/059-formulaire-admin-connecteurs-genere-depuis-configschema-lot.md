# §59 — Formulaire admin connecteurs généré depuis `configSchema` (lot A2)

> **Statut :** clos · **Section :** [Points ouverts › Security](section-points-security.md) · ancien `TODO.md` §59 · [registre](README.md)

**Énoncé :** `GET /api/connector-definitions` expose déjà `configSchema` ; l’écran
Admin → Connecteurs ignore le schéma et impose un `<textarea>` JSON
(`ConnectorsAdminView.vue`). Lot **A2** de [FILE-ATTENTE.md](../../FILE-ATTENTE.md) :
feature `admin-connector-form-schema`, frontend seulement.

**Décisions (cycle 2026-09-08, agent cloud) :** D-REPLACE-TEXTAREA, D-EMPTY-SCHEMA,
D-FALLBACK-NO-DEFS, D-SCOPE-CREATE, D-NO-DYNAMICFIELDS, D-COERCE, D-PASSWORD,
D-CLIENT-REQUIRED, D-SWITCH-TYPE, D-NO-BACKEND, **D-WEBHOOK-URL = C** (rejeu Phase 1–3)
— détail dans
[`docs/ai/requirements/2026-09-08-feature-admin-connector-form-schema.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-08-feature-admin-connector-form-schema.md).

**Impacte :** `frontend/src/views/admin/ConnectorsAdminView.vue`,
`frontend/src/api/admin.ts`, `frontend/src/components/connectors/**`, e2e connecteurs,
[docs/guides/GUIDE-UTILISATEUR.md](../guides/GUIDE-UTILISATEUR.md) §4, [06-connecteurs.md](../specs/06-connecteurs.md)
§ écran d’admin.

**Statut :** cycle `dev-lifecycle` Phases 1–9 **clos** — [PR #117](https://github.com/superjojo79/FlowForge/pull/117).
Vitest 324/324 ; Playwright 68/68 (`E2E-CONN-1` webhook, `E2E-CONN-2` smtp, `E2E-CONN-3` callback).
**Mergé sur `main`** (2026-09-08).
