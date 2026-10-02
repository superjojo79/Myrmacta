---
phase: implementation
title: Implementation — LC1-suite (liste des tables DMN dans la palette)
description: Ce qui a été livré pour TODO §74, écarts au design, revue adversariale
feature: lc1-suite-palette-dmn
lot: LC1-suite
---

# Implementation — `lc1-suite-palette-dmn` (TODO §74)

Documents liés : [requirements](../requirements/2026-09-30-feature-lc1-suite-palette-dmn.md) ·
[design](../design/2026-09-30-feature-lc1-suite-palette-dmn.md) · [planning](../planning/2026-09-30-feature-lc1-suite-palette-dmn.md) ·
[testing](../testing/2026-09-30-feature-lc1-suite-palette-dmn.md).

## Setup

Backend : `./mvnw verify -Dmaven.compiler.release=21 -Dskip.frontend=true` (JDK 21 dans l'environnement ; le `pom.xml` vise 25).
Frontend : `cd frontend && npm ci && npm run test` ; Playwright : `PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/pw-browsers/chromium npx playwright test`.
Aucune variable d'environnement, aucune migration, aucun drapeau.

## Code

| Fichier | Changement |
|---|---|
| `domain/workflow/palette/usecase/ListBlockReferencesUseCaseImpl` | `SERVED_SOURCES` += `dmnTables` |
| `infrastructure/palette/BlockReferenceLookupAdapter` | dépend de `DmnDeploymentPort` ; `list("dmnTables")` = dernière version de chaque clé, triée, libellé « clé (vN) » ; `exists("dmnTables")` = `findDeploymentInfo` (ajouté après la revue adversariale) |
| `domain/workflow/palette/port/out/BlockReferenceLookupPort` | javadoc |
| `frontend/.../valueSourceResolver.ts` | `SERVER_SOURCES` += `dmnTables` |

Wiring : le bean `BlockReferenceLookupAdapter` est un `@Component` ; `DmnDeploymentPort` est déjà un bean (Flowable) — aucun changement de configuration.

## Écarts au design

Un seul : `exists("dmnTables")` n'était pas prévu (D-LC1S-EXISTS), ajouté par la revue adversariale — voir ci-dessous.

## Revue adversariale (Phase 10, sous-agent indépendant, 1 passe)

Aucun constat Critique ni Haut. Constats et traitement :

| # | Sévérité | Constat | Traitement |
|---|---|---|---|
| 1 | Moyenne | Documentation obsolète (`dmnTables` « en saisie libre » : ROADMAP, implementation/design/testing de §73) | **Corrigé** : écarts marqués résolus, ROADMAP, TODO §74, FILE-ATTENTE, CHANGELOG |
| 2 | Basse | `exists("dmnTables")` levait `IllegalArgumentException` (non atteignable aujourd'hui) ; javadoc ambiguë | **Corrigé** en TDD (`dmnTableExistenceIsCheckedAgainstTheTenantDeployments`) + javadoc |
| 3 | Basse | Test d'isolation tenant non probant (le mock renvoie vide) | **Corrigé** : renommé `dmnTablesAreReadForTheRequestedTenantOnly` (il prouve le passage du tenant configuré ; l'isolation réelle est celle de `FlowableDmnDeploymentAdapter`, déjà testée) |
| 4 | Basse | Message « n'existe plus » inexact pour une clé jamais déployée (BPMN conçu avant la table) | **Tracé, non modifié** : avertissement fondé (le déploiement refuserait), libellé partagé par d'autres briques et asserté par `E2E-PALETTE-2` ; à reprendre si un retour utilisateur le demande |

Pas de seconde passe : aucun constat Critique/Haut.
