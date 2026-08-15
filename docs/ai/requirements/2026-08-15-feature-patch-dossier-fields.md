---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
created: 2026-08-15
updated: 2026-08-15
feature: patch-dossier-fields
stakeholder_decisions: 2026-08-15
phase2: 2026-08-15
todo_ref: DEFERRED-FEATURES.md §1.4 (PATCH fields dossier) / TODO §24
---

# Requirements & Problem Understanding

## Problem Statement
**What problem are we solving?**

Un dossier peut être créé avec des champs dynamiques (`Dossier.fields`,
validés contre `DossierType.fieldDefinitions`) et son **statut** peut être
changé (`PATCH /api/dossiers/{id}/status`), mais il n'existe **aucun moyen
de corriger ses champs après création**. Une erreur de saisie (référence
externe erronée, montant mal renseigné, valeur `SELECT` invalide corrigée
après coup) ne peut être réparée que par accès direct base de données.

- **Affectés :** `DOSSIER_MANAGER` / `TENANT_ADMIN` (gestion quotidienne des
  dossiers), tout utilisateur EDIT sur un type de dossier.
- **Situation actuelle :** `CreateDossierUseCase` (création, jeu de champs
  complet) et `UpdateDossierStatusUseCase` (statut, verrou optimiste
  `expectedVersion`) existent ; aucun équivalent pour `fields`.
- **Réf. :** `DEFERRED-FEATURES.md` §1.4 ("PATCH fields dossier — Extension
  API (point 24)") ; `TODO.md` point 24 (contrat d'API incomplet) ;
  `Dossier.withFields()` (déjà présent, utilisé aujourd'hui uniquement par
  `DossierFieldRedactor` côté lecture).

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

- **`PATCH /api/dossiers/{id}/fields`** — met à jour un sous-ensemble des
  champs dynamiques d'un dossier existant, par **fusion** (D-MERGE) : seuls
  les champs présents dans le body sont modifiés, les autres restent
  inchangés.
- Une valeur `null` dans le body **efface** le champ correspondant
  (D-CLEAR) — retiré du jeu de champs après fusion, revalidé comme tel
  (échoue en 400 si le champ est `required`).
- Le jeu de champs **fusionné** (existant + patch) est revalidé dans son
  intégralité contre `DossierType.fieldDefinitions` — même
  `DossierFieldValidator` que la création (types, `required`, `SELECT`
  options, champs inconnus selon `allowUnknownFields`), pas de nouvelle
  logique de validation.
- Édition bloquée si le dossier est `CLOTURE` ou `ARCHIVE` (D-STATUS-GATE)
  — 409, cohérent avec l'esprit de `DossierStatusTransitions` (un dossier
  clos ne doit plus voir son contenu métier changer).
- Verrou optimiste `expectedVersion` (optionnel), même contrat que
  `PATCH .../status`.
- ACL : `assertCanEdit` sur le dossier (même contrôle que `PATCH
  .../status` et la création) — aucune nouvelle règle d'autorisation.

### Secondary goals

- Réutiliser `Dossier.withFields()` (déjà présent) — aucun nouveau champ
  domaine, aucune migration Liquibase.
- Couverture tests alignée Jacoco / Vitest projet (≥ 80 % zones touchées).
- Mettre à jour `DEFERRED-FEATURES.md` §1.4 et `TODO.md` point 24 une fois
  livré.

### Non-goals

- Historique / audit des modifications de champs (qui a changé quoi, quand)
  — hors scope V1, pas de table d'audit dédiée.
- Validation de transitions métier sur la valeur d'un champ (ex. un champ
  `SELECT` ne peut passer de "A" à "C" sans passer par "B") — hors scope,
  seule la validation structurelle (`DossierFieldValidator`) s'applique.
- Édition en masse (plusieurs dossiers en un appel).
- Exposer un endpoint de suppression de dossier ou de purge de champs
  (`DELETE`) — seule la fusion avec `null` permet d'effacer un champ.
- Changer la sémantique de `GET /api/dossiers/{id}` (redaction des champs
  `sensitive`, déjà couverte par `DossierFieldRedactor`) — le PATCH écrit
  la valeur réelle, la lecture continue de la redacter pour les non-admin,
  comportement déjà en place et inchangé.
