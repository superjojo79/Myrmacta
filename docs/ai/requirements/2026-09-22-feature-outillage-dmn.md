---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
created: 2026-09-22
updated: 2026-09-23
feature: outillage-dmn
phase1: 2026-09-22
phase2: 2026-09-22
phase3: 2026-09-23
stakeholder_decisions: 2026-09-23
todo_ref: TODO.md §66 · analysis doc/28-outillage-dmn.md
---

# Requirements & Problem Understanding — `outillage-dmn`

## Phase 2: Review requirements

Revue 2026-09-22. Six décisions produit actées (cinq en Phase 1 +
**D-DMN-TEST-SURFACE** trouvée et tranchée en Phase 2). Aucune question
produit ouverte. Les hypothèses **A-*** alignent le cycle sur le code ;
ce ne sont pas des **D-***.

**Validé section par section**

| Section | Verdict |
|---|---|
| Template README | OK (problem, goals/non-goals, stories, success, constraints, questions) |
| Problem | Aligné code §61 + analyse 28 ; écarts confirmés (`findDeployedXml` = latest only) |
| Goals / non-goals | Cohérents avec les six **D-*** ; surfaces disjointes §65 / lot C |
| Stories | Complétées (upload JSON/CSV UI, bénéfice « afin de ») |
| Success | Mesurables ; Playwright étendu à l’upload de jeux de tests |
| Constraints | RBAC = même `@PreAuthorize` que déploiement DMN existant |
| Open items | Zéro **D-*** ouvert ; technique → Phase 3 |

**Écart trouvé en revue (corrigé) :** D-DMN-TEST-FORMAT ne fixait pas la
surface → **D-DMN-TEST-SURFACE = C** (MCP + REST + UI upload).

**Alternatives reconsidérées (non rouvertes) :** MCP-only pour evaluate
(écarté Phase 1 = C) ; JSON-only pour tests (écarté = C formats) ; purge
versions (écarté = A).

Lint feature OK (hors check branche `feature-*` vs `cursor/*` cloud).

## Problem Statement

**What problem are we solving?**

Le moteur DMN et l’éditeur `/admin/dmn-editor` sont livrés (TODO §61).
Un agent MCP peut déployer une table et un process qui l’invoque via
`serviceTask flowable:type="dmn"`. En revanche :

1. **Aucune évaluation ad hoc** hors d’un process actif.
2. **Aucun jeu de tests** (entrées → sorties attendues) sans démarrer
   un workflow.
3. **Historique opaque** : Flowable versionne (`ACT_DMN_*`), mais
   l’app n’expose que la dernière version ; pas de redéploiement d’une
   version antérieure.

Qui est touché : `WORKFLOW_ADMIN` / `TENANT_ADMIN`, agents M2M
(`API_SERVICE` via MCP).

Contournement actuel : process jetable + lecture des variables.

Source : [28-outillage-dmn.md](../../../analysis%20doc/28-outillage-dmn.md),
`ROADMAP.md` **1.1**, **1.2**, **1.3** / **5.1**.

## Goals & Objectives

**What do we want to achieve?**

### Primaire

- Un **seul cycle** livre les trois écarts du document 28.
- Évaluation ad hoc : **MCP + REST + bouton Tester**
  (D-DMN-EVAL-SURFACE = C), version **optionnelle**
  (D-DMN-EVAL-VERSION = B).
- Jeux de tests **JSON et CSV** (D-DMN-TEST-FORMAT = C) sur
  **MCP + REST + upload UI** (D-DMN-TEST-SURFACE = C).
- Historique des versions + **« Redéployer la version N »** avec
  confirmation UI (D-DMN-ROLLBACK-UX = A) ; MCP + REST pour les
  agents / SPA (A-HISTORY-SURFACE).
- **Pas de purge** applicative (D-DMN-HISTORY-RETENTION = A).

### Secondaire

- `GUIDE-UTILISATEUR.md` (Tester, jeux de tests, historique,
  redéploiement) ; golden OpenAPI ; entrée `CHANGELOG` à la PR
  d’implémentation.

### Non-goals

- Purge / rétention applicative des versions DMN.
- Évaluation d’un **brouillon** XML non déployé (A-EVAL-DEPLOYED-ONLY).
- Portail externe, signature, inbox, alerting SLA / widgets / BI.
- Modification de ClamAV / `AttachmentScanPort`.
- Table applicative miroir de `ACT_DMN_*`.
- `dryRun` sur evaluate/test (opérations non mutantes ; hors sujet).

## User Stories & Use Cases

**How will users interact with the solution?**

- En tant qu’agent MCP (`API_SERVICE`), je veux `evaluate_decision`
  (inputs + `version?`) **afin de** valider une règle sans démarrer un
  process.
- En tant que `WORKFLOW_ADMIN`, je veux un bouton **Tester** dans
  `/admin/dmn-editor` (version courante ou choisie) **afin de** vérifier
  un cas unitaire pendant l’édition.
