# §79 — Tâches planifiées sûres sous double exécution (D5) (livré)

> **Statut :** clos · **Section :** [Points ouverts › Autorisation et conformité](section-points-autorisation-et-conformite.md) · ancien `TODO.md` §79 · [registre](README.md)

**Énoncé :** lot **D5**, analyse [36-verrou-jobs-planifies.md](../specs/36-verrou-jobs-planifies.md). Docs SDD : [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-10-01-feature-d5-verrou-jobs-planifies.md),
[design (audit complet)](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/design/2026-10-01-feature-d5-verrou-jobs-planifies.md), [implementation](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-10-01-feature-d5-verrou-jobs-planifies.md),
[testing](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/testing/2026-10-01-feature-d5-verrou-jobs-planifies.md).

**Décisions.** D-JOBS-TRIGGER = corriger dès l'audit (un déploiement progressif fait coexister deux pods, l'audit montre sept tâches fautives dont deux en corruption) ; D-JOBS-MECHANISM = prise par ligne
(UPDATE conditionnel), ni advisory lock ni ShedLock ; D-JOBS-LEASE = baux courts (2 à 5 min) et reprise des unités abandonnées ; D-JOBS-CRON = créneau cron au plus une fois sous crash, rendu en cas d'échec.

**Livré.** 7 tâches rendues sûres (relais outbox notifications et workflow, alertes SLA, invitations portail, déclencheurs cron, export BI, réconciliation `RESERVED`) + distribution des signatures ; migrations additives 027 à 029 ;
`docs/guides/DEPLOIEMENT.md` §11 (ce qui est sûr, ce qui ne l'est pas : SSE, rate-limit, Kafka). **Impacte :** `docs/guides/DEPLOIEMENT.md`, `docs/specs/36`, `ROADMAP.md`, `CHANGELOG.md`. **Revue adversariale** : voir le document d'implémentation.