- Réouvrir un dossier `CLOTURE`/`ARCHIVE` pour l'éditer — si un correctif
  est nécessaire sur un dossier clos, il doit d'abord repasser par une
  transition de statut valide (hors scope de cette feature).

## Stakeholder decisions

| Id | Décision | Choix |
|---|---|---|
| **D-MERGE** | Sémantique PATCH | **Fusion** — seuls les champs du body sont modifiés, le reste du jeu de champs existant est conservé tel quel |
| **D-CLEAR** | Valeur `null` dans le body | **Efface le champ** (retire la clé après fusion) — convention JSON Merge Patch (RFC 7396) ; revalidé comme absent (400 si `required`) |
| **D-STATUS-GATE** | Édition sur dossier `CLOTURE`/`ARCHIVE` | **Bloquée** — 409 `DossierConflictException` |
| **D-ENDPOINT** | Forme de l'API | **`PATCH /api/dossiers/{id}/fields`** dédié, symétrique à `PATCH .../status` déjà existant |
| **D-EMPTY-BODY** *(Phase 2, résolu par précédent)* | Body vide (`fields` absent/`{}`) | **400** — `IllegalArgumentException`, même convention que le reste de l'API (`targetDossierId is required`, `status is required`, etc.) ; une requête sans effet net n'est pas un PATCH valide |
| **D-NOOP-VERSION** *(Phase 2, résolu par précédent)* | Version/`updatedAt` sur un merge sans changement net | **Toujours avancés** — aucun PATCH existant (`.../status`) ne détecte les no-op ; ajouter une comparaison profonde du jeu fusionné serait une complexité nouvelle pour un bénéfice marginal |
| **D-SENSITIVE-AUDIT** *(Phase 2)* | Écriture d'un champ `sensitive` | **A** — EDIT suffit (cohérent avec la création, aucune restriction supplémentaire), **et** un événement `SECURITY_AUDIT` dédié est émis (nom de champ + dossier + auteur, jamais la valeur) quand un champ `sensitive` change — nouvelle catégorie d'événement, pas un refus comme les événements existants |

### Alternatives écartées

| Sujet | Retenu | Écarté |
|---|---|---|
| Sémantique PATCH | Fusion | Remplacement complet — casse la sémantique HTTP PATCH standard, oblige le FE à toujours détenir l'état complet des champs |
| Effacement de champ | `null` efface | `null` rejeté en 400 — aucune voie pour vider un champ optionnel rempli par erreur, jugé trop restrictif |
| Édition dossier clos | Bloquée (409) | Autorisée à tout statut — risque de divergence entre l'état du dossier et son historique de clôture |
| Forme API | Endpoint dédié `.../fields` | `PATCH /api/dossiers/{id}` générique (status+fields mélangés) — casse la symétrie avec l'endpoint status existant, mélange deux préoccupations |
| Écriture champ sensitive | EDIT + audit dédié | Restreindre à `TENANT_ADMIN` — rejeté, incohérent avec la création qui n'a pas cette restriction, casse la symétrie EDIT ; EDIT seul sans traçabilité — rejeté, aucune visibilité si un champ sensible change |

## User Stories & Use Cases
**How will users interact with the solution?**

1. **En tant que** gestionnaire, **je veux** corriger la valeur d'un champ
   mal saisi à la création, **afin de** ne plus devoir passer par un accès
   base de données direct.
2. **En tant que** gestionnaire, **je veux** effacer un champ optionnel
   rempli par erreur, **afin de** revenir à un état propre sans devoir
   connaître une valeur de remplacement arbitraire.
3. **En tant que** utilisateur sans droit EDIT sur le type du dossier,
   **je ne veux pas** pouvoir modifier ses champs, **afin de** préserver le
   modèle ACL déjà en place partout ailleurs.
4. **En tant que** gestionnaire, **je veux** être bloqué si je tente
   d'éditer un dossier déjà clôturé, **afin de** ne pas altérer un dossier
   dont le cycle de vie métier est terminé.

### Edge cases

- Body vide (`{}` ou `fields` absent) → 400 (D-EMPTY-BODY).
- Champ inconnu dans le body, `allowUnknownFields = false` → 400 (même
  comportement que la création).
- `expectedVersion` fourni et différent de `dossier.version()` → 409
  (même comportement que `PATCH .../status`).
