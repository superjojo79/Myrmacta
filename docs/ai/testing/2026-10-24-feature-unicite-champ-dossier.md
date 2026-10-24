---
phase: testing
title: Unicité d'un champ de dossier — tests
description: Scénarios dérivés des règles U1-U12 et R1-R7
---

# Tests — `unicite-champ-dossier` (issue #184)

Objectif de couverture : 100 % des nouvelles classes du domaine (`UniqueFieldKeys`, validation de `FieldDefinition`,
pré-contrôles), chaque règle U/R couverte par au moins un test. Tests d'intégration sur PostgreSQL embarqué (zonky).

## Unitaires — domaine

- [x] `FieldDefinitionTest` : `unique` accepté sur `STRING`, `NUMBER`, `DATE`, champ calculé ; refusé sur `BOOLEAN`,
      `SELECT`, `MONEY`, `EXTERNAL_REF`, `FILE`, groupe, sous-champ de groupe, champ sensible (U2-U4) ; `null` → faux.
- [x] Valeur unique de plus de 500 caractères refusée (`TOO_LONG`) ; `unique` refusé sur un nom de plus de 100 caractères (U13).
- [x] `UniqueFieldKeysTest` : forme canonique (`1.50` = `1.5`, date ISO, texte non modifié) ; valeur vide ignorée ;
      aucune clé pour `CLOTURE`/`ARCHIVE` ; un champ non unique ignoré (U5-U7).
- [x] `CreateDossierUseCaseImplTest` / `UpdateDossierFieldsUseCaseImplTest` : conflit → erreur `UNIQUE` avec la
      référence, groupée avec les autres erreurs ; le dossier lui-même n'est pas un conflit (PATCH sans changement).
- [x] `UpdateDossierTypeUseCaseImplTest` / compatibilité : activer `unique` avec doublons ouverts → refus avec valeurs et
      références ; doublons seulement parmi les clôturés → accepté ; retirer `unique` → accepté (U10, U11).
- [x] `SearchSimilarDossiersUseCaseTest` : champ non cherchable → erreur ; source `dossierField:` sensible ou non
      déclarée → erreur (R8) ; source vide → (0, null) sans appel au port ;
      exclusion du dossier courant transmise au port.
- [x] `BlockAttributeRules` / catalogue : 0 ou 4 critères refusés, ligne mal formée, source inconnue, sortie réservée ou
      absente.

## Intégration — backend

- [x] `DossierUniqueValueIT` : création d'un doublon refusée (400, `UNIQUE`, référence) ; même valeur après clôture du
      premier acceptée ; PATCH vers une valeur prise refusé ; purge → lignes supprimées (cascade).
- [x] `DossierUniqueConcurrencyIT` : deux créations simultanées de la même valeur (deux threads, barrière) → une
      réussite, une erreur `UNIQUE`, une seule ligne en base (U9).
- [x] `DossierUniqueComputedIT` : `plaqueNormalisee` calculée ; `1 ABC 123` puis `1-abc 123` → refus.
- [x] Écritures par workflow : complétion d'une tâche `dossier-fields` et `fieldMappingTask` avec une valeur prise →
      refus / incident, rien n'est écrit.
- [x] Verrou du type : une mise à jour du type qui active `unique` pendant une création concurrente de doublon (barrière
      entre threads) → soit la création échoue `UNIQUE`, soit la mise à jour échoue 409 ; jamais deux dossiers ouverts
      avec la même valeur et un champ unique actif.
- [x] Type : activation avec doublons ouverts → 409 avec exemples ; sans doublon → contrainte active immédiatement
      (création suivante refusée) ; désactivation → lignes supprimées.
- [ ] Promotion de configuration : bundle qui active `unique` sur une cible contenant des doublons → refus (U12).
- [x] `DossierSearchTaskIT` : 0 voisin → 0 et `null` ; 2 voisins → 2 et référence du plus ancien ; clôturés et dossier
      courant exclus ; source `variable:` ; champ rendu non cherchable après déploiement → incident rejouable.
- [x] Déploiement : BPMN avec `searchCriteria` invalide ou sortie réservée refusé (parser).
- [x] MCP : `define_dossier_type` avec `unique` ; `create_dossier` en doublon → erreur `UNIQUE`.
- [x] `ChangelogChecksumStabilityIT` vert avec le changeSet 037 ; ArchUnit vert (allowlist `Dossier.fields()` étendue).

## Frontend — Vitest

- [x] `FieldDefinitionDefsEditor.spec.ts` : case « Valeur unique » visible, désactivée hors types autorisés et pour un
      champ sensible ; la raison inactive est un texte visible relié par `aria-describedby` ; valeur envoyée dans la
      charge utile ; absente des colonnes de groupe.
- [x] `validateFieldDefinitions.spec.ts` : mêmes règles que le serveur (U2, U3, U4, U13, sous-champ).
- [x] `api/http` : extraction de `errors[]` d'un `ProblemDetail` (avec `code`, `reference`), tolérante à une réponse
      sans `errors` ; extraction de `duplicates[]`.
- [x] `DynamicFields` : erreur serveur affichée sous le bon champ, hors du `<label>` ; `UNIQUE` traduit avec la
      référence ; autre code → message serveur ; la référence n'est pas montrée pour un autre code ; `aria-invalid` et
      `aria-describedby` sur la saisie, sur `<output>` (champ calculé, ex. `plaqueNormalisee`) et sur `MONEY`,
      `EXTERNAL_REF`, `REPEATABLE_GROUP`.
