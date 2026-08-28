---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
---

# Implementation Guide

## Development Setup
**How do we get started?**

Aucune dépendance nouvelle, aucune migration, aucune variable d'environnement. Backend :
`./mvnw verify -Dmaven.compiler.release=21`. Frontend : `cd frontend && npm run test` (Vitest),
`npm run test:e2e` (Playwright, nécessite `PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/pw-browsers/chromium`
dans ce bac à sable), `npm run typecheck`, `npm run lint`.

## Code Structure
**How is the code organized?**

Fichiers modifiés/ajoutés (8 milestones du plan) :

| Fichier | Nature |
|---|---|
| `domain/dossier/model/DossierType.java` | Modifié — `requireUniqueFieldNames` (D-NAME-UNIQUE) |
| `domain/dossier/usecase/UpdateDossierTypeUseCaseImpl.java` | Modifié — injecte `CheckDossierTypeSchemaCompatibilityUseCase`, bloque avant `save` |
| `infrastructure/config/DossierConfig.java` | Modifié — nouveau paramètre du bean `updateDossierTypeUseCase` |
| `infrastructure/web/dossier/DossierExceptionHandler.java` | Modifié — nouveau `@ExceptionHandler(DossierTypeSchemaIncompatibleException.class)` → 409 |
| `infrastructure/mcp/tool/DefineDossierTypeTool.java` | Modifié — `defineUpdate()` simplifié (check explicite retiré du chemin réel, gardé pour `dryRun`) |
| `frontend/src/dossier/validateFieldDefinitions.ts` | **Nouveau** — validation de forme (miroir client de `DossierFieldValidator` + unicité `name`) |
| `frontend/src/components/dossier/FieldDefinitionDefsEditor.vue` | **Nouveau** — éditeur de liste `FieldDefinition[]`, réutilisé création + édition |
| `frontend/src/views/admin/DossierTypesAdminView.vue` | Modifié — formulaire de création + panneau d'édition inline |
| `GUIDE-UTILISATEUR.md` §1 | Modifié — limite retirée, nouvel écran documenté |

Tests ajoutés/modifiés : `DossierTypeTest`, `UpdateDossierTypeUseCaseImplTest`,
`DossierExceptionHandlerTest`, `DefineDossierTypeToolTest` (2 tests réécrits), `DossierApiTest`
(9 nouveaux cas), `validateFieldDefinitions.spec.ts` (nouveau), `FieldDefinitionDefsEditor.spec.ts`
(nouveau), `DossierTypesAdminView.spec.ts` (7 nouveaux cas), `frontend/e2e/pilotage.spec.ts`
(4 nouveaux `E2E-SCHEMA-*`).

## Implementation Notes
**Key technical details to remember:**

### Core Features

- **D-NAME-UNIQUE** — `DossierType` compact constructor rejette toute liste `fieldDefinitions`
  portant un `name` dupliqué (`IllegalArgumentException`, mappée 400 par `handleBadRequest`
  générique). Couvre les 3 chemins d'écriture (REST create, REST update, MCP) sans code
  supplémentaire, puisqu'ils construisent tous un `DossierType`.
- **D-UI-SCHEMA-COMPAT / D-DESIGN-CHECK-PLACEMENT** — `UpdateDossierTypeUseCaseImpl.update()`
  invoque désormais `CheckDossierTypeSchemaCompatibilityUseCase` (déjà écrit, déjà testé côté MCP)
  avant `repository.save`, et lève `DossierTypeSchemaIncompatibleException` (déjà existante) si le
  nouveau schéma casse des dossiers existants. `DefineDossierTypeTool` (MCP) simplifié en
  conséquence : le chemin d'écriture réel ne fait plus son propre appel au check (redondant),
  seul le chemin `dryRun` le garde (il ne doit jamais persister).
