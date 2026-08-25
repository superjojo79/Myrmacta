---
phase: deployment
title: Deployment Strategy
description: No runtime deployment change — documentary chore
status: n-a
created: 2026-08-25
updated: 2026-08-25
feature: derive-documentaire
---

# Deployment Strategy — `derive-documentaire`

## Phase 7: Implementation notes (deployment)

**Non applicable.** Feature 100 % documentaire. Aucun artefact runtime,
aucune migration, aucun secret, aucun pipeline à changer.

Le seul fichier yaml touché (`src/main/resources/application.yml`) est un
**commentaire d'en-tête** : les exclusions Flowable et le
`context-path` commenté restent strictement identiques.

Rollback = revert git des markdown + du commentaire.
