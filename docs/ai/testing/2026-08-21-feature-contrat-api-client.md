---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: draft
created: 2026-08-21
updated: 2026-08-21
feature: contrat-api-client
phase4: 2026-08-21
requirements_ref: docs/ai/requirements/2026-08-21-feature-contrat-api-client.md
design_ref: docs/ai/design/2026-08-21-feature-contrat-api-client.md
---

# Testing Strategy — `contrat-api-client`

> Scénarios rédigés en Phase 4 (planning), à cocher au fil de
> l'implémentation TDD (Phase 5) puis validés par une relecture Jacoco
> réelle en Phase 8 (pas de coverage sweep superficiel — cf. leçon retenue
> sur `durcissement-donnees-sensibles`).

## Test Coverage Goals

- Backend : Jacoco ≥ seuil projet sur le code **nouveau/modifié**
  (`./mvnw verify -Dmaven.compiler.release=21`), sweep par classe (pas
  seulement au niveau bundle).
- ArchUnit : `ListSorts` reste dans `infrastructure.web` ; aucune fuite de
  `Pageable`/`Page` Spring Data ni de types Flowable au-delà de
  `infrastructure` — couvert par les règles ArchUnit existantes.
- OpenAPI golden (`openapi/openapi.yaml`) : diff CI doit inclure les 6
  endpoints migrés vers `PageResponse` et les 3 endpoints désormais
  documentés (SSE, multipart, webhook).
- Frontend : `npm run test` (Vitest) et `npm run test:e2e` (Playwright,
  `PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/pw-browsers/chromium`) verts sans
  régression — aucun composant Vue modifié (D-SPA-ADAPT), seuls les
  wrappers `api/*.ts` et leurs specs changent.

## Unit Tests

### `ListSorts` (renommage `AdminListSorts`)

- [ ] Toutes les constantes existantes (`DOSSIER_TYPES`, `DOCUMENT_TEMPLATES`,
      `CONNECTORS`, `WORKFLOW_TYPE_CONFIGS`, `NOTIFICATION_WEBHOOKS`,
      `PURGE_OUTBOX`) conservées, tous les appelants existants recompilent
      (pas de test dédié — vérifié par compilation + `ArchitectureRulesTest`).
- [ ] 6 nouvelles constantes ajoutées (`DOSSIER_ELEMENTS`,
      `MY_WORKFLOW_TASKS`, `CONNECTOR_DEFINITIONS`, `DOSSIER_WORKFLOWS`,
      `WORKFLOW_LINK_HISTORY`, `WORKFLOW_LINK_TASKS`) avec la valeur
      attendue par endpoint (cf. design, tableau API Design).

### `JpaDossierElementRepositoryAdapter.pageByDossierId`

- [ ] Page 0 taille N sur un dossier avec > N éléments → contenu trié
      `addedAt,asc`, `totalElements` correct.
- [ ] Page hors bornes (au-delà du dernier élément) → contenu vide,
      `totalElements` correct (pas d'exception).
- [ ] Dossier sans élément → page vide, `totalElements = 0`.

### `JpaDossierWorkflowLinkRepositoryAdapter.pageByDossierId`

- [ ] Page 0 taille N sur un dossier avec > N liens workflow → contenu
      trié `sequence,asc`, `totalElements` correct.
- [ ] Dossier sans lien workflow → page vide.

### `FlowableWorkflowTaskInboxAdapter.findActiveAssignedTo` (nouvelle signature `PageQuery`)

- [ ] Assignee avec > `size` tâches actives → `listPage(offset, size)`
      utilisé (pas `.list()` complet), `totalElements` = `count()` réel,
      pas plafonné à l'ancien `DEFAULT_LIMIT = 100`.
- [ ] Assignee sans tâche → page vide.
- [ ] Tri déterministe (`createdAt,asc`) vérifié sur un jeu de tâches
      créées à des instants différents.

### `FlowableWorkflowVisualizationAdapter.getHistory` / `.listActiveTasks` (nouvelle signature `PageQuery`)

- [ ] `getHistory` : `listPage`/`count` natifs utilisés, tri
      `startedAt,asc` préservé (comportement existant avant migration).
- [ ] `listActiveTasks` : `listPage`/`count` natifs utilisés, tri
      `createdAt,asc`.
- [ ] Process instance introuvable → `WorkflowProcessInstanceNotFoundException`
      inchangée (non-régression, comportement déjà testé, à revérifier
      après changement de signature).

### `ListDossierElementsUseCaseImpl` / `AttachWorkflowUseCaseImpl.getHistory` / `ListMyTasksUseCaseImpl` / `WorkflowLinkVisualizationUseCaseImpl`

- [ ] Chaque use case délègue correctement au port paginé et retourne
      `PageResult<T>` (pas de re-slicing applicatif redondant).
- [ ] Contrôles d'accès existants inchangés (`DossierAccessPolicy`,
      `requireReadableLink`) — non-régression après changement de
      signature.

