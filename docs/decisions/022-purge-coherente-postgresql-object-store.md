# §22 — Purge cohérente PostgreSQL / object store

> **Statut :** clos · **Section :** [Points ouverts › Autorisation et conformité](section-points-autorisation-et-conformite.md) · ancien `TODO.md` §22 · [registre](README.md)

**Implémenté (2026-08-19 — feature `purge-stockage`, PR #56) :**

| Sujet | Choix livré |
|---|---|
| Mécanisme (Q1) | Job consomme `purge_outbox` (`ProcessPurgeOutboxUseCase`) — pas XA, pas soft-delete-only |
| Port (Q2) | `DocumentStoragePort.delete` (S3 + filesystem) ; **jamais MinIO** en dur |
| Ordre (Q3) | Store puis PostgreSQL |
| Graphe (Q4) | Éléments, relations src\|tgt, workflow links, puis dossier |
| Flowable (Q5) | `WorkflowEnginePort.deleteProcessInstanceBestEffort` ; ACT_HI résiduel OK |
| UI (Q6) | Admin **lecture** `/admin/purge-outbox` ; enqueue sur détail dossier |
| HTTP (Q7) | `POST /api/dossiers/{id}/purge-requests` async ; pas de purge dans le thread HTTP |
| Tenant (Q8) | Destroy tenant = **runbook ops** ([`docs/ai/deployment/2026-08-19-feature-purge-stockage.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/deployment/2026-08-19-feature-purge-stockage.md)) |
| Orphelins (Q9) | Pas de GC store hors outbox |
| E2E (Q10) | Playwright E2E-PURGE-1..5 + suite existante |
| Enqueue (Q11) | Seulement `CLOTURE`/`ARCHIVE` (ouvert → 409) ; job ignore l’horloge si `origin=ADMIN` |

**Reste hors V1 :** recovery d’une ligne `PROCESSING` coincée après crash JVM ;
backoff `nextAttemptAt` ; GC orphelins store.

**Impacte :** [07-generation-documents.md](../specs/07-generation-documents.md),
[12-autorisation-conformite.md](../specs/12-autorisation-conformite.md).
