---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
---

# Testing Strategy

## Test Coverage Goals
**What level of testing do we aim for?**

- Backend : 100% des lignes/branches nouvelles (compact constructor `DossierType`,
  `UpdateDossierTypeUseCaseImpl`, nouveau handler d'exception) — seuils bundle existants inchangés
  (Jacoco LINE 85% / BRANCH 75%, déjà tenus à ce jour).
- Frontend : Vitest sur le nouveau composant `FieldDefinitionDefsEditor.vue` et l'utilitaire de
  validation, plus les nouveaux chemins de `DossierTypesAdminView.vue` ; Playwright pour les
  parcours bout-en-bout (création avec schéma, édition bloquée par incompatibilité).
- Alignement avec les success criteria du document requirements : blocage 409 effectif et
  visible en UI, unicité de `name` refusée aux deux niveaux (formulaire + domaine), régression MCP
  nulle (`DefineDossierTypeTool`).

## Unit Tests
**What individual components need testing?**

### `DossierType` (compact constructor — D-NAME-UNIQUE)
- [x] T-DOM-1: rejette `fieldDefinitions` avec deux entrées de même `name` (`IllegalArgumentException`)
- [x] T-DOM-2: accepte `fieldDefinitions` avec des `name` tous uniques (régression)
- [x] T-DOM-3: accepte `fieldDefinitions` vide (régression — comportement actuel inchangé)
- [x] T-DOM-4: message d'exception mentionne le `name` dupliqué (exploitable en 400 `detail`)

### `UpdateDossierTypeUseCaseImpl` (D-UI-SCHEMA-COMPAT, D-DESIGN-CHECK-PLACEMENT)
- [x] T-USE-1: schéma compatible avec tous les dossiers existants → persiste et retourne le type mis à jour
- [x] T-USE-2: schéma incompatible avec ≥1 dossier existant → lève `DossierTypeSchemaIncompatibleException`, `repository.save` **jamais** appelé
- [x] T-USE-3: type sans aucun dossier existant → toujours compatible, persiste directement (le check ne bloque jamais un type vierge)
- [x] T-USE-4: `TenantAdminGuard` toujours appliqué avant le check de compatibilité (régression — un acteur non autorisé est rejeté sans lire les dossiers ni le repository)
- [x] T-USE-5 (régression) : les 7 tests existants de `UpdateDossierTypeUseCaseImplTest` continuent de passer une fois le constructeur étendu avec `CheckDossierTypeSchemaCompatibilityUseCase` (mock stub par défaut = compatible dans `setUp()`)

### `DossierExceptionHandler` (nouveau handler)
- [x] T-EXC-1: `DossierTypeSchemaIncompatibleException` → `ProblemDetail` 409, `title` = "Dossier type schema incompatible", `detail` contient le nombre de dossiers concernés et les références d'exemple
- [x] T-EXC-2: propriétés structurées `incompatibleCount`/`sampleReferences` posées sur le `ProblemDetail`
- [x] T-EXC-3: `sampleReferences` vide → `detail` sans le suffixe "(ex. …)" (pas de parenthèse vide)

### `DefineDossierTypeTool` (régression MCP — D-DESIGN-CHECK-PLACEMENT)
- [x] T-MCP-1: mise à jour compatible, `dryRun=false` → `checkCompatibilityUseCase.check()` **non** appelé par l'outil (appelé une seule fois, à l'intérieur de `UpdateDossierTypeUseCaseImpl`, donc invisible du mock du tool ; `updateDossierTypeUseCase.update()` appelé directement)
- [x] T-MCP-2: mise à jour incompatible, `dryRun=false` → l'exception lancée par le mock de `updateDossierTypeUseCase.update()` (pas par `checkCompatibilityUseCase` directement) se propage identiquement ; **réécriture de `update_incompatibleSchema_isRejectedWithoutSaving`** (aujourd'hui stubbe `checkCompatibilityUseCase`, doit stubber `updateDossierTypeUseCase.update()` pour lever)
- [x] T-MCP-3: `dryRun=true` sur update → `checkCompatibilityUseCase.check()` toujours appelé explicitement par l'outil (chemin inchangé), `updateDossierTypeUseCase.update()` jamais appelé (régression déjà couverte par `dryRun_onUpdatePath_doesNotSave`, à revalider sans changement)
- [x] T-MCP-4: chemin `create` inchangé (aucun appel au check, régression déjà couverte par les tests `defineCreate` existants)
- [x] T-MCP-5 (régression) : `update_preservesExistingRetentionAndSla` adapté (retire le stub désormais inutile de `checkCompatibilityUseCase` sur le chemin non-dryRun, ou le laisse en `lenient()` si Mockito strict le exige)

