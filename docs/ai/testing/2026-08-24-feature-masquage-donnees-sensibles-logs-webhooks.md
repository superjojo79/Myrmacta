---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: done
created: 2026-08-24
updated: 2026-08-24
feature: masquage-donnees-sensibles-logs-webhooks
design_ref: docs/ai/design/2026-08-24-feature-masquage-donnees-sensibles-logs-webhooks.md
---

# Testing Strategy — `masquage-donnees-sensibles-logs-webhooks`

Feature backend uniquement — aucun endpoint HTTP, aucun contrat d'API
consommé par la SPA n'est touché (`NotificationEvent`/`SendNotificationUseCase`
sont des types internes au domaine). **Vitest/Playwright non applicables**
(pas de régression FE possible, aucun fichier `.vue`/`.ts` modifié) —
confirmé en Phase 7/8 par `git diff --stat`, pas juste supposé.

## Test Coverage Goals

- Backend : Jacoco ≥ seuil projet sur le code neuf/modifié (`./mvnw verify`).
- Style de test : mirroring `D-SECRETS-AUDIT` (`NotificationWebhookResponseSecretMaskingTest`,
  `ConnectorResponseSecretMaskingTest`) — construire un objet avec une valeur
  connue, exercer le vrai chemin de production, asserter l'absence de cette
  valeur dans la sortie.

## Unit Tests

### `Dossier`/`FieldDefinition` — D-LOG-GUARD
- [x] `toString()` de `Dossier` avec des champs sensibles connus ne contient
      pas leurs valeurs (assertion directe sur la chaîne produite)
- [x] `toString()` de `Dossier` reste utile en debug (affiche au moins
      `id`/`typeCode`/nombre de champs) — pas juste `Dossier@hashcode`
- [x] `toString()` de `FieldDefinition` ne contient pas `defaultValue`
- [x] Règle ArchUnit (`ArchitectureRulesTest`) : liste blanche des classes
      autorisées à appeler `Dossier.fields()`/`FieldDefinition.defaultValue()`
      (pas une détection de logging — vérifié non réalisable avec ArchUnit,
      voir design doc). `ArchitectureRulesFixtureTest` prouve que la règle
      échoue vraiment sur une classe hors liste blanche (`ClassFileImporter`
      ciblé, pas seulement supposée correcte). Liste blanche déterminée en
      faisant tourner la règle contre le code réel (5 classes pour
      `Dossier.fields()`, 1 pour `FieldDefinition.defaultValue()`), pas
      devinée par `grep`
- [x] Non-régression : `DossierFieldRedactorTest` toujours vert
      (`./mvnw test -Dtest=DossierFieldRedactorTest`), comportement des 3
      appelants (`GetDossierUseCaseImpl`, `ExportDossiersUseCaseImpl`,
      `WorkflowLinkVisualizationUseCaseImpl`) inchangé

### `SendNotificationUseCaseImpl` — D-WEBHOOK-MASK
- [x] `send()` sans `dossierFieldsSnapshot` (les 2 producteurs réels
      actuels) : comportement strictement inchangé (payload transmis tel
      quel, zéro appel à `DossierTypeRepository`) — non-régression du
      chemin à 100 % du trafic réel
- [x] `send()` avec `dossierFieldsSnapshot` simulé contenant un champ
      `sensitive=true` avec une valeur connue : la valeur est absente du
      payload transmis à `outboxRepository.savePendingBatch()` (assertion
      sur l'argument capturé)
- [x] `send()` avec `dossierFieldsSnapshot` mais un champ non-sensible :
      valeur conservée (pas de sur-masquage)
- [x] `send()` avec `dossierFieldsSnapshot` référençant un `dossierTypeCode`
      introuvable (`DossierTypeRepository.findByCode()` retourne vide) :
      **décision actée en implémentation** — fail-safe, la clé
      `dossierFields` est entièrement omise du payload (pas de valeur
      brute non masquée, pas d'exception)
- [x] `send()` avec un champ sensible marqué comme tel dans
      `FieldDefinition` mais absent de la map `fields` du snapshot : pas
      d'exception (convention « clé absente = pas de valeur », cohérente
      avec `DossierFieldMerger`/`DossierFieldRedactor` — un champ
      *sensible avec valeur `null`* n'existe pas dans ce codebase, cf.
      `Map.copyOf` sur `Dossier.fields()`/`DossierFieldsSnapshot.fields()`)
- [x] Canal `BROWSER` avec `dossierFieldsSnapshot` : même masquage appliqué
      (effet de bord assumé en Phase 3 design)
- [x] **Contrôle mutant manuel** : masquage neutralisé temporairement dans
      `SendNotificationUseCaseImpl`, confirmé que 4 tests échouent (preuve
      que ces tests détectent vraiment une régression, pas un succès à
      vide), puis code restauré et re-vérifié vert

## Integration Tests

- [x] Bout en bout réel (`NotificationSensitiveFieldMaskingEndToEndTest`) :
      `SendNotificationUseCaseImpl` (masque) → `NotificationPayloadJson`
      (sérialise) → `NotificationOutboxRelay` (désérialise, classe réelle)
      → `WebhookRestAdapter` (corps HTTP réel capturé) ne contient jamais
      la valeur sensible connue à aucune étape — seules les frontières
      repository/HTTP sont mockées, toutes les classes de production
      pertinentes tournent réellement
- [x] `WebhookRestAdapter` reste inchangé fonctionnellement pour les
      payloads sans donnée dossier (`WebhookRestAdapterTest` existant
      toujours vert, non modifié)

## End-to-End Tests

Non applicable — feature backend pure, aucun flux utilisateur FE concerné.

## Test Data

- Fixture `FieldDefinition` avec `sensitive=true`/`false` (déjà des
  builders similaires dans les tests `DossierFieldRedactor`/`DossierFieldMerger`
  existants — réutiliser plutôt que dupliquer)
- Valeur "connue" de test (chaîne unique facilement grep-able, ex.
  `"SECRET-VALUE-DO-NOT-LEAK-<random>"`), mirroring le pattern des tests
  `*SecretMaskingTest` existants

## Test Reporting & Coverage

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw verify
```

- Frontend non concerné — confirmé en Phase 8 par `git diff --stat --
  frontend/` (aucune sortie, zéro fichier touché) — pas de `npm run
  test`/`test:e2e` à produire pour cette feature
- `./mvnw verify` final : **1088 tests, 0 échec**, seuils Jacoco tenus,
  `BUILD SUCCESS`

## Manual Testing

Aucune (feature backend, aucune UI).

## Performance Testing

Non applicable — le lookup `DossierTypeRepository` n'a lieu que si un
snapshot est fourni, ce qui n'arrive jamais sur le trafic réel actuel (cf.
design doc, Non-Functional Requirements).

## Bug Tracking

Aucun processus dédié — toute lacune trouvée en Phase 7 (revue fichier par
fichier) est corrigée directement avant de clore la feature.
