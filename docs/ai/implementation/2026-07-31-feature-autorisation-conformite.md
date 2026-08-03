---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: checked
created: 2026-08-03
updated: 2026-08-03
phase7: 2026-08-03
---

# Implementation Guide

## Phase 7 — Check Implementation

**Clos.** Alignement design / requirements **OK** ; pas d’écart majeur.
Preuve fraîche : `./mvnw verify` → **193** tests / 0 fail ; Jacoco
instructions **90,2 %** ; ArchUnit OK.

| Critère requirements | Statut |
|---|---|
| Point 19 clos V1 | OK (`TODO.md`) |
| `@EnableMethodSecurity` + surface livrable | OK (matrice ci-dessous) |
| Guard domaine + infra HTTP absente | OK |
| `requireWorkflowAdmin` absente | OK |
| `sensitive` bout-en-bout / pas de masquage | OK (IT) |
| 403 ProblemDetail method security | OK (IT) |
| Contrainte S3/FS anti-MinIO documentée | OK (DEFERRED + design) |
| Jacoco ≥ 80 % / ArchUnit | OK |

### Revue fichier par fichier

| Fichier | Intent design | Verdict |
|---|---|---|
| `SecurityConfig` | `@EnableMethodSecurity` | OK |
| `DossierTypeController` | `@PreAuthorize TENANT_ADMIN` POST/PATCH ; GET libre | OK |
| `DossierTypeAclController` | `@PreAuthorize TENANT_ADMIN` | OK |
| `WorkflowTypeConfigController` | `hasAnyRole` mutateurs ; GET libre ; pas de guard domaine | OK |
| `DossierWorkflowController` / `WorkflowTaskController` | Pas de `@PreAuthorize` ; `DossierAccessGuard` | OK |
| `domain…TenantAdminGuard` | Java pur ; use cases types/ACL | OK |
| `infrastructure…TenantAdminGuard` | Supprimé | OK |
| `MethodSecurityExceptionHandler` | Spring authz → 403 ProblemDetail | OK |
| `FieldDefinition` | `sensitive` meta, défaut false | OK (écart DEV-SENS-BOOL) |
| `DossierConfig` | Bean guard + wiring | OK |

### Matrice authz réalisée

| Surface | Annotation | Garde |
|---|---|---|
| POST/PATCH dossier-types | `hasRole('TENANT_ADMIN')` | `TenantAdminGuard` domaine |
| POST/DELETE …/acl | `hasRole('TENANT_ADMIN')` | `TenantAdminGuard` domaine |
| GET dossier-types | — | policy / filtre use case |
| dossiers CRUD | — | `DossierAccessPolicy` |
| POST/PATCH workflow-type-configs | `hasAnyRole(TENANT_ADMIN,WORKFLOW_ADMIN)` | — |
| start / history / complete | — | `DossierAccessGuard` |

## Phase 5 — synthèse

**T1–T9 done.** Method security + `TenantAdminGuard` domaine +
`FieldDefinition.sensitive` + D-403. Traçage `task` CLI indisponible.

## Development Setup

- Worktree : `.worktrees/feature-autorisation-conformite`
- Branche : `cursor/autorisation-conformite-f54c`
- JDK 25 (`JAVA_HOME`), `./mvnw test` / `verify`
- Main intégré : PR #18 (`DossierAccessGuard` conservé)

## Code Structure

```text
domain/dossier
├── TenantAdminGuard.java          # requireTenantAdmin(User)
├── model/FieldDefinition.java     # + sensitive (Boolean, coercé)
└── usecase/*Type* *Acl*           # injecte TenantAdminGuard

infrastructure
├── security/SecurityConfig.java   # + @EnableMethodSecurity
├── web/security/MethodSecurityExceptionHandler.java
├── web/dossier/DossierTypeController.java
├── web/dossier/DossierTypeAclController.java
├── web/dossier/DossierAccessGuard.java               # KEEP (PR #18)
└── web/workflow/WorkflowTypeConfigController.java
```

## Implementation Notes

### Core Features

1. **`@EnableMethodSecurity`** + matrice controllers.
2. **`TenantAdminGuard` domaine** sur Create/Update type + Grant/Revoke.
3. **`FieldDefinition.sensitive`** : `Boolean` coercé (voir écart).
4. **D-403** : advice Spring authz → ProblemDetail 403.

### Design deviations

| ID | Écart | Sévérité | Action |
|---|---|---|---|
| DEV-SENS-BOOL | `Boolean` + coerce au lieu de `boolean` primitif (Jackson `FAIL_ON_NULL_FOR_PRIMITIVES`) | Low | Accepté ; design mis à jour |

### Missing / non-blocking

| Item | Notes |
|---|---|
| Unit dédié `MethodSecurityExceptionHandler` | Couvert via MockMvc ProblemDetail |
| CreateDossierTypeUseCaseImplTest unitaire | Couvert via API + autres use cases admin |
| Masquage `sensitive` | Hors scope (Q4) — follow-up §23 |

## Integration Points

- BFF OIDC (`ROLE_*`)
- `DossierAccessPolicy` / `DossierAccessGuard`
- JSON `field_definitions` — pas de changeset SQL

## Testing Notes

Preuve phase 8 : `./mvnw verify` — **199** / 0 ; Jacoco **90,3 %**.

Nouveaux tests phase 8 : `CreateDossierTypeUseCaseImplTest`,
`MethodSecurityExceptionHandlerTest`, `grantAcl_dossierManager_returns403ProblemDetail`,
`updateStatus_viewerWithViewOnly_returns403`.

## Known Issues / Follow-ups

- Phase 9 : code review
- Follow-ups produit : masquage/exports `sensitive` ; port S3/FS
