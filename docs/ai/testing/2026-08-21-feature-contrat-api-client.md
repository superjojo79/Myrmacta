---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: complete
created: 2026-08-21
updated: 2026-08-21
feature: contrat-api-client
phase4: 2026-08-21
phase8: 2026-08-21
requirements_ref: docs/ai/requirements/2026-08-21-feature-contrat-api-client.md
design_ref: docs/ai/design/2026-08-21-feature-contrat-api-client.md
---

# Testing Strategy — `contrat-api-client`

> Scénarios rédigés en Phase 4 (planning), cochés au fil de
> l'implémentation TDD (Phase 5), validés par une relecture Jacoco réelle
> en Phase 8 — sweep exact `grep -o 'id="L[0-9]*" title="[^"]*missed[^"]*"'`
> par fichier, chaque ligne signalée relue et corrélée au diff (pas de
> coverage sweep superficiel — cf. leçon retenue sur
> `durcissement-donnees-sensibles`).

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

- [x] Toutes les constantes existantes conservées, tous les appelants
      existants recompilent (`ListSortsTest`, 2/2).
- [x] 6 nouvelles constantes ajoutées avec la valeur attendue par
      endpoint.

### `JpaDossierElementRepositoryAdapter.pageByDossierId`

- [x] Page 0 taille N sur un dossier avec > N éléments → contenu trié
      `addedAt,asc`, `totalElements` correct
      (`DossierElementPersistenceTest`, 4/4).
- [x] Dossier sans élément → page vide, `totalElements = 0`.

### `JpaDossierWorkflowLinkRepositoryAdapter.pageByDossierId`

- [x] Page 0 taille N sur un dossier avec > N liens workflow → contenu
      trié `sequence,asc`, `totalElements` correct
      (`WorkflowPersistenceTest`, 7/7).

### `FlowableWorkflowTaskInboxAdapter.findActiveAssignedTo` (nouvelle signature `PageQuery`)

- [x] `listPage(offset, size)` + `.count()` sur une requête fraîche
      utilisés (pas `.list()` complet) — vérifié explicitement par mock
      (`FlowableWorkflowTaskInboxAdapterTest`, 2/2, assertion
      `verify(countQuery, never()).listPage(...)`).
- [x] Mapping des champs tâche (nom `null` → chaîne vide, etc.).

### `FlowableWorkflowVisualizationAdapter.getHistory` / `.listActiveTasks` (nouvelle signature `PageQuery`)

- [x] `listPage`/`count` natifs utilisés pour les deux méthodes —
      vérifié explicitement par mock (`FlowableWorkflowVisualizationAdapterTest`,
      7/7, 2 nouveaux scénarios).
- [x] Non-régression `getDiagram`/`reassignTask`/`assertTaskOnInstance`
      (signature inchangée) — 5 tests existants toujours verts.

### `ListDossierElementsUseCaseImpl` / `AttachWorkflowUseCaseImpl.getHistory` / `ListMyTasksUseCaseImpl` / `WorkflowLinkVisualizationUseCaseImpl`

- [x] Chaque use case délègue correctement au port paginé et retourne
      `PageResult<T>` (`ListDossierElementsUseCaseImplTest` 2/2,
      `AttachWorkflowUseCaseImplTest` 5/5, `ListMyTasksUseCaseImplTest`
      1/1, `WorkflowLinkVisualizationUseCaseImplTest` 18/18 dont 2
      nouveaux pour getHistory/getTasks).
- [x] Contrôles d'accès existants inchangés — non-régression confirmée
      (tests `getDiagram_denies*`/`allows*` toujours verts, même chemin
      de code que `getHistory`/`getTasks` via `requireReadableLink`).

### `ConnectorDefinitionController.list` (slice mémoire)

- [x] `catalog.listAll()` trié par `type` avant `PageResult.slice` —
      ordre déterministe vérifié (`ConnectorAdminApiTest`, nouveau
      matcher `isSortedByTypeAscending`).
- [x] `size` respecté, `sort` non supporté → 400
      (`ConnectorAdminApiTest`, 10/10).

## Integration Tests

