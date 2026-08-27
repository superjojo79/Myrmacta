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

Aucune déviation par rapport au design. Prochaine tâche : **T2.1**
(Liquibase `017-mcp-audit-authors.yaml`).

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
