---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
---

# Testing Strategy

> Première passe (Phase 1), dérivée des Success Criteria des requirements — cases à cocher au fil
> de Phase 8 (`dev-testing`), pas avant.

## Test Coverage Goals
**What level of testing do we aim for?**

- 100% des branches nouvelles/modifiées (gardes blank + callback MDC) — cohérent avec le seuil
  Jacoco du bundle (LINE ≥85%/BRANCH ≥75%).
- Aucune régression sur les tests `requireNonNull` existants des 4 records ciblés.
- Aucun test frontend nouveau — feature confirmée backend seul.

## Unit Tests
**What individual components need testing?**

### Records domaine (validation blank)
- [ ] `WorkflowTypeConfig` : `workflowKey` blank (`""`, `"   "`) → `IllegalArgumentException`
- [ ] `TriggerOutboxPayload` : `processKey` blank → `IllegalArgumentException`
- [ ] `WorkflowStartedEvent` : `processKey` blank → `IllegalArgumentException`
- [ ] `DossierType` : `workflowKey` blank → `IllegalArgumentException`
- [ ] Non-régression : `workflowKey`/`processKey` `null` → `NullPointerException` (comportement
      `requireNonNull` inchangé) pour les 4 records

### ProcessPurgeOutboxUseCaseImpl (callback MDC)
- [ ] Callback invoqué exactement une fois par item traité, avec le `dossierId` correct, avant
      `purgeDossier`
- [ ] Callback invoqué même quand l'item échoue (`RuntimeException` catchée → `markFailed`)
- [ ] Callback non invoqué pour un item skippé par `shouldSkipClock` ou non `claim()`é
- [ ] Signature/port : `ProcessPurgeOutboxUseCase` reste sans dépendance `org.slf4j`/MDC
      (vérifié par `ArchitectureRulesTest.domain_has_no_framework_dependency`, pas de nouveau test
      dédié nécessaire)

### PurgeOutboxJob (intégration callback → MdcScope)
- [ ] `dossierId` présent dans le MDC pendant l'exécution du callback pour un item donné
- [ ] `dossierId` restauré (retiré ou valeur précédente) après le callback, `tenantId` toujours
      présent (comportement `MdcScope` par pile, déjà testé pour `tenant`/`dossier` combinés
      ailleurs)

## Integration Tests
**How do we test component interactions?**

- [ ] `PurgeOutboxJob` bout-en-bout (ou test d'intégration existant étendu) : un batch de 2+ items
      distincts voit chacun son propre `dossierId` en MDC au bon moment, pas de fuite entre items
- [ ] `ArchitectureRulesTest` complet vert (pas seulement la règle domaine ciblée) — non-régression
      globale

## End-to-End Tests
**What user flows need validation?**

- Aucun — feature backend seul, aucun flux utilisateur visible modifié.

## Test Data
**What data do we use for testing?**

- Records domaine construits directement en test unitaire (pas de fixture DB nécessaire pour les
  gardes blank).
- `PurgeOutboxRecord`/`Dossier` de test déjà existants dans les tests `ProcessPurgeOutboxUseCaseImpl`
  actuels, réutilisés pour vérifier le callback.

## Test Reporting & Coverage
**How do we verify and communicate test results?**

- `./mvnw verify` (Jacoco) — vérifier que les nouvelles branches (gardes blank, callback) sont
  couvertes ; inspection `target/site/jacoco/<pkg>/<Class>.java.html` si le bundle ne suffit pas à
  localiser un gap.
- Pas de commande frontend concernée.

## Manual Testing
**What requires human validation?**

- Aucun — pas de surface UI/UX modifiée.

## Performance Testing
**How do we validate performance?**

- Non applicable — pas de changement de volumétrie ni de chemin chaud identifié.

## Bug Tracking
**How do we manage issues?**

- Suivi standard du dépôt (TODO.md §49, PR de la feature) — pas de process dédié supplémentaire.
