# §65 — Enrichissement du reporting — alerting SLA, widgets, export BI

> **Statut :** clos · **Section :** [Points ouverts › Security](section-points-security.md) · ancien `TODO.md` §65 · [registre](README.md)

**Énoncé :** les trois écarts de
[29-enrichissement-reporting.md](../specs/29-enrichissement-reporting.md)
dans un seul cycle : alerting sur les statuts SLA déjà calculés, compteurs
dead-letter et tâches sans assigné, export CSV planifié vers
`DocumentStoragePort`.

**Statut :** **clos** — [PR #132](https://github.com/superjojo79/FlowForge/pull/132) mergée sur
`main` (2026-09-22). Lot C (ClamAV) livré en parallèle, surfaces disjointes.

**Décisions Phase 1 (une par une) :** D-CYCLE-SCOPE = les trois volets ;
D-ALERT-CHANNEL = EMAIL + WEBHOOK + BROWSER via `SendNotificationUseCase` ;
D-ALERT-RECIPIENTS = e-mails et webhooks choisis par `TENANT_ADMIN`,
BROWSER vers les participants ; D-ALERT-THRESHOLD = statuts existants ;
D-ALERT-DEDUP = une alerte par franchissement et par dossier ;
D-DASHBOARD-WIDGETS = dead-letter + tâches ouvertes sans assigné ;
D-BI-MECHANISM = job storage, pas de JDBC ; D-BI-SCOPE = CSV dossiers et
CSV SLA masqués ; D-BI-DELIVERY = historique horodaté et rétention ;
D-ALERT-ACTIVATION = opt-in, snapshot sans envoi ; D-BI-ACTIVATION =
opt-in ; D-CADENCE = 1 h / 24 h / 30 jours ; D-BI-SCHEDULE = intervalle,
pas une heure civile ; D-BI-DOSSIER-SHAPE = un CSV par type, `VIEW` au
téléchargement, CSV SLA pour les rôles du rapport.

**Impacte :** `tenant_sla_settings`, jobs reporting, `/rapports`,
formulaire SLA admin, `docs/guides/GUIDE-UTILISATEUR.md`, golden OpenAPI. Pas
Compose, pas `AttachmentScanPort`, pas de variable d’environnement.

**Détail :** `docs/ai/{requirements,design,planning,testing}/2026-09-21-feature-enrichissement-reporting.md`.
