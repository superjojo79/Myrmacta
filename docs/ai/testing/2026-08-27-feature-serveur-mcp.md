---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: planned
created: 2026-08-27
updated: 2026-08-27
feature: serveur-mcp
phase1: 2026-08-27
---

# Testing Strategy — `serveur-mcp`

Scénarios dérivés des success criteria requirements et des composants
design. Cases `- [ ]` jusqu'à la Phase 8.

## Test Coverage Goals
**What level of testing do we aim for?**

- Backend Jacoco **BUNDLE** LINE ≥ 85 % / BRANCH ≥ 75 % (`./mvnw verify`).
- Nouvelles classes `infrastructure.mcp` + gardes domaine touchées :
  viser les branches métier (dry-run, create vs update, JWT, policy).
- Frontend : `npm run test` **et** `npm run test:e2e` en **régression**
  uniquement (**D-SPA** : pas de nouveau spec Playwright MCP).
  Ne pas déclarer Playwright hors scope : la suite existante doit rester
  verte (contrat conventions FlowForge).
- Pas de golden OpenAPI nouveau (pas d'endpoint springdoc).

## Unit Tests
**What individual components need testing?**

### Policy / gardes (domaine)

- [ ] **UT-POL-1** `DossierAccessPolicy` : `User` avec `API_SERVICE` → VIEW
  autorisé sans ACL (D-POLICY-AS).
- [ ] **UT-POL-2** `DossierAccessPolicy` : `Service` reste refusé.
- [ ] **UT-POL-3** `SlaReportAccess` : `User` `API_SERVICE` → unrestricted ;
  `Service` toujours 403.
- [ ] **UT-POL-4** `DOSSIER_VIEWER` sans ACL toujours refusé (non-régression
  reporting D-AUTHZ C).
- [ ] **UT-GUARD-1** Create/Update type : `TENANT_ADMIN` OK (non-régression).
- [ ] **UT-GUARD-2** Create/Update type : `API_SERVICE` OK.
- [ ] **UT-GUARD-3** Create/Update type : `WORKFLOW_ADMIN` seul toujours
  refusé (la garde d'écriture type reste TA | API_SERVICE).
- [ ] **UT-GUARD-4** Upsert rétention / SLA settings / purge : `API_SERVICE`
  toujours refusé (garde TA non élargie).

### Tools (infra, use cases mockés)

- [ ] **UT-DEF-1** `code` inconnu → `CreateDossierTypeUseCase`.
- [ ] **UT-DEF-2** `code` connu → `UpdateDossierTypeUseCase`.
- [ ] **UT-DEF-3** `dryRun=true` → aucun `save`.
- [ ] **UT-DEP-1** upsert config puis `deploy` (ordre).
- [ ] **UT-DEP-2** XML invalide **avant** save config (pas d'upsert).
- [ ] **UT-DEP-3** `dryRun=true` → parse OK, pas de deploy ni save.
- [ ] **UT-GET-1** `id` xor `reference` ; les deux ou aucun → erreur.
- [ ] **UT-GET-2** agrège elements + history + relations.
- [ ] **UT-GET-3** n'appelle pas `Dossier.fields()`.
- [ ] **UT-SEARCH-1** relais `SearchDossiersQuery` (size 101 rejeté).
- [ ] **UT-SLA-1** relais `GetSlaSummaryUseCase.Query`.
- [ ] **UT-TPL-1** `includeContent` forcé / défaut false.
- [ ] **UT-ACTOR-1** JWT `sub` → `AccessContext.User` rôle `API_SERVICE`.

## Integration Tests
**How do we test component interactions?**

- [ ] **IT-MCP-1** `initialize` + `tools/list` : 9 tools nommés.
- [ ] **IT-MCP-2** Bearer JWT HMAC local `API_SERVICE` + `tenant_id`
  match → `list_dossier_types` OK (MockMvc chaîne M2M).
- [ ] **IT-MCP-3** JWT `tenant_id` ≠ instance → 403 filtre existant.
- [ ] **IT-MCP-4** sans `API_SERVICE` → 403.
- [ ] **IT-MCP-5** `define_dossier_type` persisté ; GET type REST/use case
  le voit.
- [ ] **IT-MCP-6** `deploy_workflow` : config créée si absente +
  déploiement (port Flowable mocké / H2 comme les tests deploy existants).
- [ ] **IT-MCP-7** `get_dossier` champ `sensitive` masqué.
- [ ] **IT-MCP-8** `query_sla_summary` cap 10k → erreur métier
  `SLA_COHORT_TOO_LARGE`.
- [ ] **IT-MCP-9** `search_dossiers` page défaut 20.
- [ ] **IT-ARCH** ArchUnit : `domain` n'importe pas
  `org.springframework.ai` / `io.modelcontextprotocol`.

## End-to-End Tests
**What user flows need validation?**

Pas de parcours SPA nouveau.

- [ ] **E2E-REG-1** `cd frontend && npm run test:e2e` vert (suite actuelle).
- [ ] **E2E-REG-2** `cd frontend && npm run test` vert.
- [ ] Mocks `frontend/e2e/**` **non** modifiés (contrat REST SPA inchangé).

## Test Data
**What data do we use for testing?**

- JWT HMAC de test : même secret `app.security.m2m.jwt-secret` que les
  IT M2M connecteur existantes (réutiliser le helper si présent).
- Fixtures BPMN avec DI (précédent `deploiement-bpmn`).
- `DossierType` + dossier avec champ `sensitive=true`.
- H2 MODE=PostgreSQL ; pas de Testcontainers requis pour le MCP.

## Test Reporting & Coverage
**How do we verify and communicate test results?**

```bash
./mvnw verify -Dmaven.compiler.release=21
cd frontend && npm run test
cd frontend && npm run test:e2e
```

- Jacoco BUNDLE (LINE 85 / BRANCH 75).
- Compter les tests MCP ajoutés ; 0 `.only` / `.skip` nouveau.

## Manual Testing
**What requires human validation?**

- Pointer Claude Code (ou un client MCP) vers
  `http://localhost:8080/api/m2m/mcp` avec un JWT local — hors CI,
  getting-started.

## Performance Testing
**How do we validate performance?**

- Pas de bench dédié V1. Cap SLA 10k déjà testé côté reporting.
- Taille XML : même plafond que l'upload admin (IT de rejet trop gros
  si le plafond est réutilisé).

## Bug Tracking
**How do we manage issues?**

- Bugs trouvés en Phase 5/7/8 : TDD + note implementation.
- Non-régression reporting D-AUTHZ C (viewer/service REST) = bloquant
  si cassée.