### `DossierConfig` (câblage Spring)
- [x] T-CFG-1: contexte Spring démarre sans erreur avec le nouveau paramètre `CheckDossierTypeSchemaCompatibilityUseCase` sur le bean `updateDossierTypeUseCase` (couvert par les tests d'intégration existants qui chargent le contexte complet, pas de test dédié nécessaire)

### `FieldDefinitionDefsEditor.vue` (nouveau composant, Vitest)
- [x] T-FE-1: rend un état vide avec un bouton « Ajouter un champ »
- [x] T-FE-2: ajouter une ligne insère une `FieldDefinition` éditable par défaut (`type=STRING`)
- [x] T-FE-3: supprimer une ligne la retire de la liste émise
- [x] T-FE-4: réordonner (haut/bas) change l'ordre de la liste émise
- [x] T-FE-5: choisir `type=SELECT` révèle l'éditeur d'`options` ; changer vers un autre type le masque
- [x] T-FE-6: la case `sensitive` bascule le flag `sensitive` de la ligne
- [x] T-FE-7: chaque modification de ligne émet `update:modelValue` avec la liste **complète** (remplacement intégral, pas de diff)

### Utilitaire de validation formulaire (nouveau, ex. `frontend/src/dossier/validateFieldDefinitions.ts`)
- [x] T-FE-VAL-1: rejette un `name` dupliqué dans la liste
- [x] T-FE-VAL-2: rejette un `name`/`label` vide
- [x] T-FE-VAL-3: rejette un type `SELECT` avec `options` vide
- [x] T-FE-VAL-4: rejette une `defaultValue` incohérente avec `type` (`NUMBER`/`DATE`/`BOOLEAN`)
- [x] T-FE-VAL-5: accepte une liste entièrement valide (aucune erreur)

### `DossierTypesAdminView.vue` (extension, Vitest — mocks `axios` existants)
- [x] T-FE-VIEW-1: le formulaire de création inclut `allowUnknownFields` + `fieldDefinitions` issus de l'éditeur (remplace le `fieldDefinitions: []` en dur)
- [x] T-FE-VIEW-2: soumission bloquée (aucun appel API) si `validateFieldDefinitions` détecte des erreurs, message affiché
- [x] T-FE-VIEW-3: l'action « Modifier les champs » ouvre le panneau d'édition pré-rempli avec `fieldDefinitions`/`allowUnknownFields` du type existant
- [x] T-FE-VIEW-4: sauvegarde du panneau d'édition → appelle `updateDossierType` avec la liste complète, rafraîchit la liste au succès
- [x] T-FE-VIEW-5: sauvegarde en échec (409 mocké) → affiche `toProblemDetail(err).detail`, panneau reste ouvert (pas de perte de saisie), liste non rafraîchie avec une donnée obsolète
- [x] T-FE-VIEW-6: bouton Annuler du panneau d'édition abandonne les modifications locales sans appel API

## Integration Tests
**How do we test component interactions?**

- [x] T-API-1: `PATCH /api/dossier-types/{code}` avec un schéma compatible → 200, `DossierTypeResponse` reflète `fieldDefinitions`/`allowUnknownFields`
- [x] T-API-2: `PATCH /api/dossier-types/{code}` avec un schéma incompatible avec un dossier réel (fixture dossier existant du type) → 409 `ProblemDetail`, type inchangé en base après coup (relecture `GET` confirme l'ancien schéma)
- [x] T-API-3: `PATCH /api/dossier-types/{code}` avec deux `fieldDefinitions` de même `name` → 400
- [x] T-API-4: `POST /api/dossier-types` avec deux `fieldDefinitions` de même `name` → 400
- [x] T-API-5: `POST /api/dossier-types` avec `allowUnknownFields=true` + `fieldDefinitions` non vide → 201, round-trip exact dans la réponse (vérifie s'il existe déjà un test couvrant ce cas avant d'en ajouter un redondant)
- [x] T-API-6: `PATCH` sans rôle `TENANT_ADMIN` → 403 (régression, `@PreAuthorize` inchangé)

## End-to-End Tests
**What user flows need validation?**

- [x] E2E-SCHEMA-1: `TENANT_ADMIN` crée un `DossierType` avec 2+ `fieldDefinitions` (dont un `SELECT` avec options) et `allowUnknownFields` coché → le type apparaît dans la liste avec le schéma attendu
- [x] E2E-SCHEMA-2: `TENANT_ADMIN` modifie le schéma d'un type **sans** dossier existant (ajout d'un champ) → sauvegarde directe réussie
- [x] E2E-SCHEMA-3: `TENANT_ADMIN` tente une modification de schéma qui casserait un dossier existant (fixture seedée) → message d'erreur 409 affiché avec le nombre de dossiers concernés, le schéma **n'est pas** modifié (rechargement de la page confirme l'ancien schéma)
- [x] E2E-SCHEMA-4: `name` dupliqué dans l'éditeur → rejeté côté client avant tout appel réseau (aucune requête `PATCH`/`POST` interceptée)
- [x] E2E-SCHEMA-5 (régression) : parcours existants `DossierTypesAdminView` (rétention, SLA, ACL) non cassés par les modifications du formulaire de création

## Test Data
**What data do we use for testing?**

- Backend : fixtures `DossierType`/`FieldDefinition`/`Dossier` déjà présentes dans les classes de
  test existantes (`type(...)`, `field(...)` helpers) — étendues avec un cas « dossier existant
  incompatible » (ex. un `Dossier` dont une valeur `fields()` ne correspondrait plus au nouveau
  schéma proposé — champ `required` désormais absent, ou valeur hors `options` `SELECT`).
- Frontend : mocks `axios` déjà en place dans `DossierTypesAdminView.spec.ts` et
  `frontend/src/api/dossierTypes.spec.ts`, étendus avec une réponse 409 `ProblemDetail`
  (`incompatibleCount`/`sampleReferences`) pour couvrir le chemin d'erreur.
- E2E : réutilise le pattern de fixtures Playwright existant (seed via API avant le test, pas de
  jeu de données statique).

## Test Reporting & Coverage
**How do we verify and communicate test results?**

- Backend : `./mvnw verify -Dmaven.compiler.release=21` (suite complète + ArchUnit + Jacoco).
- Frontend : `cd frontend && npm run test` (Vitest), `npm run test:e2e` (Playwright) — **les deux
  obligatoires** dès que le SPA ou son contrat API consommé est touché (convention
  `flowforge-conventions`).
- Couverture vérifiée par fichier (pas seulement l'agrégat `BUNDLE`) sur les fichiers nouveaux
  (`FieldDefinitionDefsEditor.vue`, utilitaire de validation) avant de clore la Phase 8.

## Manual Testing
**What requires human validation?**

- Revue visuelle de l'éditeur de schéma (pas de maquette dédiée à suivre, cf. design) — cohérence
  avec le style existant de `DossierTypesAdminView.vue`.
- Vérification que `GUIDE-UTILISATEUR.md` §1 ne contredit plus l'écran livré (retrait du
  paragraphe « Limite actuelle de l'écran »).

## Performance Testing
**How do we validate performance?**

- Hors scope dédié — le check de compatibilité relit tous les dossiers du type sans pagination,
  déjà le comportement en prod via le MCP (pas une régression, cf. design § Non-Functional
  Requirements). Pas de nouveau test de charge pour cette feature.

## Bug Tracking
**How do we manage issues?**

- Tout bug réel trouvé pendant l'implémentation/la revue est corrigé en TDD (rouge confirmé avant
  le correctif) et documenté dans le doc implementation + `FILE-ATTENTE.md`, comme pour les
  features précédentes de ce dépôt.

## Résultats (Phase 5, 2026-08-28)

Tous les scénarios ci-dessus implémentés en TDD (rouge confirmé avant chaque correctif) et
vérifiés verts :

- Backend : `./mvnw verify -Dmaven.compiler.release=21` → **1382 tests, 0 échec**, ArchUnit vert,
  seuils Jacoco tenus, `BUILD SUCCESS`.
- Frontend : `npm run test` → **247/247** ; `npm run typecheck` propre ; `npm run lint` propre ;
  `npm run test:coverage` agrégat 87.09 %/78.82 %/86.34 %/88.8 % (stmt/branch/fn/lines, ≥ seuils
  75/60/70/80) ; `npm run test:e2e` → **59/59** (4 nouveaux `E2E-SCHEMA-*`, 0 régression).
- Couverture vérifiée par fichier (pas seulement l'agrégat) : `FieldDefinitionDefsEditor.vue`
  100 % lignes/fonctions après ajout de 6 cas de test manquants (label/defaultValue/group/options/
  toggle required/move-up/re-sync prop) ; `validateFieldDefinitions.ts` 96 %/86 % ; le panneau
  d'édition de `DossierTypesAdminView.vue` (code nouveau de cette feature) intégralement couvert
  après ajout du cas de blocage local manquant — le reste du fichier (rétention/SLA/ACL,
  préexistant, non touché) reste à son niveau de couverture antérieur, non ré-ouvert ici.
## Résultats (Phase 8, 2026-08-28, preuve fraîche post-Phase 7)

- Backend : `./mvnw verify -Dmaven.compiler.release=21` → **1383 tests, 0 échec** (1382 + 1
  nouveau test de fail-fast trouvé en Phase 7), Jacoco tenu, `BUILD SUCCESS`.
- Frontend : `npm run test` → **247/247** ; `npm run typecheck` propre ; `npm run lint` propre ;
  `npx playwright test` (`PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/pw-browsers/chromium`) → **59/59**,
  0 régression.

- Écart réel trouvé et corrigé en implémentation (pas en design) : `FieldDefinitionDefsEditor.vue`
  liait son `v-for` directement sur la prop `modelValue` au lieu de l'état local réactif `rows` —
  le composant n'affichait pas ses propres modifications avant que le parent ne renvoie la prop
  mise à jour (ex. changer `type` en `SELECT` ne révélait pas l'éditeur d'options). Corrigé en
  TDD (rouge confirmé), 21/21 tests verts ensuite.
