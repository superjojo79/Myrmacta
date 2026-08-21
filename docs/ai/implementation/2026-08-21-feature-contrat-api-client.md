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
