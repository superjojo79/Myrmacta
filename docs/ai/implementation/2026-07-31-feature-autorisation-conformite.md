---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: implemented
created: 2026-08-03
updated: 2026-08-03
---

# Implementation Guide

## Phase 5 — synthèse

**T1–T9 done.** Method security + `TenantAdminGuard` domaine +
`FieldDefinition.sensitive` + D-403. Preuve : `./mvnw verify` → **193** tests /
0 fail ; Jacoco instructions **90,2 %** ; ArchUnit OK. Traçage `task` CLI
indisponible.

## Development Setup

- Worktree : `.worktrees/feature-autorisation-conformite`
- Branche : `cursor/autorisation-conformite-f54c`
- JDK 25 (`JAVA_HOME`), `./mvnw test` / `verify`
- Main intégré : PR #18 (`DossierAccessGuard` conservé)

## Code Structure

```text
domain/dossier
├── TenantAdminGuard.java          # NEW — requireTenantAdmin(User)
├── model/FieldDefinition.java     # + sensitive (Boolean, coercé)
└── usecase/*Type* *Acl*           # injecte TenantAdminGuard

infrastructure
├── security/SecurityConfig.java   # + @EnableMethodSecurity
├── web/security/MethodSecurityExceptionHandler.java  # NEW — 403 ProblemDetail
├── web/dossier/DossierTypeController.java            # @PreAuthorize
├── web/dossier/DossierTypeAclController.java         # @PreAuthorize
├── web/dossier/DossierAccessGuard.java               # KEEP (PR #18)
└── web/workflow/WorkflowTypeConfigController.java    # @PreAuthorize hasAnyRole
```

Supprimé : `infrastructure.web.dossier.TenantAdminGuard`,
`requireWorkflowAdmin`.

## Implementation Notes

### Core Features

1. **`@EnableMethodSecurity`** sur `SecurityConfig` ; annotations selon matrice
   design (types/ACL = `TENANT_ADMIN` ; workflow-config mutateurs =
   `TENANT_ADMIN|WORKFLOW_ADMIN` ; dossiers / start / history / complete =
   aucune annotation rôle).
2. **`TenantAdminGuard` domaine** branché Create/Update type + Grant/Revoke ;
   bean `DossierConfig`.
3. **`FieldDefinition.sensitive`** : composant `Boolean` coercé
   `Boolean.TRUE.equals` dans le compact ctor (clé absente / null → false) —
   **écart D-SENS-DEF** (primitive `boolean` cassait Jackson
   `FAIL_ON_NULL_FOR_PRIMITIVES`). Ctor confort 7-args conserve les call sites.
4. **D-403** : `MethodSecurityExceptionHandler` mappe
   `AuthorizationDeniedException` /
   `org.springframework.security.access.AccessDeniedException` → 403
   ProblemDetail. Handlers domaine inchangés (FQCN distinct).

### Patterns

- Hexagonal : rôle HTTP au bord ; ACL domaine / `DossierAccessGuard`.
- Défense en profondeur types/ACL uniquement.

### Design deviations

| ID | Écart | Sévérité |
|---|---|---|
| DEV-SENS-BOOL | `Boolean` + coerce au lieu de `boolean` primitif | Low — requis Jackson ; sémantique false inchangée |

## Integration Points

- BFF OIDC existant (`ROLE_*`)
- `DossierAccessPolicy` / `DossierAccessGuard` inchangés pour dossiers/workflow
  instance
- JSON `field_definitions` — pas de changeset SQL

## Testing Notes

- Units : `TenantAdminGuardTest`, `FieldDefinitionTest`, validator sensitive,
  use cases admin
- IT : MockMvc 403 ProblemDetail (MANAGER, WORKFLOW_ADMIN sur types) ;
  sensitive round-trip ; valeur dossier en clair ; persistence JSON
- Régression workflow / auth / ArchUnit

## Known Issues / Follow-ups

- Phase 7 check implementation
- Phase 8 formaliser doc testing `verified`
- Masquage `sensitive` / exports (§23 reste)
- Port object storage S3/FS (doc only pour l’instant)
