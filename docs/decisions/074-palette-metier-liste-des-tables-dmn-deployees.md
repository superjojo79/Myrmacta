# §74 — Palette métier — liste des tables DMN déployées (LC1-suite) (livré)

> **Statut :** clos · **Section :** [Points ouverts › Autorisation et conformité](section-points-autorisation-et-conformite.md) · ancien `TODO.md` §74 · [registre](README.md)

**Énoncé :** reliquat de §73 (« reste en suite : liste des tables DMN déployées »). Docs SDD :
[requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-30-feature-lc1-suite-palette-dmn.md), [design](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/design/2026-09-30-feature-lc1-suite-palette-dmn.md),
[planning](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/planning/2026-09-30-feature-lc1-suite-palette-dmn.md), [implementation](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-09-30-feature-lc1-suite-palette-dmn.md),
[testing](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/testing/2026-09-30-feature-lc1-suite-palette-dmn.md). Journal des décisions : [DECISIONS.md](roadmap-2026/DECISIONS.md).

**Décisions (mandat « roadmap complète », 2026-09-30).** D-LC1S-SOURCE = endpoint de références de la palette (pas de nouvelle API REST publique) ;
D-LC1S-LABEL = valeur = clé, libellé = « clé (vN) » ; D-LC1S-EXISTS = `exists("dmnTables")` ajouté à l'adaptateur (l'existence reste vérifiée au
déploiement par le use case). **Livré** : `GET /api/bpmn-building-blocks/references/dmnTables` (TENANT_ADMIN, WORKFLOW_ADMIN), frontend, `E2E-PALETTE-8`.
**Revue adversariale** (sous-agent) : 0 Critique/Haut ; 1 Moyenne (doc obsolète), 3 Basses (2 corrigées, 1 tracée).
**Impacte :** `docs/guides/GUIDE-UTILISATEUR.md` (palette), `ROADMAP.md`, `CHANGELOG.md`. Aucune variable d'environnement, aucune migration.