- [x] `GET /api/dossiers/{id}/elements?page=&size=&sort=` : défauts,
      `PageResponse` bien formé, `sort` invalide → 400
      (`DossierApiTest`, 71/71, nouveau test `listElements_pageAndSizeRespected`).
- [x] `GET /api/workflow-tasks/mine?page=&size=` : `PageResponse` bien
      formé, plus de `DEFAULT_LIMIT` tronquant silencieusement
      (`WorkflowApiTest`, nouvelle assertion dans le scénario reassign
      existant — acteur avec tâche assignée voit `content[0]`/`totalElements`).
- [x] `GET /api/connector-definitions?page=&size=` : `@PreAuthorize
      hasRole('TENANT_ADMIN')` inchangé, page/size respectés
      (`ConnectorAdminApiTest`, 10/10).
- [x] `GET /api/dossiers/{id}/workflows?page=&size=` : `PageResponse`
      bien formé (`WorkflowApiTest`, 9/9).
- [x] `GET /api/workflow-links/{linkId}/history?page=&size=` et
      `.../tasks?page=&size=` : `PageResponse` bien formé
      (`WorkflowApiTest`, 9/9, 3 assertions JSON adaptées).
- [x] `GET /api/dossiers/{id}/relations` : **aucun changement** — non
      touché par le diff (D-RELATIONS respecté), pas de régression
      possible sur un code non modifié.
- [x] OpenAPI golden (`OpenApiContractIT`) : régénéré
      (`-Dopenapi.update=true`), 6 endpoints paginés + 3 opérations
      `@Hidden` retirées confirmés présents dans `openapi/openapi.yaml`
      par lecture directe ; run normal (sans régénération) vert —
      **déterminisme vérifié sur 2 régénérations consécutives
      identiques** après correction (voir Trouvailles Phase 8).
- [x] `GET /api/notifications/stream`, `POST /api/dossiers/{id}/elements/attachment`,
      `POST /api/webhooks/{instanceId}` : comportement runtime inchangé —
      `OpenApiEnabledApiTest` mis à jour (les 3 chemins existent
      désormais dans `/v3/api-docs`, alors qu'il vérifiait auparavant
      leur absence).

## End-to-End Tests

- [x] Suite Playwright existante (`frontend/e2e/pilotage.spec.ts`) —
      39/39 verts après correction de 2 régressions réelles trouvées par
      cette exécution complète (voir Trouvailles Phase 8).
- [x] Régression complète du projet exécutée : `npm run test` (136/136),
      `npm run typecheck`, `npm run lint`, `npm run test:e2e` (39/39).

## Test Data

Fixtures existantes réutilisées ; aucun nouveau jeu de données partagé
nécessaire (les scénarios de pagination utilisent des jeux de 2-3
éléments suffisants pour prouver tri + `totalElements`, pas de test de
volume `> 100` distinct — la preuve de non-troncature repose sur la
lecture de code des adaptateurs Flowable, cf. tests adaptateur ci-dessus).

## Test Reporting & Coverage

- `./mvnw -Dmaven.compiler.release=21 verify` → **BUILD SUCCESS**, Tests
  run **986**, Failures **0**, Jacoco check met.
- Sweep par classe (13 fichiers substantiellement modifiés) via
  `grep -o 'id="L[0-9]*" title="[^"]*missed[^"]*"'` sur les rapports
  HTML Jacoco : chaque ligne signalée relue et corrélée au diff. Résultat :
  **100% des lignes/branches ajoutées ou modifiées par cette feature sont
  couvertes.** Les branches encore signalées « missed » (`DossierController`,
  `DossierWorkflowController`, `WorkflowTaskController`,
  `WorkflowLinkVisualizationController`, `AttachWorkflowUseCaseImpl`,
  `ListMyTasksUseCaseImpl`, `WorkflowLinkVisualizationUseCaseImpl`,
  `FlowableWorkflowTaskInboxAdapter`, `FlowableWorkflowVisualizationAdapter`)
  appartiennent toutes à des méthodes **non touchées** par cette feature
  (`addElement`, `reassign`, `complete`, `reserve`, `abortReserved`,
  `getDiagram`, `assertTaskOnInstance`, etc.) — vérifié ligne par ligne en
  comparant avec la version pré-feature (`git show`) : contenu identique,
  gap déjà présent avant `contrat-api-client`, hors périmètre.
