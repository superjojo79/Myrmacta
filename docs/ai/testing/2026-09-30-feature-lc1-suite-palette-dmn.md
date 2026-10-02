---
phase: testing
title: Testing — LC1-suite (liste des tables DMN dans la palette)
description: Scénarios et résultats de vérification
status: verified
created: 2026-09-30
updated: 2026-09-30
feature: lc1-suite-palette-dmn
lot: LC1-suite
---

# Testing — `lc1-suite-palette-dmn`

## Scénarios (dérivés des critères de succès)

| Critère | Test | Niveau |
|---|---|---|
| S1 liste (clé, « clé (vN) », tri) | `BlockReferenceLookupAdapterTest.dmnTablesListTheLatestDeploymentOfEachKey…` ; `BuildingBlockApiTest.deployedDecisionTablesAreListedByTheirLatestVersionThroughTheRealSecurityChain` (deux versions réellement déployées dans Flowable) | unitaire + HTTP réel |
| S1 rôles | `BlockReferencesUseCaseTest` (les deux rôles ; source servie), `BuildingBlockApiTest.workflowAdminGetsTheReferenceLists…` | unitaire + HTTP |
| S2 403 / 401 | `BuildingBlockApiTest.dmnTablesReferencesAreRefusedToOtherRolesAndToAnonymous` | HTTP réel |
| S3 SPA | `valueSourceResolver.spec.ts` (endpoint appelé ; source inconnue ⇒ `null`) ; Playwright `E2E-PALETTE-8` | Vitest + E2E |
| S4 tenant | `dmnTablesAreReadForTheRequestedTenantOnly` (le tenant demandé est celui transmis au port) ; l'isolation réelle est celle de `FlowableDmnDeploymentAdapter` (`FlowableDmnDeploymentAdapterTest`) | unitaire |
| Revue #2 | `dmnTableExistenceIsCheckedAgainstTheTenantDeployments` | unitaire |

TDD : rouge constaté (erreur de compilation puis `dmnTableExistence…` en erreur) avant chaque implémentation.

## Résultats (HEAD du lot, 2026-09-30)

| Commande | Résultat |
|---|---|
| `./mvnw verify -Dmaven.compiler.release=21` (frontend construit, sans `-Dskip.frontend`) | **BUILD SUCCESS**, 2658 tests, 0 échec, 0 erreur, 3 ignorés (préexistants) |
| `cd frontend && npm run test:all` | Vitest **499/499** (99 fichiers) ; Playwright **106/106** (dont `E2E-PALETTE-1..8`) |
| `npx vue-tsc -b` ; `npx eslint .` | sans erreur |

Note d'environnement : `-Dskip.frontend=true` fait échouer `OpenApiDisabledApiTest` (absence de `static/index.html`, sortie du build
frontend) ; le test passe dès que le frontend est construit — ce n'est pas une régression du lot. JDK 21 utilisé (`-Dmaven.compiler.release=21`,
conforme à `flowforge-conventions`), Docker absent (ITs Testcontainers non rejouées ici).
