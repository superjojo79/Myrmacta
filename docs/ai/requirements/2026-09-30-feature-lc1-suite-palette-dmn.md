---
phase: requirements
title: Requirements — LC1-suite (liste des tables DMN dans la palette)
description: Reliquat de TODO §73 — lister les tables DMN déployées dans le panneau de la brique « Décision DMN »
status: reviewed
created: 2026-09-30
updated: 2026-09-30
feature: lc1-suite-palette-dmn
lot: LC1-suite
todo_ref: TODO.md §74 · ROADMAP.md LC1 (« reste en suite »)
---

# Requirements — `lc1-suite-palette-dmn`

## Phase 1 — problème

La brique « Décision DMN » (TODO §73) demande une **clé de table de décision** saisie à la main : aucune API REST ne
liste les tables déployées (seul l'outil MCP `list_decision_tables` le fait), donc la source `dmnTables` du catalogue
était traitée en « saisie libre » (`valueSourceResolver.ts`). Une faute de frappe n'est découverte qu'au déploiement.
Objectif D0 = C : l'utilisateur avancé ne doit plus avoir à connaître la clé par cœur.

**Utilisateurs** : intégrateur et utilisateur métier avec rôle `TENANT_ADMIN` ou `WORKFLOW_ADMIN` (rôles de l'éditeur BPMN).

## Objectifs

- G1. Le panneau de la brique « Décision DMN » propose la liste des tables **déployées du tenant** (dernière version).
- G2. Aucun contrat sensible : `{value, label}` uniquement (jamais XML ni règles).
- G3. Aucun changement de sémantique de déploiement : l'existence de la clé reste vérifiée au déploiement.
- G4. Même comportement de repli qu'aujourd'hui si la liste est indisponible (saisie libre, éditeur utilisable).

## Non-objectifs

Éditer la table depuis la palette, afficher les versions antérieures, ajouter `dmnTables` à la vérification d'existence
de l'adaptateur (déjà couverte au déploiement), nouvelle API REST publique de liste.

## Critères de succès

- S1. `GET /api/bpmn-building-blocks/references/dmnTables` renvoie, pour `TENANT_ADMIN` et `WORKFLOW_ADMIN`, une entrée
  par clé déployée (valeur = clé, libellé = « clé (vN) » avec N = dernière version), triée par clé.
- S2. `DOSSIER_MANAGER` ⇒ 403, anonyme ⇒ 401 (chaîne de sécurité réelle).
- S3. Le SPA alimente la liste déroulante depuis cet endpoint ; un échec réseau retombe en saisie libre.
- S4. Isolation tenant : seules les tables du tenant de l'instance sont listées.

## Décisions ouvertes tranchées (mandat de l'objectif « roadmap », journal : `docs/roadmap-goal/DECISIONS.md`)

D-LC1S-SOURCE, D-LC1S-LABEL, D-LC1S-EXISTS — voir le journal.
