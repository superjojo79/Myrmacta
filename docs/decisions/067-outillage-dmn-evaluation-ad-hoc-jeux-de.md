# §67 — Outillage DMN — évaluation ad hoc, jeux de tests, historique/rollback

> **Statut :** clos · **Section :** [Points ouverts › Security](section-points-security.md) · ancien `TODO.md` §67 · [registre](README.md)

**Énoncé :** les trois écarts de
[28-outillage-dmn.md](../specs/28-outillage-dmn.md) /
`ROADMAP.md` **1.1** + **1.2** + **1.3/5.1** dans un seul cycle :
`evaluate_decision` (MCP + REST + bouton Tester), jeu de tests JSON,
historique des versions Flowable + redéploiement d’une version
antérieure (libellé « Redéployer la version N »).

**Résolu (2026-09-24) :** Phases 1 à 9 livrées, mergées sur `main`
([PR #134](https://github.com/superjojo79/FlowForge/pull/134), commit
`97b429a`). Surfaces disjointes de §65, de l'inbox §66 et du lot C.

**Décisions Phase 1 :** D-DMN-EVAL-SURFACE = C ; D-DMN-EVAL-VERSION = B ;
D-DMN-TEST-FORMAT = C ; D-DMN-ROLLBACK-UX = A ;
D-DMN-HISTORY-RETENTION = A.

**Décision Phase 2 :** D-DMN-TEST-SURFACE = C.

**Décisions Phase 3 :** port d’évaluation dédié ; résultat toujours en
liste ; version via `parentDeploymentId` ; match de tests ordonné ;
CSV `input.`/`expected.` hors fichier, coercion des littéraux ; 100 cas ;
409 si redéploiement de la courante ; allowlist rejouée ; tools
`evaluate_decision`, `test_decision`, `list_decision_table_versions`,
`redeploy_decision_table` ; REST unique `POST /test` ; Tester en JSON ;
`TenantAdminGuard` sur les quatre nouveaux use cases.

**Impacte :** `DmnDeploymentPort`, `DmnEvaluationPort`,
`/admin/dmn-editor`, 4 tools MCP, golden OpenAPI, `docs/guides/GUIDE-UTILISATEUR.md`.
Pas de table historique applicative, pas de variable d’environnement.

**Détail :** `docs/ai/{requirements,design,planning,testing}/2026-09-22-feature-outillage-dmn.md`.
