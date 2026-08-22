---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: done
created: 2026-08-22
updated: 2026-08-22
feature: modele-physique-consolide
design_ref: docs/ai/design/2026-08-22-feature-modele-physique-consolide.md
planning_ref: docs/ai/planning/2026-08-22-feature-modele-physique-consolide.md
---

# Implementation Guide — `modele-physique-consolide`

7 tâches planifiées (T1.1–T3.2) exécutées sur 3 milestones (M1
rédaction du document, M2 intégration à l'index, M3 vérification
factuelle + clôture). Feature strictement documentaire — aucun test
automatisé applicable (pas de code touché), remplacé par une revue
factuelle ligne par ligne contre les changelogs Liquibase (Phase 7).
Statut : **terminée et revue** ; prochaine étape `dev-review`
(Phase 9, close dans ce document).

## Development Setup

Aucun — pas de dépendance, pas d'environnement à configurer. Un seul
fichier markdown produit.

## Code Structure

- `analysis doc/13-modele-physique.md` (nouveau) — le document lui-même,
  7 sections (intro, ERD mermaid, index uniques partiels, répartition
  tenant/`platform`, tables notables, tables Flowable, écart de
  documentation corrigé).
- `analysis doc/00-index.md` (modifié) — ligne 13 ajoutée au tableau
  des fonctionnalités.

## Implementation Notes

### Core Features

- **ERD mermaid** — 17 tables réelles groupées par domaine (dossiers,
  workflow, connecteurs, documents, notifications, rétention/purge),
  syntaxe `erDiagram`. Seules les 2 FK réellement contraintes en base
  (`dossier_relation` → `dossier`, ×2) sont rendues comme relations
  mermaid ; les FK logiques sont annotées en commentaire de colonne
  (`"FK logique -> TABLE.colonne"`) plutôt que comme des relations
  mermaid, pour ne pas laisser croire à une contrainte DB inexistante.
- **Index uniques partiels critiques** — 4 index documentés avec leur
  invariant métier exact, pas juste leur définition SQL : chacun
  répond à la question « que casserait-on si on le supprimait ? ».
- **Écart de documentation corrigé** — section dédiée expliquant
  pourquoi `TenantRegistry` et les contextes Liquibase séparés ne sont
  pas documentés comme actifs, avec citation de la note de mise à jour
  de la décision n° 1, plutôt que de simplement les omettre sans
  explication (un futur lecteur du TODO seul aurait pu retomber sur la
  même confusion).

### Patterns & Best Practices

- **Le DDL exécutable reste l'unique source de vérité** — le document
  renvoie systématiquement aux changelogs (`src/main/resources/db/changelog/changes/*.yaml`)
  plutôt que de dupliquer leur contenu SQL, pour éviter toute
  divergence à un futur changeset.
- **Contenu extrait par parsing structuré, pas résumé de mémoire** —
  script Python ad hoc (Phase 3) parsant les 12 fichiers YAML de
  changelog pour extraire tables/colonnes/FK/index, plus `grep` ciblé
  pour les blocs SQL bruts (index partiels, non couverts par le
  parseur Liquibase structuré). Revérifié en Phase 7 par comparaison
  directe (`diff`) entre la liste de tables du document et
  `grep tableName:` sur les changelogs.

## Design Deviations

Aucune — le design (Phase 3) a été suivi tel quel, sans écart en
implémentation. Les deux écarts réels de cette feature ont été trouvés
et corrigés **avant** l'implémentation (Phase 1, sur le périmètre
lui-même — TODO §25 documentait des mécanismes obsolètes), pas pendant
ou après.

## Integration Points

Aucune API, aucun endpoint, aucune base de données modifiée — feature
purement documentaire.

## Error Handling

Non applicable.

## Performance Considerations

Non applicable.

## Security Notes

Aucun changement de sécurité — aucun code touché. Le document
mentionne le mécanisme de chiffrement des DEK (`tenant_connector_dek`,
décision n° 4) et l'ACL par type de dossier (`dossier_type_acl`,
décision n° 20) mais ne modifie ni l'un ni l'autre.

## Phase 9 — revue finale (2026-08-22)

Revue holistique (au-delà de la vérification factuelle de Phase 7) :

- **Alignement design** : les 6 success criteria du requirements doc
  et la structure en 7 sections du design doc sont tous couverts dans
  `13-modele-physique.md` (tableau de traçabilité du design doc).
  Aucun écart.
- **Cohérence avec les docs existantes** : style et ton alignés sur
  les 12 docs `analysis doc/` existants (citations de décisions
  numérotées, renvois croisés, pas de duplication de DDL déjà pratiqué
  nulle part ailleurs dans ces docs). Numérotation 13 cohérente avec
  la séquence 01–12 déjà en place.
- **Duplication** : aucune — c'est la première vue consolidée de ce
  type dans le projet (confirmé par la lecture des 12 docs existants
  en Phase 1, aucun ne couvre l'inventaire physique complet).
- **Rupture de contrat** : aucune — zéro fichier de code touché,
  confirmé par `git diff --stat` à plusieurs reprises (Phase 7, ici).
- **Sécurité rollback** : triviale — un seul fichier markdown ajouté,
  une ligne ajoutée dans `00-index.md`, aucune migration, aucune
  dépendance.
- **Fidélité au TODO d'origine** : l'écart trouvé en Phase 1
  (`TenantRegistry`/contextes Liquibase obsolètes) aurait pu être
  silencieusement contourné (documenter comme si de rien n'était, ou
  omettre sans expliquer) — traité explicitement à la place, avec une
  section dédiée dans le livrable qui trace pourquoi l'écart existe et
  où il est tranché (décision n° 1, décision n° 13), suivant le
  précédent de `fiabilisation-tests-plateforme` (TODO §26, même type
  d'écart trouvé et traité de la même façon).

**Checklist finale :** design ✅ · pas de gap logique ✅ (feature
documentaire, aucune logique à vérifier au-delà de l'exactitude
factuelle déjà revue en Phase 7) · sécurité ✅ (non applicable, aucun
code) · points d'intégration vérifiés ✅ (aucun) · exactitude du
contenu vérifiée ✅ (17/17 tables, 4/4 index, 2/2 FK DB — tous
confirmés par recherche directe dans les sources, pas supposés) · docs
à jour ✅ (requirements, design, planning, implementation, testing,
TODO.md, FILE-ATTENTE.md).

**Aucun blocage.** Feature prête à être poussée / PR à ouvrir sur
demande explicite de l'utilisateur (convention établie cette session).