- **Écran admin** — `FieldDefinitionDefsEditor.vue` (nouveau, état local réactif synchronisé sur
  la prop `modelValue`, émet la liste complète à chaque édition) réutilisé tel quel en création
  (`DossierTypesAdminView.vue`, formulaire existant étendu) et en édition (nouveau panneau inline
  révélé par l'action « Modifier les champs » par ligne du tableau, pas de modale).
- **Validation formulaire** — `validateFieldDefinitions.ts` (nouveau, fonction pure) : `name`/`label`
  non vides, unicité de `name`, `options` non vide si `SELECT`, `defaultValue` cohérente avec
  `type`. Bloque la soumission côté client avant tout appel réseau (create et édition).

### Patterns & Best Practices

- Remplacement intégral de `fieldDefinitions` à chaque sauvegarde (pas de diff), même convention
  que le MCP `define_dossier_type`.
- Composant frontend en HTML natif + scoped CSS + `data-testid`, pas de composant PrimeVue isolé —
  suit le pattern déjà en place sur `DossierTypesAdminView.vue`/`DynamicFields.vue`.
- Panneau d'édition pré-rempli directement depuis l'objet `DossierType` déjà chargé par la liste
  paginée (`listDossierTypes`), **sans appel réseau supplémentaire** — écart mineur par rapport au
  design initial qui envisageait un `GET /api/dossier-types/{code}` dédié ; simplification trouvée
  en implémentation (la réponse de liste porte déjà `fieldDefinitions`/`allowUnknownFields`
  complets, vérifié dans `DossierTypeResponse.from`), pas une réduction de fonctionnalité.

## Integration Points
**How do pieces connect?**

- `PATCH /api/dossier-types/{code}` — aucun changement de schéma de requête/réponse, nouveau
  comportement 409 uniquement en cas d'incompatibilité (`ProblemDetail` avec `incompatibleCount`/
  `sampleReferences`). Golden OpenAPI (`openapi/openapi.yaml`) inchangé (vérifié — aucun DTO
  modifié, confirmé par `./mvnw verify` sans diff signalé sur le job golden).
- MCP `define_dossier_type` — comportement externe strictement inchangé (mêmes réponses, même
  exception levée), seul le point d'appel interne du check a bougé.

## Error Handling
**How do we handle failures?**

- `DossierTypeSchemaIncompatibleException` → 409, `detail` texte + propriétés structurées
  (`incompatibleCount`, `sampleReferences`) sur le `ProblemDetail`.
- `name` dupliqué dans `fieldDefinitions` → `IllegalArgumentException` → 400 (handler générique
  existant, aucun nouveau code).
- Frontend : erreurs de validation locale affichées via le pattern `error.value`/`editFieldErrors`
  déjà utilisé ailleurs dans `DossierTypesAdminView.vue` (`toProblemDetail(err).detail`) ; le
  panneau d'édition reste ouvert sur erreur serveur (pas de perte de saisie).

## Performance Considerations
**How do we keep it fast?**

