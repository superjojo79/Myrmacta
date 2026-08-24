---
phase: deployment
title: Deployment & Release
description: Plan deployment strategy and release process
status: done
created: 2026-08-24
updated: 2026-08-24
feature: masquage-donnees-sensibles-logs-webhooks
---

# Deployment & Release — `masquage-donnees-sensibles-logs-webhooks`

## Application release

- Aucun changement d'infrastructure, aucune propriété de configuration
  nouvelle, aucune migration Liquibase. Même artefact Boot que d'habitude
  — pas de fenêtre de compatibilité à gérer (`NotificationEvent` reste
  compatible source/binaire pour tous les appelants existants grâce au
  constructeur de convenance 7-arg).
- `SendNotificationUseCaseImpl` gagne une dépendance constructeur
  (`DossierTypeRepository`) — câblée dans `NotificationConfig`, aucune
  action manuelle de déploiement requise (résolution Spring standard).

## Rollback

Trivial — revert du commit/PR, aucune donnée persistée modifiée
rétroactivement, aucun changelog à annuler.

## Post-deployment validation

Rien de spécifique à ce lot — le comportement observable du trafic réel
actuel (aucun producteur ne peuple `dossierFieldsSnapshot`) est
strictement inchangé, vérifié par test
(`send_noDossierFieldsSnapshot_neverQueriesDossierTypeRepository`).