- Dossier introuvable ou d'un autre tenant → 404.
- Actor sans EDIT sur le type → 403.
- Dossier `CLOTURE`/`ARCHIVE` → 409 (D-STATUS-GATE), même si l'actor a
  EDIT et fournit un `expectedVersion` correct — le gate de statut est
  vérifié indépendamment du verrou optimiste.
- Effacer (`null`) un champ `required` → 400, même message d'erreur que
  "champ requis manquant" à la création.
- Champ `SELECT` avec une valeur hors `options` → 400 (réutilise
  `DossierFieldValidator`, comportement déjà existant).

## Success Criteria
**How will we know when we're done?**

- [x] `PATCH /api/dossiers/{id}/fields` fusionne le body dans les champs
      existants et renvoie le dossier mis à jour (200).
- [x] `null` efface un champ ; le jeu fusionné est revalidé dans son
      intégralité (`required`, types, `SELECT`, `allowUnknownFields`).
- [x] 409 si le dossier est `CLOTURE`/`ARCHIVE`, ou si `expectedVersion`
      ne correspond pas à `dossier.version()`.
- [x] 403 si l'actor n'a pas EDIT sur le type du dossier ; 404 si le
      dossier n'existe pas ou est d'un autre tenant.
- [x] Body vide/`fields` absent → 400 (D-EMPTY-BODY).
- [x] Événement `SECURITY_AUDIT` émis (nom de champ + dossier + auteur,
      jamais la valeur) quand un champ `sensitive` change de valeur
      (D-SENSITIVE-AUDIT).
- [x] Régression : `GET`/`POST`/`PATCH .../status`/`GET .../elements`/
      `GET .../relations` inchangés ; redaction des champs sensibles à la
      lecture toujours appliquée après un PATCH.
- [x] `./mvnw test` (zones touchées) + `frontend` Vitest/Playwright OK.
- [x] `DEFERRED-FEATURES.md` §1.4 et `TODO.md` point 24 mis à jour.

## Constraints & Assumptions
**What limitations do we need to work within?**

- Hexagone : `UpdateDossierFieldsUseCase` (port `in`) + `UpdateDossierFieldsUseCaseImpl`
  (domaine `dossier.usecase`) — même patron que
  `UpdateDossierStatusUseCaseImpl` ; aucune nouvelle table, aucun nouveau
  port `out` (réutilise `DossierRepository.save`).
- ACL réutilise `DossierAccessPolicy.assertCanEdit` existant — aucune
  nouvelle règle.
- Validation réutilise `DossierFieldValidator` existant tel quel (déjà
  conçu pour valider un jeu de champs complet) — le use case construit le
  jeu fusionné puis appelle `validate()`, sans modifier le validateur.
- H2 mode PostgreSQL pour les IT (comme le reste de `dossiers`).
- **Assumption A1 :** un dossier `DRAFT` reste éditable (seuls
  `CLOTURE`/`ARCHIVE` bloquent, pas `SUSPENDU`) — un dossier suspendu peut
  encore avoir besoin d'un correctif de champ pendant l'instruction.
- **Assumption A2 :** le type du dossier n'a pas besoin d'être `active`
  pour éditer un dossier existant de ce type (seule la création d'un
  *nouveau* dossier exige un type actif, cohérent avec le comportement
  déjà en place pour la liste/lecture).
- **Assumption A3 :** la réponse HTTP renvoie le `DossierResponse` complet
  (même forme que `GET`/`POST`), pas un format allégé — cohérent avec
  `PATCH .../status` qui renvoie déjà `DossierResponse`.

## Questions & Open Items
**What do we still need to clarify?**

### Clos Phase 2

| Id | Clarification |
|---|---|
| Q1 | Body vide/`fields` absent → 400 (D-EMPTY-BODY, résolu par précédent) |
| Q2 | Version/`updatedAt` toujours avancés, pas de détection de no-op (D-NOOP-VERSION, résolu par précédent) |
| Q3 | Champ `sensitive` : EDIT suffit + événement `SECURITY_AUDIT` dédié à la modification (D-SENSITIVE-AUDIT) |

### Reporté hors feature

- Historique/audit complet des modifications de champs (qui a changé
  quoi, valeur avant/après) — seul un événement `sensitive_field_changed`
  (métadonnées, jamais la valeur) est ajouté ; pas de table d'audit
  générale.
- Formulaire d'édition UI — API seule en V1 (voir design, Component
  Breakdown).
