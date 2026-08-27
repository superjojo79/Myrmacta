---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: in-progress
created: 2026-08-27
updated: 2026-08-27
feature: serveur-mcp
phase1: 2026-08-27
phase3: 2026-08-27
phase4: 2026-08-27
phase5: 2026-08-27
---

# Implementation Guide — `serveur-mcp`

Phase 5 TDD démarrée (plan Phase 4 :
`docs/ai/planning/2026-08-27-feature-serveur-mcp.md`). Cible design :
`docs/ai/design/2026-08-27-feature-serveur-mcp.md`.

## M1 — Gardes domaine `API_SERVICE` (fait, 2026-08-27)

Fichiers touchés :

- `domain/dossier/DossierAccessPolicy.java` — `hasPermission` : ajout
  `user.hasRole("API_SERVICE")` au fork TA/WA (VIEW/EDIT tenant-wide).
- `domain/reporting/SlaReportAccess.java` — `allowedTypeCodes` : idem
  → `null` (unrestricted) pour `API_SERVICE`.
- `domain/dossier/TenantAdminGuard.java` — nouvelle méthode
  `requireTenantAdminOrApiService` ; `requireTenantAdmin` **inchangée**
  (purge / ACL / rétention / `TenantSlaSettings` continuent de la
  refuser à `API_SERVICE`, non-régression T1.4 sans changement de code).
- `domain/dossier/usecase/CreateDossierTypeUseCaseImpl.java` et
  `UpdateDossierTypeUseCaseImpl.java` — appellent désormais
  `requireTenantAdminOrApiService`.
- `domain/dossier/usecase/SearchDossiersUseCaseImpl.java` et
  `ListDossierTypesUseCaseImpl.java` — `API_SERVICE` ajouté au fork
  unrestricted (**DD-POLICY-TRIPLE**).
- Nouveau test `domain/reporting/SlaReportAccessTest.java` (pas de test
  dédié existant pour cette classe utilitaire statique).
- Tests étendus : `DossierAccessPolicyTest`, `TenantAdminGuardTest`,
  `CreateDossierTypeUseCaseImplTest`, `UpdateDossierTypeUseCaseImplTest`,
  `SearchDossiersUseCaseImplTest`, `ListDossierTypesUseCaseImplTest`.

Aucune déviation par rapport au design.

## M2 — Audit auteurs (fait, 2026-08-27)

Fichiers touchés :

- `db/changelog/changes/017-mcp-audit-authors.yaml` (+ include master) :
  `created_by`/`updated_by` varchar(128) NOT NULL sur `dossier_type` et
  `workflow_type_config`, backfill `'system'`, pattern `addColumn` +
  `sql` UPDATE + `addNotNullConstraint` (comme 015).
- `domain/dossier/model/DossierType.java` — record étendu à 13
  composants (`createdBy`, `updatedBy`). `inherit(...)` (8 args,
  ~20 sites de test) inchangé en signature, stampe `"system"` en
  interne. `withUpdate(...)` gagne un paramètre `updatedBy`, préserve
  `createdBy` existant.
- `domain/dossier/usecase/CreateDossierTypeUseCaseImpl.java` /
  `UpdateDossierTypeUseCaseImpl.java` — stampent `actor.userId()`.
  Aucun changement REST : `AccessContextFactory.user(...)` extrayait
  déjà `OidcUser.getSubject()` comme `userId`.
- `infrastructure/persistence/dossier/DossierTypeJpaEntity.java` —
  colonnes + mapping `createdBy`/`updatedBy`.
- 8 fichiers de test avec appel direct au constructeur 11-arg de
  `DossierType` (hors `inherit`) mis à jour avec `"system", "system"`
  littéraux (pas d'impact métier, ces tests ne portent pas sur l'audit).
- `domain/workflow/model/WorkflowTypeConfig.java` — record étendu à 10
  composants. **Constructeur canonique** requiert `createdBy`/
  `updatedBy` non-null ; un **second constructeur** (les 8 anciens
  paramètres) délègue avec `"system"/"system"` — décision prise pour
  éviter de toucher ~20 fichiers de test qui construisent ce record
  directement (pas de factory `inherit` préexistante ici, contrairement
  à `DossierType`).
