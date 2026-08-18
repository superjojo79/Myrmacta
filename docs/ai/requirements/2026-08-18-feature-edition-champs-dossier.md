---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
created: 2026-08-18
updated: 2026-08-18
feature: edition-champs-dossier
phase1: 2026-08-18
phase2: 2026-08-18
stakeholder_decisions: 2026-08-18
todo_ref: follow-up UI de patch-dossier-fields (PR #50) · DossierDetailView readonly
---

# Requirements & Problem Understanding — `edition-champs-dossier`

> Identifiée comme prochaine feature après clôture de `preview-documents`
> (PR #52). Follow-up UI explicite de `patch-dossier-fields` (API livrée,
> formulaire reporté hors scope V1 de cette feature).

## Problem Statement
**What problem are we solving?**

Un gestionnaire peut **créer** un dossier avec des champs dynamiques et
**changer son statut** depuis le détail, mais il **ne peut pas corriger
les champs** après création. L'API `PATCH /api/dossiers/{id}/fields`
existe (PR #50) ; le SPA n'a ni client, ni bouton. `DossierDetailView`
force `DynamicFields` en `readonly`. Une erreur de saisie n'est
réparable que hors UI.

- **Affectés :** `DOSSIER_MANAGER` / `TENANT_ADMIN` / `WORKFLOW_ADMIN`
  (même ensemble que `canEditDossier` déjà utilisé pour éléments et
  relations).
- **Situation actuelle :** `updateDossierStatus` + bouton « Enregistrer
  statut » ; `DynamicFields` éditable seulement à la création.
- **Réf. :** `docs/ai/requirements/2026-08-15-feature-patch-dossier-fields.md`
  (Formulaire d'édition UI reporté) ; design patch « Frontend hors scope
  V1 ».

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

- **Éditer les champs** depuis le détail dossier, via
  `PATCH /api/dossiers/{id}/fields` (contrat existant, inchangé).
- **Fusion (D-DIFF)** : n'envoyer que les champs réellement modifiés —
  jamais le formulaire entier — pour ne pas écraser un champ `sensitive`
  absent du GET (redaction serveur : la clé est **retirée**, pas masquée).
- **Verrou statut (D-LOCK)** : lecture seule + pas de bouton d'enregistrement
  si le dossier est `CLOTURE` ou `ARCHIVE` (l'API répondrait 409).
- **ACL UI (D-AUTHZ)** : même règle que « Ajouter » / « Lier » —
  `canEditDossier` ; `DOSSIER_VIEWER` reste en lecture seule.

### Secondary goals

- Réutiliser `DynamicFields` (mode édition déjà utilisé à la création).
- `expectedVersion` = `dossier.version` courant, comme `patchStatus`.
- Vitest + Playwright (contrat API consommé par le SPA).
- Mettre à jour `DEFERRED-FEATURES.md` §1.4 (follow-up UI PATCH fields).

### Non-goals

- Aucun changement backend / OpenAPI / Liquibase.
- Édition du libellé ou d'autres attributs hors `fields`.
- Historique des modifications de champs.
- Reveal des champs `sensitive` pour un non-`TENANT_ADMIN` (redaction
  serveur inchangée).
- Rétention / purge (§21–22), OpenAPI (§24), observabilité (§29).
- Édition depuis le panneau dossier de la visualisation workflow.

## Alternatives considérées

| Approche | Trade-off | Verdict |
|---|---|---|
| Brancher `DynamicFields` inline dans la vue vs composant dédié | Vue déjà composition de listes/dialogs | **Composant `DossierFieldsEditor`** — vue = surface de composition |
| PATCH du formulaire entier vs diff | Entier écraserait les clés redactées | **Diff (D-DIFF)** |
| Defaults à l'édition (comme création) vs pas de defaults | Defaults rempliraient un champ sensitive redacté | **`applyDefaults=false` en édition (D-DEFAULTS)** |

## User Stories & Use Cases

1. **En tant que** `DOSSIER_MANAGER`, **je veux** corriger un champ sur le
   détail, **afin de** ne plus dépendre d'un accès base.
2. **En tant que** `DOSSIER_VIEWER`, **je veux** voir les champs en lecture
   seule sans bouton d'enregistrement, **afin de** ne pas tenter un PATCH 403.
3. **En tant que** gestionnaire sur un dossier `CLOTURE`, **je veux** que
   les champs restent verrouillés, **afin de** rester aligné avec le 409 API.
4. **En tant que** non-admin, **je ne veux pas** que l'enregistrement
   réécrive un champ `sensitive` que le GET m'a retiré.

### Edge cases

- Aucun champ modifié → pas d'appel (évite 400 D-EMPTY-BODY).
- Champ optionnel vidé → `null` dans le patch (D-CLEAR).
- Champ `sensitive` absent du snapshot → omis tant que l'utilisateur n'a
  pas saisi une nouvelle valeur.
- 409 / 400 / 403 → `ProblemDetail.detail` affiché, comme le statut.
- Type inactif : édition toujours possible (assumption A2 de
  `patch-dossier-fields`).

## Success Criteria
**How will we know when we're done?**

- [x] Le détail affiche un bouton « Enregistrer les champs » pour
      `canEditDossier` sur un dossier non clos.
- [x] Un changement de champ envoie `PATCH .../fields` avec uniquement
      ce champ + `expectedVersion`.
- [x] Viewer : pas de bouton, `DynamicFields` readonly.
- [x] `CLOTURE`/`ARCHIVE` : readonly, pas de bouton.
- [x] Champ `sensitive` redacté non envoyé si non touché.
- [x] Erreur API affichée (ProblemDetail).
- [x] Vitest + Playwright verts ; mocks E2E `PATCH .../fields`.
- [x] `DEFERRED-FEATURES.md` mis à jour.

## Constraints & Assumptions

- SPA même origine, CSRF cookie (décisions n° 15 / 27).
- Hexagone : aucun code domaine/Java dans cette feature.
- **A1 :** `WORKFLOW_ADMIN` peut éditer (déjà dans `canEditDossier` ;
  backend `assertCanEdit` le contourne comme les autres writes).
- **A2 :** un champ `FILE` reste une saisie texte (référence), comme à la
  création — pas d'upload ici.
- Task tracing `ai-devkit task` indisponible (`unknown command 'task'`).

## Stakeholder decisions

| Id | Décision | Choix |
|---|---|---|
| **D-SURFACE** | Lieu UI | `DossierDetailView` + `DossierFieldsEditor` |
| **D-AUTHZ** | Qui édite | `canEditDossier` existant (TA \| WA \| DM) |
| **D-LOCK** | Dossier clos | Readonly, pas de save |
| **D-DIFF** | Payload | Uniquement champs dont la valeur a changé |
| **D-CLEAR-UI** | Champ vidé | `null` (D-CLEAR API) |
| **D-DEFAULTS** | Defaults `DynamicFields` | **false** en édition d'un dossier existant |
| **D-EMPTY-UI** | Diff vide | Pas d'appel HTTP |
| **D-VERSION** | Optimistic lock | `dossier.version` comme `patchStatus` |

## Questions & Open Items

### Clos Phase 2 (assumptions nommées, agent autonome)

Toutes les décisions D-* ci-dessus. Aucune question produit ouverte :
le contrat API est déjà figé par `patch-dossier-fields`.

### Reporté hors feature

- Édition du libellé.
- Panneau visualisation workflow.
- Reveal / re-saisie guidée d'un champ sensitive redacté (l'utilisateur
  peut poser une **nouvelle** valeur, pas relire l'ancienne).
