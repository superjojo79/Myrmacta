---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: draft
created: 2026-08-22
updated: 2026-08-22
feature: modele-physique-consolide
design_ref: docs/ai/design/2026-08-22-feature-modele-physique-consolide.md
testing_ref: docs/ai/testing/2026-08-22-feature-modele-physique-consolide.md
---

# Project Planning & Task Breakdown — `modele-physique-consolide`

## Milestones

- [ ] M1: Rédaction `13-modele-physique.md` (ERD + 7 sections du design doc)
- [ ] M2: Intégration (`00-index.md`, liens croisés)
- [ ] M3: Vérification factuelle + clôture (TODO §25, FILE-ATTENTE.md)

## Task Breakdown

### M1: Rédaction du document

- [ ] T1.1 — Section intro + ERD mermaid (17 tables groupées par domaine, PK/FK logique vs DB)
  - Dépend de : rien (contenu déjà extrait en Phase 3)
  - Validation : les 17 `tableName:` des changelogs apparaissent toutes dans l'ERD (scénario testing doc)
- [ ] T1.2 — Section index uniques partiels critiques (tableau des 4 index + invariant)
  - Dépend de : T1.1
  - Validation : les 4 conditions `WHERE`/colonnes générées correspondent aux changelogs
- [ ] T1.3 — Section répartition tenant/`platform` + tables notables (`dossier_type_acl`, `tenant_connector_dek`, `outbox_event`)
  - Dépend de : rien
  - Validation : colonnes réelles, renvois vers décisions n° 20/n° 4 corrects
- [ ] T1.4 — Section tables Flowable (mention sans énumération) + section "écart de documentation corrigé"
  - Dépend de : rien
  - Validation : aucune mention active de `TenantRegistry`/contextes Liquibase séparés (D-MPC-SCOPE)

### M2: Intégration

- [ ] T2.1 — Ajouter la ligne 13 dans `00-index.md`
  - Dépend de : T1.1–T1.4 (document complet avant de le référencer)
  - Validation : tableau `00-index.md` cohérent, lien valide

### M3: Vérification et clôture

- [ ] T3.1 — Revue factuelle ligne par ligne contre les changelogs (Phase 7)
  - Dépend de : M1, M2
  - Validation : checklist "Vérification de contenu" du testing doc entièrement cochée
- [ ] T3.2 — Clore TODO §25 + mettre à jour `FILE-ATTENTE.md`
  - Dépend de : T3.1
  - Validation : revue manuelle, cohérence avec l'état réel du document

## Dependencies

Aucune dépendance externe — feature purement documentaire, un seul
fichier de contenu principal (`13-modele-physique.md`) plus les mises à
jour de clôture habituelles (`00-index.md`, `TODO.md`, `FILE-ATTENTE.md`).

## Timeline & Estimates

Feature documentaire courte — pas d'estimation en jours demandée.
Séquencement : M1 → M2 → M3, pas de parallélisation utile vu la taille.

## Risks & Mitigation

- **Risque** : le contenu extrait en Phase 3 (parsing structuré des
  changelogs) pourrait avoir raté un changeset ou mal interprété un
  bloc SQL brut (les index partiels sont des blocs `sql:` bruts, pas
  du Liquibase structuré — pas couverts par le parseur YAML). **Mitigation** :
  T3.1 revérifie chaque affirmation par une recherche directe dans les
  fichiers sources au moment de la rédaction, pas seulement en Phase 3.
- **Risque** : reproduire par erreur la confusion `TenantRegistry` que
  cette feature corrige justement. **Mitigation** : section dédiée
  "écart de documentation corrigé" (T1.4) qui explicite le changement
  plutôt que de simplement omettre la mention sans explication.

## Resources Needed

Aucune ressource externe/humaine supplémentaire — travail solo IA sur
la branche `claude/queue-next-feature-8r054i`.
