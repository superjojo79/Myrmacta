---
phase: planning
title: Unicité d'un champ de dossier — plan
description: Découpage en tâches TDD (issue #184)
---

# Plan — `unicite-champ-dossier` (issue #184)

Chaque tâche commence par un test rouge (skill `tdd`).

## Lot A — unicité

- [ ] A1 `FieldDefinition.unique` + règles U2-U4 (domaine, unitaires) ; JSON et golden OpenAPI.
- [ ] A2 `UniqueFieldKeys` et forme canonique (U5-U7).
- [ ] A3 ChangeSet `037-dossier-unique-value` ; synchronisation dans `JpaDossierRepositoryAdapter.save` avec
      `ON CONFLICT` et verrou du type ; `DossierUniqueValueIT`, `DossierUniqueConcurrencyIT`.
- [ ] A4 `DossierUniquenessPort` et pré-contrôles dans `CreateDossierUseCaseImpl` et `UpdateDossierFieldsUseCaseImpl`.
- [ ] A5 Type : détection des doublons dans le contrôle de compatibilité, reconstruction des lignes à l'enregistrement du
      type (U10, U11) ; promotion (U12).
- [ ] A6 Écritures par workflow (tâche `dossier-fields`, `fieldMappingTask`) et MCP couvertes par IT.

## Lot B — brique de recherche

- [ ] B1 `DossierSearchPort.findOpenMatches` (adaptateur JPA, IT).
- [ ] B2 Use case `SearchSimilarDossiersUseCase` (domaine).
- [ ] B3 `DossierSearchDelegate` (`dossierSearchTask`), `AllowedBpmnDelegates`, validation au déploiement,
      `BuildingBlockCatalog` (`dossier-search`) ; `DossierSearchTaskIT`.

## Lot C — SPA

- [ ] C1 Éditeur de schéma : case « Valeur unique », validation client, i18n fr/en (Vitest).
- [ ] C2 Formulaire de dossier : rendu de l'erreur `UNIQUE` (Vitest).
- [ ] C3 Palette : bloc `dossier-search`, diagnostics (Vitest).
- [ ] C4 Playwright : schéma, création refusée, palette ; mocks `frontend/e2e/**`.

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

- Écriture hors `DossierRepository.save` (requête SQL directe) qui contournerait la table : à rechercher en A3.
- Course entre l'activation de `unique` et une création concurrente : couverte par le verrou du type (D-UNIQUE-TYPE-LOCK).
- CI GitHub Actions actuellement hors service (jobs refusés en 2-3 s) : les vérifications locales font foi en attendant.