### `ConnectorDefinitionController.list` (slice mémoire)

- [ ] `catalog.listAll()` trié par `type` avant `PageResult.slice` —
      ordre déterministe même si `ConnectorRegistry` retourne un ordre
      d'insertion différent.
- [ ] Page au-delà du nombre de connecteurs enregistrés → page vide,
      `totalElements` = nombre réel de connecteurs.

## Integration Tests

- [ ] `GET /api/dossiers/{id}/elements?page=&size=&sort=` : défauts
      (`page=0`, `size=20`), `PageResponse` bien formé, `sort` invalide →
      400.
- [ ] `GET /api/workflow-tasks/mine?page=&size=` : plus de 100 tâches
      actives pour un acteur → toutes accessibles via pagination (preuve
      que `DEFAULT_LIMIT` ne tronque plus silencieusement).
- [ ] `GET /api/connector-definitions?page=&size=` : `@PreAuthorize
      hasRole('TENANT_ADMIN')` inchangé, page/size respectés.
- [ ] `GET /api/dossiers/{id}/workflows?page=&size=` : `PageResponse`
      bien formé.
- [ ] `GET /api/workflow-links/{linkId}/history?page=&size=` et
      `.../tasks?page=&size=` : `PageResponse` bien formé, 404 si link
      inaccessible (non-régression `requireReadableLink`).
- [ ] `GET /api/dossiers/{id}/relations` : **aucun changement** —
      test de non-régression explicite confirmant l'absence de
      `page`/`size`/`PageResponse` sur cet endpoint (D-RELATIONS respecté).
- [ ] OpenAPI golden (`OpenApiContractIT`) : régénération
      (`-Dopenapi.update=true`) puis run normal vert, diff incluant les 6
      endpoints paginés + 3 endpoints `@Hidden` retirés.
- [ ] `GET /api/notifications/stream`, `POST /api/dossiers/{id}/elements/attachment`,
      `POST /api/webhooks/{instanceId}` : comportement runtime inchangé
      (retrait `@Hidden` = documentation seule, pas de test fonctionnel
      nouveau requis au-delà des tests existants).

## End-to-End Tests

- [ ] Suite Playwright existante (`frontend/e2e/**`) verte sans
      modification de scénario — aucun flux utilisateur ne change de
      forme visible (D-SPA-ADAPT : `listAllPages` ré-agrège côté client).
- [ ] Régression complète du projet demandée (comme pour
      `durcissement-donnees-sensibles`) : `npm run test` + `npm run
      test:e2e` sur l'ensemble du SPA, pas seulement les vues touchées.

## Test Data

- Fixtures existantes réutilisées (dossiers/éléments/tâches de test déjà
  présents dans les tests d'intégration actuels des 6 endpoints).
- Pour les scénarios "> 100 tâches"/"> size éléments" : génération de
  données de test dans le test lui-même (boucle de création), pas de
  nouveau jeu de fixtures partagé.

## Test Reporting & Coverage

- Backend : `./mvnw verify -Dmaven.compiler.release=21`, Jacoco
  `target/site/jacoco/jacoco.csv` + rapport HTML par classe (sweep exact
  `grep -o 'id="L[0-9]*" title="[^"]*missed[^"]*"'`, pas de correspondance
  de classe CSS approximative).
- Frontend : `npm run test -- --coverage`.
- Coverage gaps : à documenter en Phase 8 avec justification explicite si
  une branche reste non couverte (pattern déjà appliqué sur la feature
  précédente).

## Manual Testing

- Aucune UI nouvelle (D-SPA-ADAPT) — pas de checklist manuelle dédiée.

## Performance Testing

- Pas de test de charge formel. Preuve par lecture de code que
  `listPage`/`count` Flowable remplacent bien `.list()` complet (évite un
  full scan pour `workflow-tasks/mine` et l'historique workflow) —
  documentée dans l'implementation doc en Phase 5/7.

## Bug Tracking

- Process standard du projet (issue GitHub si régression détectée en CI
  ou en revue).
