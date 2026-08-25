---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
created: 2026-08-25
updated: 2026-08-25
feature: derive-documentaire
phase1: 2026-08-25
phase2: 2026-08-25
stakeholder_decisions: 2026-08-25
todo_ref: TODO.md §45 · FILE-ATTENTE.md (Prochain — chore, pas de décision produit)
revue_ref: REVUE-ALIGNEMENT-SECURITE.md §3.3
---

# Requirements & Problem Understanding — `derive-documentaire`

## Phase 1: Requirements

> Feature **doc pure** : aucun comportement, schéma, API ni UI.
> Décisions déjà actées dans TODO §45 / FILE-ATTENTE (pas d'arbitrage produit
> restant). Code = source de vérité. Ne pas élargir le V1.

## Phase 2: Review requirements

Même session que la Phase 1 : `status: reviewed` et décisions D-* figées ci-dessous.

## Problem Statement

La revue post-implémentation 2026-08-25
([REVUE-ALIGNEMENT-SECURITE.md](../../../REVUE-ALIGNEMENT-SECURITE.md) §3.3)
constate que le **code suit les décisions 13–15, 17, 27**, mais que plusieurs
docs décrivent encore l'ancien modèle (tenant résolu par requête OIDC) ou un
périmètre non livré (DMN activé, catalogue connecteurs complet, MinIO en dur).

Conséquence : un agent ou un humain relit une spec fausse et peut
réintroduire le tenant-par-requête ou « livrer » Kafka/DMN/SERVICE HTTP.

**Déjà clos hors de cette feature :** `com.monapp` dans
`analysis doc/01-architecture-hexagonale.md` (PR #72). Ne pas replanifier.

**Hors de cette feature :** TODO §40–44 (feature candidate
`durcissement-securite-post-revue`, arbitrages A/B/C ouverts) ; TODO §46
(ArchUnit couche `application`).

## Goals & Objectives

### Primary goals

Aligner les docs listées sur le code **sans changer le code métier** :

1. `README.md` — tenant instance (`app.tenant.id`) ; claim OIDC = garde ;
   connecteurs / DMN / stockage / jsoup / diagrammes.
2. `analysis doc/12-autorisation-conformite.md` — retirer « cockpit Flowable »
   pour `WORKFLOW_ADMIN` (hors V1 ; éditeur `/admin/bpmn-editor`).
3. jsoup `1.22.x` → `1.23.1` (`README`, `00-index`, `07-generation-documents`)
   pour coller à `pom.xml`.
4. `application.yml` L1–3 — commentaire autoconfig périmé.
5. `analysis doc/06-connecteurs.md` — distinguer **cible d'analyse** vs
   **V1 livré** (ne pas supprimer le catalogue cible, ne pas implémenter
   `scheduler`/`smtp`/`http-rest` SERVICE).
6. `context-path` : rester commenté dans le yaml ; README / `11-frontend.md`
   ne doivent pas le présenter comme actif aujourd'hui (décision 14 = cible
   ops, point 31).

### Non-goals

- Activer le moteur DMN Flowable, Kafka, scheduler, smtp, `http-rest` SERVICE.
- Trancher TODO §40–44 / §46.
- Refactor code, tests, OpenAPI, SPA.
- Purger toutes les mentions historiques « MinIO » dans les docs de purge
  (`12` §22, `04` `storageRef`) — hors liste §45 ; éventuellement une
  parenthèse « port S3, pas un couplage MinIO » seulement si on touche
  déjà le fichier pour un autre item de la liste.

## User Stories

- En tant qu'**agent / développeur**, je lis le README et j'applique la
  décision 13 (instance = tenant), pas un `TenantContext` par requête.
- En tant qu'**admin produit**, je vois quels connecteurs sont **livrés**
  vs **cible analyse**, pour ne pas attendre Kafka/DMN en V1.

## Success Criteria

- [x] `README.md` ne dit plus que le tenant est résolu depuis OIDC à chaque
      requête ; le claim est décrit comme garde d'appartenance.
- [x] README / `00-index` ne présentent plus DMN, Kafka, scheduler, ERP,
      MinIO-en-dur comme livrés V1.
- [x] `12-autorisation-conformite.md` : plus de « cockpit Flowable » comme
      capacité `WORKFLOW_ADMIN`.
- [x] jsoup documenté `1.23.1` (ou `${jsoup.version}`) là où une version
      était citée.
- [x] `application.yml` : commentaire d'en-tête décrit les exclusions
      Flowable **actuelles**, pas « until real adapters land ».
- [x] `06-connecteurs.md` catalogue : colonne ou note **V1 livré**.
- [x] `grep` de contrôle (testing doc) vert ; `git diff` sans `.java` /
      `.vue` / changelog.

## Constraints & Assumptions

- Source de vérité : code + décisions 13–15, 17, 27 +
  `DEFERRED-FEATURES.md` (cockpit hors V1, `http-rest` SERVICE hors V1).
- Convention Cloud Agent : branche `cursor/derive-documentaire-a95d`
  (pas `feature-derive-documentaire`). Lint git `[MISS]` accepté.
- `npx ai-devkit@latest task` indisponible dans cette install.
- Pas de worktree isolé (workspace cloud unique).

## Questions & Open Items

**Aucun.** TODO §45 : « chore, sans arbitrage produit ». Décisions D-\*
figées ci-dessous (issues de la revue, pas ré-ouvertes) :

| ID | Décision | Choix |
|---|---|---|
| D-SOURCE | Qui gagne en cas d'écart | **Code** (et décisions actées) |
| D-V1 | Élargir le V1 pour coller au marketing | **Non** |
| D-06 | Catalogue `06` | Annoter V1 vs cible, **ne pas** effacer la cible |
| D-MONAPP | `com.monapp` dans `01` | Déjà clos PR #72 — hors scope |

## Alternatives (Phase 1)

| Approche | Trade-off | Verdict |
|---|---|---|
| A — Corriger les docs listées (chore) | Alignement immédiat, pas de code | **Retenue** (FILE-ATTENTE « Prochain ») |
| B — Attendre `durcissement-securite-post-revue` | Les docs fausses restent le temps des arbitrages 40–44 | Écartée — §45 n'a pas besoin d'arbitrage |
| C — Réécrire toute `analysis doc/` | Trop large, hors §45 | Écartée |
