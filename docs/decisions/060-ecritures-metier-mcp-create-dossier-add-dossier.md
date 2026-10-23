# §60 — Écritures métier MCP — `create_dossier` / `add_dossier_element` / `start_workflow` / `generate_document`

> **Statut :** clos · **Section :** [Points ouverts › Security](section-points-security.md) · ancien `TODO.md` §60 · [registre](README.md)

**Énoncé :** lot **A3** ([FILE-ATTENTE.md](../../FILE-ATTENTE.md)) — le canal MCP (16 tools) définit un
flux mais ne l'opère pas. Backlog déjà cadré dans
[15-serveur-mcp.md](../specs/15-serveur-mcp.md). **Un seul cycle**, pas 4 PRs.

**Statut :** **clos** — [PR #118](https://github.com/superjojo79/FlowForge/pull/118) mergée sur
`main` (2026-09-08, `cursor/mcp-ecritures-metier-6efe`). Case cochée lors du rattrapage
documentaire du 2026-09-23 (oubli à la clôture).
Numérotation §60 : §59 déjà pris par A2 mergé ([PR #117](https://github.com/superjojo79/FlowForge/pull/117)).

**Décisions :** D-WRITE-ONE-CYCLE, D-REUSE-PORTS, D-DRY-RUN, D-ELEMENT-TYPES (NOTE/LINK/INFORMATION),
D-START-ORCH (`ManualStartOrchestrator`), D-GENERATE-ATTACH (`PRODUCED_DOC`, pas de PDF Base64),
D-GENERATE-TEMPLATE (type du dossier), D-NO-OPENAPI. `CreateDossierUseCase*` non modifié
(couture import batch Vague B).

**Correctifs sécurité (revue adversariale, 2026-09-09)** :
- **A3-M1** — `generate_document` (MCP) contournait entièrement `DocumentGenerationRateLimiter`
  (30/min) qui protège l'équivalent REST `POST /api/documents/generate` : un client M2M pouvait
  générer un volume illimité de PDF. **D-RATE-LIMIT (confirmée « A — Non » le 2026-09-08) est
  reversée** : `DocumentGenerationRateLimiter`/`DocumentGenerationRateLimitExceededException`
  rendues publiques et réutilisées depuis `GenerateDocumentTool`, même compteur que le REST
  (clé `actor.userId()`), `dryRun` non décompté.
- **A3-M2** — les 4 tools d'écriture héritaient du raccourci `API_SERVICE` = admin de facto
  (risque déjà documenté, voir §56) désormais étendu aux écritures métier. Referme partiellement
  via `M2mSecurityProperties#trustedClientIds` (voir §56) — décision **A-ACTOR toujours valable**
  par défaut (allowlist vide), le durcissement est opt-in.

**Impacte :** `infrastructure.mcp.tool`, `infrastructure.web.document` (visibilité élargie),
`infrastructure.mcp.McpAccessContextFactory`, `infrastructure.config.M2mSecurityProperties`,
`application.yml` + tests MCP + `docs/guides/GETTING-STARTED.md` §12.

**Détail :** `docs/ai/**/2026-09-08-feature-mcp-ecritures-metier.md`.
