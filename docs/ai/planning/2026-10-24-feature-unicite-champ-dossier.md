---
phase: planning
title: Unicité d'un champ de dossier — plan
description: Découpage en tâches TDD (issue #184)
---

# Plan — `unicite-champ-dossier` (issue #184)

Chaque tâche commence par un test rouge (skill `tdd`).

## Lot A — unicité

- [x] A1 `FieldDefinition.unique` + règles U2-U4 (domaine, unitaires) ; JSON et golden OpenAPI.
- [x] A2 `UniqueFieldKeys` et forme canonique (U5-U7).
- [x] A3 ChangeSet `037-dossier-unique-value` ; verrou consultatif partagé du type et synchronisation dans
      `JpaDossierRepositoryAdapter.save` avec `ON CONFLICT` ; `DossierUniqueValueIT`, `DossierUniqueConcurrencyIT`.
- [x] A4 `DossierUniquenessPort` et pré-contrôles dans `CreateDossierUseCaseImpl` et `UpdateDossierFieldsUseCaseImpl`.
- [x] A5 Type : détection des doublons dans le contrôle de compatibilité ; verrou exclusif et reconstruction des lignes
      dans `JpaDossierTypeRepositoryAdapter.save` (U10, U11) ; IT de course type/dossier ; promotion (U12).
- [x] A6 Écritures par workflow (tâche `dossier-fields`, `fieldMappingTask`) et MCP couvertes par IT.

- [x] A7 (revue adversariale) Webhook entrant : erreur de validation de champ → 400 déterministe, corps sans référence ;
      essai à blanc MCP avec pré-contrôle ; message de l'exception détaillé.

## Lot B — brique de recherche

- [x] B0 `DossierSearchSpec` : grammaire de `searchCriteria`.
- [x] B1 `DossierSearchPort.findOpenMatches` (adaptateur JPA, IT).
- [x] B2 Use case `SearchSimilarDossiersUseCase` (domaine).
- [x] B3 `DossierSearchDelegate` (`dossierSearchTask`), `AllowedBpmnDelegates`, validation au déploiement,
      `BuildingBlockCatalog` (`dossier-search`) ; `DossierSearchTaskIT`.

## Lot C — SPA

- [x] C1 Éditeur de schéma : case « Valeur unique », validation client, i18n fr/en (Vitest).
- [x] C2 Erreurs de champ du serveur : extraction de `errors[]` (`api/http.ts`), affichage dans `DynamicFields.vue`,
      branchement dans `CreateDossierView.vue`, `DossierFieldsTaskForm.vue` et l'écran « corriger »
      (`DossierFieldsEditor.vue` / `DossierDetailView.vue`), message `UNIQUE` traduit ; refus 409 détaillé dans
      l'admin des types (Vitest).
- [x] C3 Palette : bloc `search-dossiers` (identifiant livré au lot B), diagnostics (Vitest), y compris la longueur
      maximale et les caractères de contrôle.
- [x] C4 Playwright : schéma, création refusée, tâche `dossier-fields`, palette ; mocks `frontend/e2e/**`. La raison
      d'une case « Valeur unique » inactive est visible (`E2E-UNIQUE-1`).
- [x] C5 Revue du lot C : rétractation de la bannière de tâche, erreurs sous le champ sur l'écran « corriger »,
      affichage 409 borné (valeur non découpée, références plafonnées côté serveur et à l'écran), accessibilité des
      champs calculés et structurés, bannière de création `role="alert"`.

## Lot D — exemple et documentation

- [ ] D1 Exemple riverain : `plaqueNormalisee` unique, case de vérification retirée (questions, générateur BPMN), README
      R7, `DIFFICULTES.md` §1 levée.
- [ ] D2 Registre §102, `identifiants.md`, `04-dossiers.md`, `13-modele-physique.md`, `GUIDE-UTILISATEUR.md`,
      `GUIDE-CONCEPTION-BPMN.md` ; `scripts/check-docs.sh --write-decisions-index`.
- [ ] D3 Revue adversariale (oracle, contournement par une écriture hors use case, course type/dossier) ; vérifications
      finales ; vider `docs/ai/`.

## Dépendances

A1 → A2 → A3 → A4 → A5 ; A6 après A4. B1 → B2 → B3, indépendant du lot A. C1-C2 après A1 ; C3 après B3. D en dernier.

## Risques

- Écriture hors `DossierRepository.save` qui contournerait la table : aucune trouvée en phase 2 ; revérifier en revue (D3).
- Course entre l'activation de `unique` et une création concurrente : couverte par le verrou consultatif du type
  (D-UNIQUE-TYPE-LOCK) et prouvée par un IT dédié.
- Affichage des erreurs par champ : nouveau comportement pour toutes les erreurs de validation, pas seulement `UNIQUE` ;
  vérifier que les messages serveur existants restent lisibles.
- CI GitHub Actions actuellement hors service (jobs refusés en 2-3 s, plus de minutes) : les vérifications locales,
  rejouant chaque job de `.github/workflows/`, font foi.
- La case n'est proposée que pour les champs de dossier (`mode === 'fields'`), pas pour une question de tâche ni une
  colonne de groupe : l'unicité ne s'y applique pas.

## Avancement

Lots A, B et C livrés dans le code de travail. Les sept constats de la revue adversariale du lot C sont corrigés. La
bannière est isolée par formulaire. Un PATCH accepté ne republie que les erreurs hors de ce correctif : retirer la
valeur d'un champ masqué retire son erreur, un correctif vide ou une erreur hors schéma non touchée la laisse. Le lot D
(exemple riverain, documentation, clôture du registre, vidage de `docs/ai/`) n'est pas commencé. Le point §102 reste
ouvert. Une nouvelle revue indépendante suit.
