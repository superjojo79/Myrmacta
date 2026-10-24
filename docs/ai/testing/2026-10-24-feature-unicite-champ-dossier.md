---
phase: testing
title: Unicité d'un champ de dossier — tests
description: Scénarios dérivés des règles U1-U12 et R1-R7
---

# Tests — `unicite-champ-dossier` (issue #184)

Objectif de couverture : 100 % des nouvelles classes du domaine (`UniqueFieldKeys`, validation de `FieldDefinition`,
pré-contrôles), chaque règle U/R couverte par au moins un test. Tests d'intégration sur PostgreSQL embarqué (zonky).

## Unitaires — domaine

- [ ] `FieldDefinitionTest` : `unique` accepté sur `STRING`, `NUMBER`, `DATE`, champ calculé ; refusé sur `BOOLEAN`,
      `SELECT`, `MONEY`, `EXTERNAL_REF`, `FILE`, groupe, sous-champ de groupe, champ sensible (U2-U4) ; `null` → faux.
- [ ] Valeur unique de plus de 500 caractères refusée (`TOO_LONG`) ; `unique` refusé sur un nom de plus de 100 caractères (U13).
- [ ] `UniqueFieldKeysTest` : forme canonique (`1.50` = `1.5`, date ISO, texte non modifié) ; valeur vide ignorée ;
      aucune clé pour `CLOTURE`/`ARCHIVE` ; un champ non unique ignoré (U5-U7).
- [ ] `CreateDossierUseCaseImplTest` / `UpdateDossierFieldsUseCaseImplTest` : conflit → erreur `UNIQUE` avec la
      référence, groupée avec les autres erreurs ; le dossier lui-même n'est pas un conflit (PATCH sans changement).
- [ ] `UpdateDossierTypeUseCaseImplTest` / compatibilité : activer `unique` avec doublons ouverts → refus avec valeurs et
      références ; doublons seulement parmi les clôturés → accepté ; retirer `unique` → accepté (U10, U11).
- [ ] `SearchSimilarDossiersUseCaseTest` : champ non cherchable → erreur ; source `dossierField:` sensible ou non
      déclarée → erreur (R8) ; source vide → (0, null) sans appel au port ;
      exclusion du dossier courant transmise au port.
- [ ] `BlockAttributeRules` / catalogue : 0 ou 4 critères refusés, ligne mal formée, source inconnue, sortie réservée ou
      absente.

## Intégration — backend

- [ ] `DossierUniqueValueIT` : création d'un doublon refusée (400, `UNIQUE`, référence) ; même valeur après clôture du
      premier acceptée ; PATCH vers une valeur prise refusé ; purge → lignes supprimées (cascade).
- [ ] `DossierUniqueConcurrencyIT` : deux créations simultanées de la même valeur (deux threads, barrière) → une
      réussite, une erreur `UNIQUE`, une seule ligne en base (U9).
- [ ] `DossierUniqueComputedIT` : `plaqueNormalisee` calculée ; `1 ABC 123` puis `1-abc 123` → refus.
- [ ] Écritures par workflow : complétion d'une tâche `dossier-fields` et `fieldMappingTask` avec une valeur prise →
      refus / incident, rien n'est écrit.
- [ ] Verrou du type : une mise à jour du type qui active `unique` pendant une création concurrente de doublon (barrière
      entre threads) → soit la création échoue `UNIQUE`, soit la mise à jour échoue 409 ; jamais deux dossiers ouverts
      avec la même valeur et un champ unique actif.
- [ ] Type : activation avec doublons ouverts → 409 avec exemples ; sans doublon → contrainte active immédiatement
      (création suivante refusée) ; désactivation → lignes supprimées.
- [ ] Promotion de configuration : bundle qui active `unique` sur une cible contenant des doublons → refus (U12).
- [ ] `DossierSearchTaskIT` : 0 voisin → 0 et `null` ; 2 voisins → 2 et référence du plus ancien ; clôturés et dossier
      courant exclus ; source `variable:` ; champ rendu non cherchable après déploiement → incident rejouable.
- [ ] Déploiement : BPMN avec `searchCriteria` invalide ou sortie réservée refusé (parser).
- [ ] MCP : `define_dossier_type` avec `unique` ; `create_dossier` en doublon → erreur `UNIQUE`.
- [ ] `ChangelogChecksumStabilityIT` vert avec le changeSet 037 ; ArchUnit vert (allowlist `Dossier.fields()` étendue).

## Frontend — Vitest

- [ ] `FieldDefinitionDefsEditor.spec.ts` : case « Valeur unique » visible, désactivée hors types autorisés et pour un
      champ sensible ; valeur envoyée dans la charge utile.
- [ ] `validateFieldDefinitions.spec.ts` : mêmes règles que le serveur.
- [ ] `api/http` : extraction de `errors[]` d'un `ProblemDetail` (avec `code`, `reference`), tolérante à une réponse
      sans `errors`.
- [ ] `DynamicFields` : erreur serveur affichée sous le bon champ ; `UNIQUE` traduit avec la référence ; autre code →
      message serveur.
- [ ] `CreateDossierView` et `DossierFieldsTaskForm` : un 400 avec `errors[]` alimente les erreurs par champ.
- [ ] Admin type : refus 409 avec valeurs en double affiché.
- [ ] `blockDiagnostics.spec.ts` / `catalogClient.spec.ts` : bloc `dossier-search` et ses diagnostics.

## Frontend — Playwright

- [ ] Admin : cocher « Valeur unique », enregistrer, voir le refus quand l'API renvoie des doublons (mock).
- [ ] Dossier : création refusée avec l'erreur `UNIQUE` sous le champ et la référence (mock 400).
- [ ] Tâche `dossier-fields` : enregistrement refusé avec l'erreur `UNIQUE` sous le champ (mock 400).
- [ ] Éditeur BPMN : la brique « Rechercher des dossiers » est dans la palette et produit les attributs attendus.

## Fixtures et mocks

- Type de test avec un champ unique simple, un champ calculé unique et un champ cherchable non unique.
- Mocks E2E `frontend/e2e/**` : réponses 400 `UNIQUE` et 409 de compatibilité, catalogue de briques avec `dossier-search`.

## Vérifications finales

- [ ] `./mvnw verify`
- [ ] `cd frontend && npm run lint && npm run test:all`
- [ ] `scripts/check-docs.sh`
- [ ] Recette manuelle de l'exemple riverain (R7) sur l'instance locale.