- [x] `CreateDossierView` : un 400 avec `errors[]` alimente les erreurs par champ ; un corps sans `errors[]` garde le
      `detail` dans une bannière `role="alert"`.
- [x] `DossierFieldsTaskForm` et `MobileTasksView` : un premier refus sur un champ absent ou masqué, puis un refus sur
      un champ visible, retire la référence précédente de la bannière de ce formulaire ; un champ masqué qui redevient
      visible aussi. Deux tâches : le `null` de la seconde ne retire pas la référence hors écran de la première.
      Un second PATCH accepté `{ priority: 'LOW', siret: null }` retire la bannière DOS-9 du champ masqué. Une erreur
      hors schéma absente du correctif reste, de même qu'un envoi dont le correctif est vide.
- [x] `DossierDetailView` : l'enregistrement des champs hors écran garde sa bannière quand une tâche liée affiche
      ensuite une erreur sur un champ visible.
- [x] `DossierDetailView` (écran « corriger ») : `UNIQUE` sous le champ, sans le `detail` anglais ; un champ hors écran
      reste dans la bannière, phrase traduite.
- [x] Admin type : refus 409 avec un paragraphe pour `detail` et une liste pour les doublons ; une valeur multiligne
      reste dans un seul item ; au plus 20 références, avec le nombre total ; pas de HTML injecté.
- [x] `DossierUniqueKeys` et le contrôle de compatibilité : au plus 20 références par valeur, `referenceCount` et le
      nombre de dossiers incompatibles complets.
- [x] `blockDiagnostics.spec.ts` : bloc `search-dossiers` (critères, noms réservés, sorties distinctes, longueur au-delà
      de `maxLength` = 1000, caractères de contrôle).

## Frontend — Playwright

- [x] Admin : cocher « Valeur unique », enregistrer (`E2E-UNIQUE-1`, la raison d'une case inactive est visible), voir
      le refus quand l'API renvoie des doublons (`E2E-UNIQUE-2`, mock).
- [x] Dossier : création refusée avec l'erreur `UNIQUE` sous le champ et la référence (`E2E-UNIQUE-3`, mock 400).
- [x] Tâche `dossier-fields` : enregistrement refusé avec l'erreur `UNIQUE` sous le champ (`E2E-UNIQUE-4`, mock 400).
      Deux tâches gardent chacune leur bannière (`E2E-UNIQUE-6`). Un PATCH accepté qui retire le champ masqué retire
      la bannière, même si la complétion de la tâche échoue ensuite (`E2E-UNIQUE-8`).
- [x] Éditeur BPMN : la brique « Rechercher des dossiers » est dans la palette, produit les attributs attendus, et un
      critère mal formé est signalé sur le canevas (`E2E-UNIQUE-5`).

### Réalisé pour le lot A (2026-10-24)

- Domaine : `FieldUniqueModelTest` (U2-U4, U13, copies, message de l'exception), `UniqueFieldKeysTest`,
  `DossierUniqueKeysTest` (recalcul d'un champ calculé, doublons), `DossierFieldValidatorTest` (U13), use cases de création
  et de correction, `CreateDossierToolTest` (essai à blanc).
- Intégration : `DossierUniqueValueIT` (U5, U7, U8, U9, purge), `DossierUniqueTypeIT` (U10, U11, champ calculé ajouté
  après coup, course type/dossier), `UniqueFieldApiTest` (400 `UNIQUE` + `reference`, 409 `duplicates`, refus à la
  définition), `UniqueFieldWorkflowIT` (`fieldMappingTask`).
- Revue adversariale : `WebhookControllerTest.receive_dossierFieldValidationFailure_isA400WithoutTheHolderReference`.
- Mutations vérifiées : sans synchronisation à l'enregistrement (4 échecs), sans verrou exclusif du type (1 échec), sans
  recalcul des règles (1 échec).
- Promotion (U12) : couverte par construction (`ImportConfigurationUseCaseImpl` passe par le contrôle de compatibilité et
  `UpdateDossierTypeUseCase`) ; pas de test dédié.

### Réalisé pour le lot B (2026-10-24)

- `DossierSearchSpecTest`, `SearchSimilarDossiersUseCaseImplTest` (8 cas, dont l'option `SELECT` retirée),
  `BlockAttributeRulesTest.theDossierSearchBlockIsValidatedAtDeployment`, `BuildingBlockCatalogTest`,
  `FlowableBpmnParserAdapterBlockTest` (XML de chaque brique accepté).
- `DossierOpenMatchesIT` (PostgreSQL) et `DossierSearchTaskIT` (moteur).

## Fixtures et mocks

- Type de test avec un champ unique simple, un champ calculé unique et un champ cherchable non unique.
- Mocks E2E `frontend/e2e/**` : réponses 400 `UNIQUE` et 409 de compatibilité, catalogue de briques avec `dossier-search`.

## Vérifications finales

- [ ] `./mvnw verify`
- [ ] `cd frontend && npm run lint && npm run test:all`
- [ ] `scripts/check-docs.sh`
- [ ] Recette manuelle de l'exemple riverain (R7) sur l'instance locale.
