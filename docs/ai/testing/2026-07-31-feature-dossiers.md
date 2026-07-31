---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: verified
created: 2026-07-31
updated: 2026-07-31
verified: 2026-07-31
---

# Testing Strategy

## Test Coverage Goals
**What level of testing do we aim for?**

- **Unitaires domaine :** `DossierFieldValidator`, `DossierAccessPolicy`,
  transitions de statut, format de référence (pure).
- **Unitaires infra légère :** mappers JPA (si extraits), handler ProblemDetail.
- **IT H2 (PostgreSQL mode) :** persistence, séquence atomique, `@Version`,
  ACL en base, controllers MockMvc + session BFF mock OIDC.
- **Régression :** auth BFF, Tenant*, workflow hexagonal, ArchUnit, Jacoco ≥ 80 %.
- **E2E navigateur / PG réel / Testcontainers :** hors scope tranche B.

Alignement success criteria requirements : CRUD + ACL + DRAFT + ProblemDetail +
`./mvnw test`/`verify` verts sans PostgreSQL externe.

## Unit Tests
**What individual components need testing?**

### DossierFieldValidator
- [x] Required manquant → erreur
- [x] Type mismatch NUMBER/DATE/BOOLEAN
- [x] SELECT hors options → erreur
- [x] Champ inconnu + `allowUnknownFields=false` → erreur
- [x] Champ inconnu + `allowUnknownFields=true` → OK
- [x] Payload valide → liste vide

### DossierAccessPolicy
- [x] `AccessContext.System` → autorise view/edit
- [x] `TENANT_ADMIN` / `WORKFLOW_ADMIN` → bypass
- [x] User avec EDIT → view+edit OK
- [x] User avec VIEW → view OK, edit refus
- [x] User sans grant → refus
- [x] `AccessContext.Service` → refus dossier (décision 20)

### UpdateDossierStatus (règles transition)
- [x] Chaque transition autorisée du graphe
- [x] Transitions illégales → exception conflit
- [x] `ARCHIVE` terminal

### SequentialReferenceGenerator (logique format / année UTC)
- [x] Format `DOS-{TYPE}-{YYYY}-{seq:06d}`
- [x] Année dérivée UTC (pas `Instant` `%tY` cassé)

## Integration Tests
**How do we test component interactions?**

### Persistence H2
- [x] Liquibase applique les 4 tables + contraintes
- [x] Save/load `DossierType` (jsonb fieldDefinitions)
- [x] Create dossier + référence unique incrémentale
- [x] Concurrent/sequential next value ne duplique pas
- [x] `@Version` : second save stale → conflit
- [x] UNIQUE `(tenant_id, reference)` / `(tenant_id, code)`

### API MockMvc + OIDC mock
- [x] Anonyme `POST /api/dossiers` → 401
- [x] `TENANT_ADMIN` crée type → 201 ; PATCH type (`active`/fields) → 200
- [x] Non-admin / `WORKFLOW_ADMIN` crée type → 403
- [x] Grant EDIT puis create dossier (`label` requis) → 201 `DRAFT` + référence ;
      `tenantId` = instance
- [x] Create sur type `active=false` → **409**
- [x] DefaultValue injecté si clé absente puis validé
- [x] Create champs invalides / FILE required blank → 400 ProblemDetail `errors[]`
- [x] Create sans `label` → 400
- [x] Get sans VIEW → 403 ; avec VIEW → 200 ; `WORKFLOW_ADMIN` bypass OK
- [x] Get by `ref` → 200
- [x] PATCH status légal → 200 ; illégal → 409
- [x] PATCH status version stale → 409
- [x] Revoke ACL → create/get refusés ; re-grant idempotent 204
- [x] CSRF manquant sur mutateur → 403
- [x] Suite BFF existante non régressée

## End-to-End Tests
**What user flows need validation?**

- [ ] N/A navigateur — parcours couverts en IT MockMvc
- [ ] Smoke manuel optionnel : instance + PG réel (hors CI)

## Architecture Tests (ArchUnit)
- [x] `domain.dossier` indépendant de Spring / JPA / `infrastructure`
- [x] Pas d’entité JPA hors `infrastructure.persistence`
- [x] Convention `port.in` vs `usecase` (*Impl) respectée

## Test Data
**What data do we use for testing?**

| Fixture | Valeur | Usage |
|---|---|---|
| Tenant | `tenant_dev` | yaml + search_path H2 schema |
| Admin | rôles `TENANT_ADMIN`, claim tenant OK | CRUD types / ACL |
| Manager | `DOSSIER_MANAGER` + grant EDIT SINISTRE | create/status |
| Viewer | `DOSSIER_VIEWER` + grant VIEW | get only |
| Type | code `SINISTRE`, fields sample | validation |
| H2 URL | `jdbc:h2:mem:…;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE` | IT |

## Test Reporting & Coverage
**How do we verify and communicate test results?**

- Commandes : `./mvnw test` ; `./mvnw verify` (Jacoco).
- Seuil : bundle ≥ 80 % lignes (existant).
- Gaps acceptés : config YAML pure, generated, package-info.

## Manual Testing
**What requires human validation?**

- [ ] Optionnel : démarrage local avec PostgreSQL + création type/dossier via HTTP.
- UI/UX : N/A (pas de frontend dans la feature).

## Performance Testing
**How do we validate performance?**

- Hors scope — pas de benchmark formel pour CRUD B.

## Bug Tracking
**How do we manage issues?**

- Écarts H2 vs PG (JSONB, RETURNING) documentés dans implementation si découverts.
- Régression auth / ArchUnit = bloqueur merge.
