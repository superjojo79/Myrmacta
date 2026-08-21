---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: complete
created: 2026-08-21
updated: 2026-08-21
feature: contrat-api-client
phase5: 2026-08-21
phase7: 2026-08-21
requirements_ref: docs/ai/requirements/2026-08-21-feature-contrat-api-client.md
design_ref: docs/ai/design/2026-08-21-feature-contrat-api-client.md
planning_ref: docs/ai/planning/2026-08-21-feature-contrat-api-client.md
---

# Implementation Guide — `contrat-api-client`

## Development Setup

Aucun prérequis nouveau. `./mvnw -Dmaven.compiler.release=21 verify` (backend),
`npm run test` / `npm run typecheck` / `npm run lint` / `npm run test:e2e`
(frontend, `PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/pw-browsers/chromium` dans
cet environnement).

## Code Structure

- `infrastructure/web/ListSorts.java` — renommage de `AdminListSorts`,
  12 constantes (6 existantes + 6 nouvelles pour cette feature).
- Ports out modifiés (ajout d'une méthode paginée, ancienne méthode `List<>`
  conservée quand réutilisée ailleurs) : `DossierElementRepository`,
  `DossierWorkflowLinkRepository`, `WorkflowTaskInboxPort` (signature
  remplacée, un seul appelant), `WorkflowVisualizationPort` (signature
  remplacée, un seul appelant).
- Adaptateurs JPA (`infrastructure/persistence/**`) : pattern
  `SpringPages.of(query)` / `SpringPages.map(page, mapper)`, méthode
  Spring Data surchargée `find...(id, Pageable)` à côté de la version
  `find...(id)` existante.
- Adaptateurs Flowable (`infrastructure/flowable/**`) : `listPage(offset,
  size)` + `.count()` sur une query fraîche (jamais réutiliser un objet
  query Flowable entre `.count()` et `.listPage()`).
- `ConnectorDefinitionController` : slice mémoire, tri explicite
  (`Comparator.comparing(ConnectorDefinition::getType)`) avant
  `PageResult.slice`.

## Implementation Notes

### Core Features

- **Pagination JPA (elements, dossiers/{id}/workflows) :** réutilise
  intégralement le pattern `openapi-pagination-admin` (`SpringPages`),
  aucune nouveauté technique.
- **Pagination Flowable native (workflow-tasks/mine, workflow-links
  history+tasks) :** remplace un `.list()`/`.listPage(0, limite fixe)`
  par `.listPage(offset, size)` + `.count()` sur une requête séparée.
  Piège identifié et évité : appeler `.count()` puis `.listPage()` sur le
  **même** objet `TaskQuery`/`HistoricActivityInstanceQuery` — toujours
  reconstruire une requête fraîche par opération terminale.