- En tant qu’agent MCP ou admin (REST/UI), je veux soumettre un jeu
  **JSON ou CSV** **afin d’**obtenir un rapport pass/fail par cas.
- En tant que `WORKFLOW_ADMIN`, je veux uploader un fichier de tests
  dans l’éditeur **afin de** régresser une grille sans outil externe.
- En tant que `WORKFLOW_ADMIN`, je veux l’historique des versions et
  confirmer **Redéployer la version N** **afin de** revenir à un XML
  connu sans croire à une suppression.
- En tant qu’agent MCP, je veux lister les versions et redéployer
  (description tool = redéploiement) **afin d’**automatiser le même
  geste.

### Edge cases

- Clé / version inconnue → 404.
- Inputs incompatibles → erreur métier claire (pas 500 opaque).
- Jeu de tests vide → rejet validation.
- CSV / JSON mal formé → 400 explicite.
- Fichier de tests trop volumineux / trop de cas → borne Phase 3
  (A-TEST-CASE-CAP).
- Redéploiement version déjà courante → comportement Flowable à figer
  en Phase 3 ; jamais une suppression.
- Évaluation = tables **déjà déployées** seulement.
- `DOSSIER_MANAGER` / `DOSSIER_VIEWER` → 403 sur REST (même garde que
  déploiement DMN).

## Success Criteria

**How will we know when we're done?**

- MCP `evaluate_decision` + REST evaluate : outputs pour version
  courante ou explicite.
- Bouton **Tester** : résultat affiché (succès ou erreur métier).
- Jeux de tests JSON **et** CSV via MCP, REST, et upload UI ; rapport
  par cas.
- Liste de toutes les versions Flowable pour une clé (UI + REST + MCP).
- « Redéployer la version N » → nouvelle version courante ; confirmation
  UI obligatoire ; aucun libellé « supprimer ».
- `./mvnw verify` vert ; Vitest + Playwright verts (Tester, upload
  jeu de tests, historique/redéploiement).
- `GUIDE-UTILISATEUR.md` + golden OpenAPI à jour.

## Constraints & Assumptions

**What limitations do we need to work within?**

- Hexagonale : domaine pur ; Flowable / MCP / REST / Vue en adaptateurs.
- RBAC REST : `@PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN')")`
  — miroir `DmnDecisionDeploymentController` actuel ; MCP = `API_SERVICE`
  (A-RBAC-ALIGN).
- Sécurité : Phase 3 reconfirme qu’aucun chemin n’évalue du XML hors
  déploiement allowlisté.
- Pas de nouvelle variable d’environnement.
- Une instance = un tenant.
- `findDeployedXml` actuel = latest only → extension versionnée en
  design.

| ID | Hypothèse |
|---|---|
| **A-EVAL-DEPLOYED-ONLY** | Évaluation = tables déjà déployées. |
| **A-VERSION-DEFAULT** | `version` omise = dernière déployée. |
| **A-TEST-ON-EVAL** | Jeu de tests = boucle sur `EvaluateDecisionUseCase`. |
| **A-NO-APP-HISTORY-TABLE** | Historique via Flowable, pas de table maison. |
| **A-ROLLBACK-IS-REDEPLOY** | Rollback = redéploiement du XML de la version N. |
| **A-SINGLE-CYCLE** | Trois sous-items roadmap dans la même feature. |
| **A-CSV-SHAPE** | Forme exacte des colonnes CSV → Phase 3. |
| **A-HISTORY-SURFACE** | Historique + redéploiement = MCP + REST + UI (SPA → REST). |
| **A-RBAC-ALIGN** | Mêmes rôles que le déploiement DMN existant. |
| **A-TEST-CASE-CAP** | 100 cas (D-TECH-TEST-CAP = A). |
| **A-MCP-CONFIRM** | La confirmation D-DMN-ROLLBACK-UX s’applique à l’UI ; MCP exécute directement. |

## Questions & Open Items

**What do we still need to clarify?**

Décisions produit :

| ID | Décision | Quand |
|---|---|---|
| **D-DMN-EVAL-SURFACE** | **C** — MCP + REST + bouton Tester | Phase 1 |
| **D-DMN-EVAL-VERSION** | **B** — version optionnelle, défaut = courante | Phase 1 |
| **D-DMN-TEST-FORMAT** | **C** — JSON **et** CSV | Phase 1 |
| **D-DMN-ROLLBACK-UX** | **A** — « Redéployer la version N » + confirmation | Phase 1 |
| **D-DMN-HISTORY-RETENTION** | **A** — aucune purge dans ce cycle | Phase 1 |
| **D-DMN-TEST-SURFACE** | **C** — MCP + REST + upload UI | Phase 2 |

Décisions techniques : design Phase 3 (`status: reviewed`), 2026-09-23.
Aucune question produit ou technique ouverte. Phases 4 à 9 exécutées
le 2026-09-23.