- `ConnectorDefinitionController`, `ListDossierElementsUseCaseImpl`,
  `JpaDossierElementRepositoryAdapter`, `JpaDossierWorkflowLinkRepositoryAdapter` :
  **0 branche manquante**.
- Frontend : `npm run test -- --coverage` non exécuté séparément (le run
  standard 136/136 couvre les wrappers modifiés ; pas de régression de
  couverture attendue sur des fonctions dont seule l'implémentation
  interne change, signature publique inchangée).

## Manual Testing

Aucune UI nouvelle (D-SPA-ADAPT) — pas de checklist manuelle dédiée.

## Performance Testing

Pas de test de charge formel. Preuve par lecture de code + tests
adaptateur dédiés (assertions `verify(..., never()).listPage(...)` sur
la requête `count`) que `listPage`/`count` Flowable remplacent bien
`.list()` complet.

## Bug Tracking

Process standard du projet.

## Trouvailles Phase 8 (régressions réelles trouvées et corrigées)

Conformément à la demande explicite de faire tourner *tous* les tests du
projet (pas seulement les tests unitaires backend), l'exécution complète
de la suite Playwright a révélé **2 régressions réelles** que les tests
unitaires/d'intégration backend et Vitest n'auraient jamais détectées :

1. **Mocks Playwright non enveloppés.** `frontend/e2e/pilotage.spec.ts`
   simulait les 6 endpoints migrés avec des tableaux JSON bruts
   (`'[]'`, `JSON.stringify(tasks)`, etc.) au lieu de l'enveloppe
   `PageResponse`. Avec `listAllPages` côté SPA, un mock non enveloppé
   produit un tableau vide silencieux (`content` absent → page vide →
   boucle stoppée), sans erreur réseau visible. **Corrigé** : les 6
   points de mock utilisent désormais `envelope(...)` (helper déjà
   présent dans le fichier, utilisé par les endpoints admin déjà
   paginés).
2. **Pattern glob Playwright ne matchant plus l'URL.** `page.route('**/api/workflow-tasks/mine', ...)` et `page.route('**/api/connector-definitions', ...)`
   ne se terminaient pas par un caractère joker — avec `?page=0&size=100`
   désormais toujours présent dans l'URL réelle, ces routes ne
   matchaient plus du tout et la requête tombait dans le vide.
   **Corrigé** : `'**/api/workflow-tasks/mine*'` et
   `'**/api/connector-definitions*'`.

Ces deux corrections ont été validées par re-exécution complète de la
suite (39/39 verts) après chaque correction, pas seulement par
inspection du diff.

3. **Non-déterminisme du golden OpenAPI.** Le retrait de `@Hidden` sur
   `WebhookController` exposait `@RequestHeader HttpHeaders headers`
   dans le golden — la classe Spring `HttpHeaders` a un ordre de
   propriétés généré par réflexion **non stable** d'une régénération à
   l'autre (confirmé par diff de deux exécutions consécutives de
   `OpenApiContractIT -Dopenapi.update=true`, qui auraient rendu la CI
   flaky sans lien avec un changement réel de contrat). **Corrigé** :
   `@Parameter(hidden = true)` sur ce seul paramètre (le schema du corps
   de requête reste documenté) ; déterminisme reconfirmé sur 2
   régénérations consécutives identiques après correction.
4. **`OpenApiEnabledApiTest` obsolète.** Le test
   `adminCanReadDocs_managerIsForbidden_hiddenOperationsAbsent`
   affirmait explicitement l'absence des 3 chemins désormais documentés
   — sans cette correction, `./mvnw verify` aurait échoué sur un test
   qui contredisait directement l'objectif de la feature (D-HIDDEN-SPEC).
   **Corrigé** : le test (renommé
   `..._previouslyHiddenOperationsNowDocumented`) vérifie désormais leur
   présence.
