---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: done
created: 2026-08-15
updated: 2026-08-15
feature: patch-dossier-fields
phase2_stakeholder_sync: 2026-08-15
phase5_execution: 2026-08-15
phase8_audit: 2026-08-15
---

# Testing Strategy

## Test Coverage Goals
**What level of testing do we aim for?**

- Backend : Jacoco ≥ 80 % sur les zones touchées (convention projet).
- Chaque critère de succès du doc de requirements couvert par au moins un
  test automatisé listé ci-dessous.

## Unit Tests
**What individual components need testing?**

### `DossierFieldMerger.merge` (domaine, fonction pure)
- [x] Champ présent dans le patch avec une valeur → écrase la valeur
      existante.
- [x] Champ présent dans le patch avec `null` → retiré du résultat.
- [x] Champ absent du patch → conservé tel quel dans le résultat.
- [x] Patch vide → résultat identique au jeu de champs existant.
- [x] Nouveau champ (absent de l'existant) posé par le patch → présent
      dans le résultat.

### `Dossier.withFields(Map, String, Instant)` (nouvelle surcharge)
- [x] Pose `fields`, `updatedBy`, `updatedAt` ; laisse `status`, `version`,
      `closedBy`, `closedAt` inchangés.
- [x] Surcharge `withFields(Map)` existante (utilisée par
      `DossierFieldRedactor`) toujours présente et inchangée — pas de
      régression sur son appelant.

### `DossierFieldMerger.changedSensitiveFieldNames` (domaine, fonction pure)
- [x] Champ `sensitive` dont la valeur change (existant → différente dans
      le merged) → présent dans le résultat.
- [x] Champ `sensitive` réécrit avec une valeur **identique** → absent du
      résultat (pas d'événement pour un no-op).
- [x] Champ `sensitive` nouvellement posé (absent avant, présent après) →
      présent dans le résultat.
- [x] Champ `sensitive` effacé (`null`, présent avant, absent après) →
      présent dans le résultat.
- [x] Champ non-`sensitive` modifié → jamais dans le résultat, quel que
      soit le changement.

### `UpdateDossierFieldsUseCaseImpl`
- [x] Fusionne le patch, valide le jeu fusionné, sauvegarde ; renvoie
      `UpdateDossierFieldsResult` avec le dossier mis à jour
      (`updatedAt` avancé) et la liste des champs `sensitive` changés.
- [x] Rejette (400, `IllegalArgumentException`) un `fieldsPatch` `null` ou
      vide (D-EMPTY-BODY).
- [x] Rejette (403) si l'actor n'a pas EDIT sur le type du dossier.
- [x] Rejette (404) si le dossier n'existe pas ou appartient à un autre
      tenant.
- [x] Rejette (409) si le dossier est `CLOTURE`.
- [x] Rejette (409) si le dossier est `ARCHIVE`.
- [x] Accepte l'édition sur `DRAFT`/`OUVERT`/`EN_COURS`/`SUSPENDU`
      (Assumption A1).
- [x] Rejette (409) si `expectedVersion` fourni ne correspond pas à
      `dossier.version()`.
- [x] Accepte sans `expectedVersion` (verrou optionnel, comme
      `PATCH .../status`).
- [x] Rejette (400, `DossierFieldValidationException`) l'effacement
      (`null`) d'un champ `required`.
- [x] Rejette (400) une valeur de type incorrect ou une option `SELECT`
      invalide dans le jeu fusionné. (`TYPE_MISMATCH` testé directement dans
      `UpdateDossierFieldsUseCaseImplTest`/`DossierApiTest` ; `INVALID_OPTION`
      couvert au niveau du `DossierFieldValidator` réutilisé tel quel —
      `DossierFieldValidatorTest`, aucune branche spécifique dans le use case.)
- [x] Rejette (400) un champ inconnu si `allowUnknownFields = false`.
- [x] Accepte l'édition même si `DossierType.active = false` (Assumption
      A2).
- [x] Champ `sensitive` modifiable par tout détenteur EDIT, aucune
      restriction d'accès supplémentaire (D-SENSITIVE-AUDIT) —
      `sensitiveFieldsChanged` non vide n'empêche jamais l'écriture.
- [x] `UpdateDossierFieldsUseCaseImpl` ne dépend d'aucune classe
      `infrastructure.*` (vérification ArchUnit — voir Component
      Breakdown du design, piège identifié en Phase 2).

### `SecurityAuditLog.sensitiveFieldChanged`
- [x] Log contient `dossierId` et le nom du champ, jamais la valeur
      (ancienne ou nouvelle).
- [x] Niveau INFO (pas WARN, pour rester distinct des événements de
      refus existants).

## Integration Tests
**How do we test component interactions?**

- [x] `PATCH /api/dossiers/{id}/fields` — 200 + `DossierResponse` complet
      (MockMvc, H2 mode PostgreSQL).
- [x] `null` dans le body JSON désérialisé correctement en clé présente à
      valeur `null` dans le `Map<String,Object>` (pas silencieusement
      supprimée par Jackson avant d'atteindre le use case).
- [x] 400/403/404/409 — mêmes scénarios que les tests unitaires, vérifiés
      bout en bout avec `ProblemDetail`.
- [x] Après un PATCH fields, `GET /api/dossiers/{id}` par un non-admin
      redacte toujours les champs `sensitive` (régression
      `DossierFieldRedactor`).
- [x] Après un PATCH fields, `GET /api/dossiers?sort=updatedAt,desc`
      (pagination-dossiers) fait remonter le dossier édité en tête de
      liste.
- [x] `PATCH .../fields` modifiant un champ `sensitive` → `SECURITY_AUDIT`
      émis (capture logger, comme les tests existants d'`AccessDeniedException`) ;
      un PATCH ne touchant aucun champ `sensitive` → aucun événement.
- [x] Régression : `POST /api/dossiers`, `PATCH .../status`,
      `GET .../elements`, `GET .../relations` inchangés.

### Scénarios ajoutés en Phase 8 (audit indépendant)

Trous identifiés en repassant le doc scénario par scénario contre le code
réel — non couverts par un test avant cette phase, comblés maintenant.

- [x] **Cas combiné statut + verrou.** Dossier `CLOTURE` avec un
      `expectedVersion` qui correspond **exactement** à la version
      courante → 409 quand même, message `ProblemDetail.detail` contenant
      "status" et **pas** "Expected version" (preuve que le gate de
      statut est bien vérifié indépendamment du verrou, comme l'exige
      l'edge case des requirements — jusqu'ici seulement vérifié par
      lecture de code en Phase 7, jamais par un test isolant précisément
      la combinaison). `UpdateDossierFieldsUseCaseImplTest
      .update_rejectsClotureEvenWithMatchingExpectedVersion` (unitaire) +
      `DossierApiTest.updateFields_statusClotureWithMatchingExpectedVersion_stillReturns409WithStatusDetail`
      (bout en bout).
- [x] **D-NOOP-VERSION bout en bout.** Un PATCH qui fusionne vers un jeu
      de champs strictement identique à l'existant avance quand même
      `version`/`updatedAt` — vérifié avec la vraie DB H2 (pas un mock),
      preuve directe du success criterion D-NOOP-VERSION plutôt qu'une
      simple affirmation ("c'est automatique via `@Version`").
      `DossierApiTest.updateFields_noNetChange_stillAdvancesVersionAndUpdatedAt`.
- [x] **Plusieurs champs `sensitive` changés dans le même PATCH.** Les
      tests précédents ne couvraient qu'un seul champ sensible à la fois ;
      vérifié qu'un patch touchant deux champs `sensitive` simultanément
      émet bien **deux** événements `SECURITY_AUDIT` distincts (un par
      champ), pas un seul événement agrégé ni un événement manquant pour
      le second champ.
      `DossierApiTest.updateFields_multipleSensitiveFieldsChanged_emitsOneSecurityAuditEventPerField`.

## Test Data
**What data do we use for testing?**

- Fixtures : au moins un `DossierType` avec un champ `required`, un champ
  `SELECT` à options, et un champ `sensitive`, pour couvrir tous les
  chemins de validation en un seul jeu de fixtures.
- Dossiers de test dans chaque statut (`DRAFT`, `OUVERT`, `CLOTURE`,
  `ARCHIVE`) pour couvrir le gate de statut.

## Test Reporting & Coverage

- `./mvnw test` (module `dossier`) avec rapport Jacoco.
- **Preuve fraîche Phase 8 (2026-08-15) :** suite complète backend
  695/695 verts (0 échec, 0 erreur), `DossierApiTest` 56/56 (22 scénarios
  PATCH fields + 34 existants inchangés — 3 nouveaux scénarios ajoutés en
  Phase 8), `UpdateDossierFieldsUseCaseImplTest` 16/16 (1 nouveau
  scénario), `ArchitectureRulesTest` 3/3, Jacoco inchangé sur les classes
  touchées : `DossierFieldMerger` 100 %, `Dossier` 99.2 %, `SecurityAuditLog`
  100 %, `UpdateDossierFieldsUseCaseImpl` 95.1 %, `DossierController`
  92.2 %.

## Manual Testing
**What requires human validation?**

- Aucun — pas de composant UI livré dans cette feature (hors scope V1,
  voir design). Si un formulaire d'édition FE est ajouté ultérieurement,
  vérification manuelle à ajouter à ce moment-là.

## Performance Testing

- Sans objet — un seul `findById` + un seul `save`, même profil que
  `PATCH .../status`.

## Bug Tracking

- Suivi standard du projet (`TODO.md` / `DEFERRED-FEATURES.md`).
