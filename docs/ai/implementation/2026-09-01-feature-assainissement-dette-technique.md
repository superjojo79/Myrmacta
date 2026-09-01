---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
---

# Implementation Guide

## Development Setup
**How do we get started?**

- Aucun setup particulier — feature backend seule, aucune nouvelle dépendance, aucune migration.
- Cycle TDD standard : `./mvnw -Dtest=<Classe> test` ciblé puis `./mvnw verify` avant tout claim.

## Code Structure
**How is the code organized?**

- **M1 — validation blank (4 fichiers `domain` + 4 fichiers de test)** :
  `domain/workflow/model/WorkflowTypeConfig.java`, `TriggerOutboxPayload.java`,
  `WorkflowStartedEvent.java`, `domain/dossier/model/DossierType.java` — garde
  `if (x.isBlank()) throw new IllegalArgumentException(...)` ajoutée dans chaque compact
  constructor, juste après le `Objects.requireNonNull` existant. Tests : `WorkflowTypeConfigTest`
  (méthode ajoutée), `TriggerOutboxPayloadTest`/`WorkflowStartedEventTest` (nouveaux fichiers,
  n'existaient pas), `DossierTypeTest` (méthode ajoutée).
- **M2 — callback MDC (3 fichiers `domain`/`infrastructure` + 3 fichiers de test)** :
  `domain/dossier/port/in/ProcessPurgeOutboxUseCase.java` (signature `processBatch` étendue),
  `domain/dossier/usecase/ProcessPurgeOutboxUseCaseImpl.java` (invocation du callback + `finally`),
  `infrastructure/dossier/PurgeOutboxJob.java` (implémentation du callback via `MdcScope`). Tests :
  `ProcessPurgeOutboxUseCaseImplTest` (4 nouvelles méthodes + 8 méthodes existantes mises à jour vers
  la signature 3 arguments), `PurgeOutboxJobTest` (1 nouvelle méthode unitaire + 1 nouveau test
  d'intégration multi-items + 2 méthodes existantes mises à jour), `PurgeOutboxApiTest` (1 appel
  direct mis à jour).
- **M3 — nettoyage doc** : `DEFERRED-FEATURES.md` (4 lignes mises à jour, aucune supprimée —
  marquées **Clos**/**Livré** avec référence plutôt que retirées, pour garder la trace historique).

## Implementation Notes
**Key technical details to remember:**

### Core Features

- **D-BLANK-VALIDATION-SCOPE** : implémenté tel que conçu, sans écart. Message d'exception uniforme
  `"<champ> must not be blank"` (cohérent avec `DossierType.code`'s
  `"code must match ..., was: ..."` en substance, pas en forme exacte — pas de convention de message
  imposée trouvée dans le dépôt au-delà de nommer le champ).
- **Trouvaille Phase 7 (hors périmètre, non corrigée ici)** : `WorkflowTriggerEvent.processKey`
  (`domain/connector/model/WorkflowTriggerEvent.java`) porte le même gap (`requireNonNull` sans
  garde blank) que les 4 records ciblés, mais n'était pas dans la liste D-BLANK-VALIDATION-SCOPE
  tranchée en Phase 1/3. Vérifié non exploitable en pratique : son seul appelant
  (`ConnectorTriggerRuntimeImpl.onIncomingEvent`) construit immédiatement un `TriggerOutboxPayload`
  avec cette même valeur — la garde blank désormais présente sur `TriggerOutboxPayload.processKey`
  intercepte donc déjà tout `processKey` blank transitant par ce chemin. Laissé tel quel
  (defense-in-depth déjà réalisée un cran plus loin dans le flux), documenté ici plutôt que corrigé
  silencieusement hors du périmètre décidé.
- **D-MDC-DOSSIER-MECHANISM** : implémenté tel que conçu en Phase 3 — `Function<UUID, Runnable>`,
  callback enveloppe tout `purgeDossier(record)` via `finally`, pas seulement le `catch`. Le test
  d'intégration multi-items (`PurgeOutboxJobTest.process_multipleItems_...`) utilise le **vrai**
  `ProcessPurgeOutboxUseCaseImpl` (pas mocké) avec un `Answer` Mockito sur
  `WorkflowEnginePort.deleteProcessInstanceBestEffort` pour capturer l'état MDC au moment exact de
  l'appel — preuve directe que le `log.warn` réel de `FlowableWorkflowEngineAdapter` identifié en
  Phase 3 voit bien `dossierId` ET `tenantId` simultanément en MDC, sans fuite entre les 2 items du
  batch.
- **D-PERSISTABLE-CONFIRM** : fermé par preuve documentée en Phase 3 (design doc), aucun code écrit,
  ligne `DEFERRED-FEATURES.md` mise à jour en M3.

### Patterns & Best Practices

- **Séquencement TDD sur un changement de signature de port** : l'ajout du 3ᵉ paramètre à
  `ProcessPurgeOutboxUseCase.processBatch` n'a pas de comportement testable en lui-même (c'est de la
  plomberie de compilation) — traité comme préalable non-TDD assumé (tous les appelants mis à jour
  avec un callback no-op dans le même geste), puis le vrai comportement (callback effectivement
  invoqué, fermeur effectivement exécuté) a été piloté par test rouge→vert par-dessus cette base
  compilable. Documenté ici plutôt que présenté comme un TDD strict de bout en bout, par honnêteté
  sur ce qui s'est réellement passé (une première tentative avait implémenté le comportement complet
  avant le test — reverti et refait dans le bon ordre).
- Callback typé `Function<UUID, Runnable>` plutôt que `Consumer<UUID>` — voir design doc pour la
  justification (fermeture symétrique du `MdcScope` sans exception checked).

## Integration Points
**How do pieces connect?**

- `ProcessPurgeOutboxUseCase` (port `in`) — 1 seul appelant de production
  (`PurgeOutboxJob.process()`, via `this::openItemScope`), 1 seule implémentation
  (`ProcessPurgeOutboxUseCaseImpl`). Aucun contrat REST/MCP concerné.

## Error Handling
**How do we handle failures?**

- Gardes blank : `IllegalArgumentException` non catchée spécifiquement — remonte comme toute
  violation d'invariant domaine existante (cohérent avec `DossierType.code`/`CODE_PATTERN`).
- Callback MDC : le `finally` garantit que le fermeur de scope s'exécute même si `purgeDossier`
  lève une exception catchée par le `catch (RuntimeException ex)` englobant — testé explicitement
  (`processBatch_itemFails_stillRunsCloserAfterMarkFailed`).

## Performance Considerations
**How do we keep it fast?**

- Aucun changement mesurable attendu (comparaisons de chaînes, un `MdcScope` ouvert/fermé par item
  déjà du même ordre que le `MdcScope.tenant` existant par batch) — pas de benchmark dédié.

## Security Notes
**What security measures are in place?**

- Aucun changement de surface de sécurité — pas de nouvel endpoint, pas de nouvelle donnée
  sensible, pas de changement d'autorisation.