Aucun changement — le check de compatibilité relit tous les dossiers du type sans pagination,
déjà le comportement en prod via le MCP (pas une régression introduite ici). Le déplacement du
check dans `UpdateDossierTypeUseCaseImpl` **supprime** une requête dupliquée sur le chemin MCP réel
(le tool ne l'appelle plus deux fois).

## Phase 7 — Check Implementation (2026-08-28)

Revue fichier par fichier du diff complet (`git diff origin/main...HEAD`, 27 fichiers stricts —
confirmé après un `git fetch origin main` qui a révélé que le ref `origin/main` local était
périmé). Alignement design confirmé sur les 5 fichiers backend et les 3 fichiers frontend
principaux ; aucune fuite de données sensibles introduite (les `sampleReferences` exposées en 409
sont des références de dossier générées par séquence, pas des valeurs de champ) ; aucun nouveau
composant n'introduit de mutation d'état partagé (les copies `[...array]`/`{...obj}` sont
systématiques côté `FieldDefinitionDefsEditor.vue`, pas d'aliasing avec l'objet `DossierType`
affiché dans le tableau).

**1 écart réel trouvé et corrigé** (efficacité, pas une régression fonctionnelle) :
`UpdateDossierTypeUseCaseImpl.update()` exécutait le check de compatibilité (lecture DB de tous
les dossiers du type) **avant** la construction de `existing.withUpdate(...)`, qui valide
pourtant l'unicité de `name`/la politique de rétention/le SLA en mémoire, sans I/O. Une requête
avec un `name` dupliqué **et** un schéma par ailleurs incompatible aurait payé une lecture DB
inutile avant d'échouer. Réordonné : la construction (échec rapide, 400) passe désormais avant le
check de compatibilité (409). Corrigé en TDD (`update_duplicateFieldName_failsFastWithoutQueryingCompatibility`,
rouge confirmé avant le correctif — vérifie `verifyNoInteractions(checkCompatibilityUseCase)`).
`./mvnw verify` ciblé (`UpdateDossierTypeUseCaseImplTest`, `DefineDossierTypeToolTest`,
`DossierApiTest`, `DossierExceptionHandlerTest`, `DossierTypeTest`, `ArchitectureRulesTest`) :
124 tests, 0 échec.

Aucun autre écart bloquant ou point ouvert non traité.

## Phase 9 — Revue finale (2026-08-28)

Revue holistique avant PR : `git diff --stat origin/main...HEAD` (après `git fetch origin main`
frais) confirme **27 fichiers strictement scopés** à la feature (5 fichiers backend prod, 5 fichiers
backend test, 4 fichiers frontend prod nouveaux/modifiés, 3 fichiers frontend test, 1 fichier e2e,
6 docs `docs/ai/**` + `TODO.md`/`FILE-ATTENTE.md`/`GUIDE-UTILISATEUR.md`).

- **Callers tracés** : seuls 2 points d'instanciation de `UpdateDossierTypeUseCaseImpl` dans tout le
  dépôt (`DossierConfig`, `UpdateDossierTypeUseCaseImplTest`), tous deux mis à jour avec le nouveau
  paramètre — aucun appelant manqué. Seuls 2 consommateurs de `createDossierType`/`updateDossierType`
  côté frontend (`DossierTypesAdminView.vue`, `api/dossierTypes.ts` lui-même) — aucun autre écran
  affecté par le changement de payload de création.
- **Pas de migration, pas de changement de schéma DB, pas de changement OpenAPI** (golden inchangé,
  confirmé par `./mvnw verify` sans échec du job golden) — rollback trivial (revert du commit).
- **Pas de nouvelle dépendance**, aucun risque de conflit de version.
- **Scan diff** : aucun `console.log`/`System.out.print`/`debugger`/`TODO:`/`FIXME`/test
  `.only`/`.skip` introduit, aucun secret en clair.
- Cohérence avec des modules similaires vérifiée en Phase 3/7 (pattern `DossierConflictException`
  pour le handler d'exception, pattern `validateSlaTargetHours` pour la validation structurelle,
  pattern `DynamicFields.vue`/`DossierFieldsEditor.vue` pour le style du nouveau composant).

**Checklist finale :**
- [x] Alignement design confirmé (Phase 7)
- [x] Aucune lacune logique bloquante (1 écart mineur trouvé et corrigé en Phase 7 — ordre
  validation/check)
- [x] Sécurité : aucun changement de rôle, allowlist ArchUnit `Dossier.fields()` inchangée,
  `ArchitectureRulesTest` vert
- [x] Points d'intégration vérifiés (callers tracés ci-dessus)
- [x] Tests couvrent les changements (backend 1383/1383, frontend 247/247 + 59/59 Playwright)
- [x] Docs à jour (`GUIDE-UTILISATEUR.md` §1, `TODO.md` §47, `FILE-ATTENTE.md`, docs `docs/ai/**`)

Aucun blocage. Prêt pour PR.

## Security Notes
**What security measures are in place?**

- Aucun changement de rôle : `POST`/`PATCH /api/dossier-types` restent `TENANT_ADMIN` uniquement.
- `CheckDossierTypeSchemaCompatibilityUseCaseImpl` reste la seule nouvelle classe à lire
  `Dossier.fields()` dans ce chemin, déjà présente dans l'allowlist ArchUnit
  `DOSSIER_FIELDS_READ_ALLOWLIST` avant cette feature (aucun changement de règle nécessaire,
  confirmé par `ArchitectureRulesTest` vert).
