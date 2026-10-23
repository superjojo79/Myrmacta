# §24 — Contrat d'API — surface incomplète et format d'erreur non normalisé

> **Statut :** clos · **Section :** [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) · ancien `TODO.md` §24 · [registre](README.md)

**Partiel (2026-08-13 — feature `pagination-dossiers`) :** liste/recherche/pagination
dossiers + envelope page + Supervision FE livrés. **Partiel (2026-08-15 — feature
`patch-dossier-fields`) :** `PATCH /api/dossiers/{id}/fields` (fusion, verrou optimiste,
gate statut, audit `sensitive`) livré. **Partiel (2026-08-16 — feature `elements-dossier`) :**
`POST /api/dossiers/{id}/elements` (JSON, `INFORMATION`/`NOTE`/`LINK`) et
`POST /api/dossiers/{id}/elements/attachment` (multipart, `ATTACHMENT`) livrés, avec la
même gate de statut `CLOTURE`/`ARCHIVE` que le point précédent (scopée au chemin humain).
**Partiel clos (2026-08-19 — feature `openapi-pagination-admin`) :** OpenAPI formel
(springdoc 3.1.0, golden `openapi/openapi.yaml`, fail-on-diff CI) ; pagination envelope
des 6 GET admin (types, templates, connectors, workflow-type-configs, webhooks, purge) ;
pagers SPA + écran `/admin/notification-webhooks` ; catalogues via `listAllPages`.
`ProblemDetail` déjà livré (tranches dossiers).

**Clos (2026-08-21 — feature `contrat-api-client`) :** les 6 listes non-admin restantes
migrées vers `PageResponse<T>` (`dossiers/{id}/elements`, `dossiers/{id}/workflows`,
`workflow-tasks/mine`, `connector-definitions`, `workflow-links/{linkId}/history`,
`workflow-links/{linkId}/tasks`) — mécanisme JPA `Pageable`, Flowable `listPage`/`count`
natif ou slice mémoire selon la source ; `DEFAULT_LIMIT=100` de `ListMyTasksUseCaseImpl`
supprimé au profit du contrat `page`/`size`. Les 3 endpoints `@Hidden` (SSE, multipart
attachment, webhook ingress) documentés dans le golden sans changement de comportement.
`dossiers/{id}/relations` volontairement non touché (décision `relations-dossiers`
respectée). SPA adapté via `listAllPages` existant, zéro composant Vue modifié. Codegen
client reste explicitement hors V1 (décision assumée, pas un gap).

**Énoncé :** aucun endpoint de création, recherche, liste ou mise à jour de dossier n'est documenté,
pas plus que l'administration des `DossierType`, `ConnectorInstance` et templates. Les listes
impliquent pagination, tri et filtres, non abordés. Les exceptions métier existent déjà avec des
sémantiques HTTP précises, mais sans format pivot.

**À produire :** la surface d'API complète avec schémas de requête et de réponse ; un format d'erreur
normalisé (`ProblemDetail` / RFC 7807 est le candidat naturel) et le `@RestControllerAdvice` associé ;
une décision sur la génération OpenAPI et son statut de contrat vis-à-vis du frontend.

**Énoncé :** aucun endpoint de création, recherche, liste ou mise à jour de dossier n'est documenté,
pas plus que l'administration des `DossierType`, `ConnectorInstance` et templates. Les listes
impliquent pagination, tri et filtres, non abordés. Les exceptions métier existent déjà avec des
sémantiques HTTP précises, mais sans format pivot.

**À produire :** la surface d'API complète avec schémas de requête et de réponse ; un format d'erreur
normalisé (`ProblemDetail` / RFC 7807 est le candidat naturel) et le `@RestControllerAdvice` associé ;
une décision sur la génération OpenAPI et son statut de contrat vis-à-vis du frontend.
