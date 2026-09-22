---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: draft
created: 2026-09-22
updated: 2026-09-22
feature: outillage-dmn
phase1: 2026-09-22
stakeholder_decisions: 2026-09-22
todo_ref: TODO.md §66 · analysis doc/28-outillage-dmn.md
---

# Requirements & Problem Understanding — `outillage-dmn`

## Phase 1: New requirement

Cadrage 2026-09-22. Cinq décisions produit tranchées **une par une**
avec l’utilisateur (D-DMN-EVAL-SURFACE → D-DMN-HISTORY-RETENTION).
Aucune question produit ouverte. Les hypothèses **A-*** alignent le
cycle sur le code existant ; ce ne sont pas des **D-***.

Template README respecté. Surfaces disjointes : lot C (ClamAV) et
`enrichissement-reporting` (TODO §65).

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

- Un **seul cycle** livre les trois écarts du document 28.
- Évaluation ad hoc : **MCP + REST + bouton Tester** (D-DMN-EVAL-SURFACE = C).
- Version d’évaluation **optionnelle**, défaut = courante
  (D-DMN-EVAL-VERSION = B).
- Jeux de tests en **JSON et CSV** (D-DMN-TEST-FORMAT = C).
- Historique des versions + action **« Redéployer la version N »** avec
  confirmation (D-DMN-ROLLBACK-UX = A).
- **Pas de purge** applicative des vieilles versions
  (D-DMN-HISTORY-RETENTION = A).

### Non-goals

- Purge / rétention applicative des versions DMN.
- Portail externe, signature, inbox, alerting SLA / widgets / BI.
- Modification de ClamAV / `AttachmentScanPort`.
- Table applicative miroir de `ACT_DMN_*`.

## User Stories & Use Cases

- En tant qu’agent MCP, j’appelle `evaluate_decision` (inputs +
  `version?`) sans démarrer un process.
- En tant que `WORKFLOW_ADMIN`, j’utilise **Tester** dans
  `/admin/dmn-editor` (REST) sur la version courante ou une version
  choisie.
- En tant qu’agent ou admin, j’envoie un jeu de tests **JSON** ou
  **CSV** ; le système rapporte pass/fail par cas.
- En tant que `WORKFLOW_ADMIN`, je consulte l’historique et confirme
  **Redéployer la version N**.
- En tant qu’agent MCP, je liste les versions et déclenche le
  redéploiement (tool dont la description clarifie la sémantique).

### Edge cases

- Clé / version inconnue → 404.
- Inputs incompatibles → erreur métier claire.
- Jeu de tests vide → rejet validation.
- CSV mal formé / colonnes manquantes → 400 explicite.
- Redéploiement de la version déjà courante → comportement à figer
  en Phase 3 (Flowable réel) ; jamais une suppression.
- Évaluation = tables **déjà déployées** seulement (pas le brouillon
  brut sans allowlist).

## Success Criteria

- `evaluate_decision` (MCP) et REST d’évaluation : outputs pour version
  courante ou explicite.
- Bouton **Tester** dans l’éditeur : résultat affiché.
- `test_decision` (ou équivalent) accepte JSON **et** CSV ; rapport
  par cas.
- Liste de toutes les versions Flowable pour une clé.
- « Redéployer la version N » crée une nouvelle version courante ;
  confirmation UI ; aucun libellé « supprimer ».
- `./mvnw verify` + Vitest + Playwright (Tester + historique/
  redéploiement) verts.
- `GUIDE-UTILISATEUR.md` + golden OpenAPI à jour.

## Constraints & Assumptions

- Hexagonale : domaine pur ; Flowable / MCP / REST / Vue en adaptateurs.
- Sécurité : tables déployées déjà allowlistées ; Phase 3 reconfirme
  qu’on n’évalue pas de XML non déployé sans reparsing
  (A-EVAL-DEPLOYED-ONLY).
- RBAC : `TENANT_ADMIN` / `WORKFLOW_ADMIN` (+ `API_SERVICE` MCP).
- Pas de nouvelle variable d’environnement.
- Une instance = un tenant.

| ID | Hypothèse |
|---|---|
| **A-EVAL-DEPLOYED-ONLY** | Évaluation = tables déjà déployées. |
| **A-VERSION-DEFAULT** | `version` omise = dernière déployée. |
| **A-TEST-ON-EVAL** | Jeu de tests = boucle sur `EvaluateDecisionUseCase`. |
| **A-NO-APP-HISTORY-TABLE** | Historique via Flowable, pas de table maison. |
| **A-ROLLBACK-IS-REDEPLOY** | Rollback = redéploiement du XML d’une version N. |
| **A-SINGLE-CYCLE** | Trois sous-items roadmap dans la même feature. |
| **A-CSV-SHAPE** | Forme exacte des colonnes CSV (préfixes input_/expected_ ou en-tête libre) → Phase 3. |

## Questions & Open Items

Toutes tranchées le 2026-09-22 :

| ID | Décision |
|---|---|
| **D-DMN-EVAL-SURFACE** | **C** — MCP + REST + bouton Tester |
| **D-DMN-EVAL-VERSION** | **B** — version optionnelle, défaut = courante |
| **D-DMN-TEST-FORMAT** | **C** — JSON **et** CSV |
| **D-DMN-ROLLBACK-UX** | **A** — « Redéployer la version N » + confirmation |
| **D-DMN-HISTORY-RETENTION** | **A** — aucune purge dans ce cycle |

Points techniques → Phase 3 (design) : ports, DTOs, forme CSV exacte,
comportement redeploy version courante, noms finaux des tools MCP.

Aucune question produit restante. Prochaine étape : **Phase 2** (revue
requirements).
