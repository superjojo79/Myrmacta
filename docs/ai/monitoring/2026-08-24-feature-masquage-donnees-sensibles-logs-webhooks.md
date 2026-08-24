---
phase: monitoring
title: Monitoring & Observability
description: Define monitoring strategy, metrics, alerts, and incident response
status: done
created: 2026-08-24
updated: 2026-08-24
feature: masquage-donnees-sensibles-logs-webhooks
---

# Monitoring & Observability — `masquage-donnees-sensibles-logs-webhooks`

## Portée

Feature de garde-fou préventif, pas de nouvelle surface applicative à
observer (aucun endpoint, aucun job planifié, aucune nouvelle métrique).

## Garantie apportée (pas une métrique runtime, une propriété statique)

- **Logs** : la liste blanche ArchUnit (`ArchitectureRulesTest`) fait
  échouer le build si une future classe hors liste appelle
  `Dossier.fields()`/`FieldDefinition.defaultValue()` — le contrôle a
  lieu au build, pas à l'exécution ; rien à surveiller en production.
- **Webhooks** : le masquage dans `SendNotificationUseCaseImpl` est
  déterministe et déjà couvert par test bout en bout — pas de mode
  d'échec silencieux à instrumenter (le cas `dossierTypeCode` introuvable
  omet la donnée plutôt que de lever une exception qui déclencherait une
  alerte).

## Si une observation future est utile

Si un futur producteur commence à peupler `dossierFieldsSnapshot`, un
compteur applicatif sur le taux d'omission (`dossierTypeCode` introuvable)
pourrait signaler un problème de configuration côté producteur — non
implémenté ici, aucun producteur réel n'existe encore pour le justifier.