- `infrastructure/persistence/workflow/WorkflowTypeConfigJpaEntity.java`
  — colonnes + mapping.
- `infrastructure/web/workflow/WorkflowApiDtos.java` —
  `WorkflowTypeConfigRequest.toDomain(...)` gagne `createdBy`/
  `updatedBy` en paramètres explicites.
- `infrastructure/web/workflow/WorkflowTypeConfigController.java` —
  `create` : `accessContextFactory.user(authentication).userId()` comme
  `createdBy`/`updatedBy` (remplace `requireOidcPrincipal`, sémantique
  équivalente + extraction du subject). `update` : préserve
  `existing.createdBy()`, stampe `updatedBy` avec l'acteur courant.

**Déviation mineure vs design** : `SaveWorkflowTypeConfigUseCase`
(`create`/`update`) n'a **pas** gagné de paramètre acteur — le
`WorkflowTypeConfig` passé en argument est déjà entièrement stampé par
l'appelant (contrôleur REST aujourd'hui, tool MCP `define_workflow`/
`deploy_workflow` en M6). Plus simple, testable au même niveau, et
conforme à l'esprit de la note design ("ajouter updatedBy... appelé
depuis le contrôleur REST et le tool MCP").

## M3 — Compatibilité schéma (fait, 2026-08-27)

Fichiers touchés :

- `domain/dossier/port/out/DossierRepository.java` — nouvelle méthode
  `findByDossierTypeCode(tenantId, dossierTypeCode)`.
- `infrastructure/persistence/dossier/SpringDataDossierRepository.java`
  — dérivée `findByTenantIdAndDossierTypeCode`.
- `infrastructure/persistence/dossier/JpaDossierRepositoryAdapter.java`
  — implémentation.
- `domain/dossier/port/in/CheckDossierTypeSchemaCompatibilityUseCase.java`
  (nouveau) — `Query(tenantId, dossierTypeCode, newFieldDefinitions,
  allowUnknownFields)` / `Result(incompatibleCount, sampleReferences)`.
- `domain/dossier/usecase/CheckDossierTypeSchemaCompatibilityUseCaseImpl.java`
  (nouveau) — charge tous les dossiers du type (pas de cap V1, conforme
  design), valide chacun avec `DossierFieldValidator.validate(newFields,
  dossier.fields(), allowUnknown)`, compte les échecs, échantillonne les
  5 premières références incompatibles.
- `ArchitectureRulesTest.DOSSIER_FIELDS_READ_ALLOWLIST` — ajout de
  `CheckDossierTypeSchemaCompatibilityUseCaseImpl` (seul nouveau membre,
  conforme design ; fait maintenant plutôt qu'en T8.1 car le code ne
  compile/passe pas la suite ArchUnit sinon).

Aucune déviation par rapport au design. `DefineDossierTypeTool` (T6.1)
appellera ce use case avant l'update pour bloquer un changement de
schéma incompatible.

Prochaine tâche : **T4.1** (BOM Spring AI 2.0.0 + starter MCP WebMVC).

## Development Setup

Workspace : branche `cursor/serveur-mcp-e12d`, no-worktree.
Stack : Java 25 / Spring Boot 4.1. Commandes :

```bash
./mvnw verify -Dmaven.compiler.release=21
cd frontend && npm run test && npm run test:e2e
```

## Code Structure

- `com.flowforge.infrastructure.mcp` — tools + factory JWT.
- Domaine : gardes **D-POLICY-AS**, `requireTenantAdminOrApiService`,
  `createdBy`/`updatedBy`, `CheckDossierTypeSchemaCompatibilityUseCase`.
- Liquibase `017-mcp-audit-authors.yaml`.
- `pom.xml` : BOM Spring AI 2.0.0 + starter MCP WebMVC.

## Implementation Notes

TDD (`tdd` + `verify`). Ne pas lire `Dossier.fields()` depuis un tool
MCP. Ne pas élargir `requireTenantAdmin`. Ne pas ajouter de starter
chat.