- **Pagination mémoire (connector-definitions) :** `ConnectorRegistry`
  reste un simple accès mémoire (`LinkedHashMap`, ordre d'insertion) ; le
  tri déterministe (`type,asc`) et le découpage (`PageResult.slice`) sont
  faits au niveau du contrôleur, pas du port.
- **Suppression de `ListMyTasksUseCaseImpl.DEFAULT_LIMIT`** (D-TASK-LIMIT) :
  le plafond dur est remplacé par le contrat `page`/`size` ; chaque page
  reste bornée à `PageQuery.MAX_SIZE = 100` côté serveur.
- **3 endpoints `@Hidden` documentés** sans changement de comportement
  runtime (retrait de l'annotation uniquement).

### Patterns & Best Practices

- TDD strict sur toute nouvelle méthode de port/adaptateur : rouge
  confirmé par échec de compilation ou de test avant chaque
  implémentation.
- Breaking changes de signature (ports in/out) traités atomiquement :
  tous les appelants en repo mis à jour dans le même commit logique,
  jamais de surcharge de compatibilité temporaire.

## Integration Points

- **Frontend :** `frontend/src/api/{dossiers,tasks,workflows,admin}.ts`
  ré-agrègent les pages via `listAllPages` (`frontend/src/api/page.ts`,
  déjà utilisé pour `workflow-type-configs`) — signature `Promise<T[]>`
  inchangée pour les stores/composants consommateurs, zéro changement de
  composant Vue.

## Error Handling

Aucun changement. Les contrôleurs migrés réutilisent
`PageQueryParser.parse(...)` (lève `IllegalArgumentException` → 400 via
le `@RestControllerAdvice` existant si `sort` ne correspond pas à
l'allow-list de la ressource).

## Performance Considerations

Les deux adaptateurs Flowable évitent désormais un chargement complet en
mémoire (`.list()`) pour l'historique d'activité et les tâches actives —
`.listPage`/`.count()` natifs réduisent la charge sur
`ACT_HI_ACTINST`/`ACT_RU_TASK` pour les processus/utilisateurs à fort
volume.

## Security Notes

Aucun changement d'autorisation sur les 6 endpoints migrés
(`DossierAccessPolicy`, `@PreAuthorize`, `requireReadableLink` inchangés) —
la pagination ne modifie que le découpage des données déjà autorisées,
jamais leur périmètre.

## Trouvaille Phase 7 (check implementation)

La relecture fichier par fichier a révélé une incohérence tri
déclaré/tri réel introduite pendant l'implémentation, invisible aux
tests fonctionnels existants (aucun ne vérifiait l'ordre exact) :

- **`workflow-tasks/mine` :** `ListSorts.MY_WORKFLOW_TASKS` déclarait
  `"createdAt,asc"` alors que `FlowableWorkflowTaskInboxAdapter` trie en
  `.desc()` (comportement pré-existant, volontairement préservé — le
  design visait « tri par défaut = valeur actuelle implicite »).
  **Corrigé** : constante alignée sur `"createdAt,desc"` (aucun
  changement de comportement runtime, seule la déclaration était fausse).
- **`workflow-links/{linkId}/tasks` :** `listActiveTasks` n'appliquait
  **aucun** `ORDER BY` explicite avant `.listPage(offset, size)` — un
  vrai bug de pagination (ordre non garanti stable entre deux pages
  consécutives côté moteur, risque de doublons/omissions). **Corrigé** :
  ajout de `.orderByTaskCreateTime().asc()`, aligné sur
  `ListSorts.WORKFLOW_LINK_TASKS = "createdAt,asc"` déjà déclaré.

Aucun autre écart trouvé sur les 4 autres mappings tri déclaré/tri réel
(`DOSSIER_ELEMENTS`, `DOSSIER_WORKFLOWS`, `CONNECTOR_DEFINITIONS`,
`WORKFLOW_LINK_HISTORY`) — vérifiés correspondre exactement à la requête
JPA/Flowable réellement exécutée.

## Écarts vs design

- **D-HIDDEN-ANNOTATIONS (webhook ingress) :** le design envisageait un
  schema `object` libre pour le payload. Le handler consomme en réalité
  `@RequestBody byte[]` (pas de parsing JSON) — springdoc génère
  nativement `type: string, format: byte`, plus fidèle au contrat réel
  que forcer un schema `object`. Documenté en Phase 4 (T5.1) avant
  implémentation, pas une dérive découverte après coup.
- **Non-déterminisme du golden OpenAPI (trouvaille en Phase 8) :**
  exposer `@RequestHeader HttpHeaders headers` sur `WebhookController`
  (nécessaire pour lire `Idempotency-Key`) tirait dans le golden le
  schema complet de `org.springframework.http.HttpHeaders` — classe
  Spring dont l'ordre de propriétés généré par réflexion n'est **pas**
  stable d'une exécution à l'autre (confirmé par diff de deux
  régénérations consécutives). Corrigé par
  `@Parameter(hidden = true)` sur ce paramètre précis (le schema du
  corps de la requête, lui, reste documenté) — aucun changement de
  comportement runtime, uniquement une exclusion de la doc pour un type
  Spring interne non pertinent au contrat.

## Addendum D-SPA-ADAPT — pagination UI ajoutée après coup (2026-08-21)

La Phase 3 (design) avait auto-décidé D-SPA-ADAPT (pas de pagination
visible, `listAllPages` silencieux côté SPA) sans poser la question à
l'utilisateur — écart signalé par l'utilisateur, décision rejouée
explicitement (cf. design doc, addendum). Verdict : pagination visible
demandée pour 5 des 6 listes ; `connector-definitions` reste
`listAllPages` (c'est un menu déroulant de types, pas une liste
parcourue).

**Backend :** aucun changement (les endpoints exposaient déjà
`page`/`size`/`sort`).

**Frontend — fichiers modifiés :**

- `api/tasks.ts`, `api/dossiers.ts` (`listDossierElements`,
  `listDossierWorkflows`), `api/workflows.ts` (`getWorkflowHistory`,
  `getWorkflowTasks`) : reviennent sur `listAllPages`, retournent
  désormais `Page<T>` brut avec `params: {page, size}`.
  `api/admin.ts` (`listConnectorDefinitions`) **inchangé**.
- `stores/tasks.ts` : `refresh(page, size)` (au lieu de `refresh()`),
  `totalPages` exposé.
- `stores/dossier.ts` : état de pagination **par section**
  (`elementsPage`/`elementsTotalPages`, `workflowsPage`/`...TotalPages`,
  `historyPage`/`...TotalPages`, `tasksPage`/`...TotalPages`) + méthodes
  `loadElementsPage`/`loadWorkflowsPage`/`loadHistoryPage`/`loadTasksPage`.
  Nouveau `currentLinkId` interne pour permettre de re-paginer
  history/tasks sans redemander `linkId` à l'appelant.
- `components/admin/AdminPager.vue` **réutilisé tel quel** (déjà
  générique malgré son nom — aucun changement).
- `components/dossier/ElementsList.vue`, `components/workflow/HistoryTimeline.vue`,
  `components/workflow/TaskList.vue` : ajout `page`/`totalPages` props +
  `defineModel('page')`, rendent `<AdminPager>` si `totalPages > 1`.
- `components/dossier/WorkflowLinksList.vue` **(nouveau)** : la liste
  `dossiers/{id}/workflows` était chargée dans le store mais **jamais
  affichée** dans `DossierDetailView.vue` avant cet addendum (constat
  fait en creusant le scope, pas anticipé) — nouveau composant
  d'affichage + pager, créé avec l'accord explicite de l'utilisateur qui
  élargit légèrement le périmètre au-delà du contrat API pur.
- `views/pilotage/MobileTasksView.vue` : pagination **synchronisée à
  l'URL** via `useAdminListQuery` (même pattern que les vues admin) —
  seule vue dédiée parmi les 5, donc pas de risque de collision entre
  plusieurs pagers sur la même page.
- `views/pilotage/DossierDetailView.vue` : câble les 4 sections
  embarquées (elements, workflows, history, tasks) avec un état **local,
  non synchronisé à l'URL** (`:page`/`@update:page` explicites vers les
  méthodes du store, pas de `v-model` générique) — 4 pagers sur la même
  page rendent le pattern `useAdminListQuery` (URL globale) inutilisable
  ici.

**Pourquoi `:page`/`@update:page` explicites plutôt que `v-model`
générique dans `DossierDetailView` :** un `watch` générique sur un ref
lié en v-model aurait pu se redéclencher quand le store remet la page à
0 en interne (changement de dossier), provoquant un double fetch inutile.
Câbler `@update:page` directement vers `store.loadXxxPage` élimine ce
risque : seul un clic utilisateur sur le pager déclenche un rechargement.

**Tests :** TDD strict (rouge confirmé avant chaque implémentation) sur
tous les nouveaux comportements — `tasks store` (+1), `dossier store`
(+2), `MobileTasksView` (+1), `ElementsList` (+2), `HistoryTimeline`
(nouveau fichier, 4 tests), `TaskList` (nouveau fichier, 4 tests),
`WorkflowLinksList` (nouveau fichier, 4 tests), `DossierDetailView`
(9/9 existants toujours verts après adaptation des mocks). `npm run
test` 153/153, `typecheck` et `lint` propres. `npm run test:e2e` 40/40
(39 existants inchangés + 1 nouveau `E2E-PAGE-2` prouvant que le pager
de la page Tâches fonctionne réellement dans un navigateur : clic →
URL `?page=1` → contenu de la page 2 affiché).

**Non testé en e2e :** les 4 pagers embarqués dans `DossierDetailView`
(elements/workflows/history/tasks) n'ont pas de scénario e2e dédié
prouvant le clic-pager de bout en bout, contrairement à `workflow-tasks/mine`
(`E2E-PAGE-2`) — les fixtures e2e existantes ont toutes 1-3 éléments par
section, donc `totalPages` reste à 1 et aucun pager ne s'affiche dans
les scénarios actuels. Couvert unitairement (Vitest) mais pas en
navigateur réel — écart mineur assumé, à combler si un scénario e2e
avec volume réel devient nécessaire.
