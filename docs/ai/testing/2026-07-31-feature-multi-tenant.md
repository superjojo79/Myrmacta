---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: verified
verified: 2026-07-31
---

# Testing Strategy

## Test Coverage Goals
**What level of testing do we aim for?**

- **Unitaires :** 100 % de `TenantIdValidator` / runner / `TenantProperties` / `TenantConfig`.
- **Config :** smoke binding + fail missing/blank + **échec boot** id invalide (`tenant-a`).
- **Régression :** workflow + `DomainConfigTest` + ArchUnit.
- **E2E / UI / DB :** hors scope tranche A.
- **Jacoco :** seuil bundle ≥ 80 % (`./mvnw verify`).

Alignement success criteria : validator testé ; yaml cohérent ; `./mvnw test` / `verify` verts.

## Unit Tests
**What individual components need testing?**

### TenantIdValidator
Fichier :
`src/test/java/com/flowforge/infrastructure/tenant/TenantIdValidatorTest.java`

- [x] Accepte `tenant_a`, `tenant_dev`, `abc` (borne basse longueur)
- [x] Accepte longueur max (63 chars)
- [x] Refuse `null` → `InvalidTenantIdException` (message contient `null`)
- [x] Refuse `""` / blank
- [x] Refuse `tenant-a` (tiret) — message contient l’id
- [x] Refuse `Tenant_a` (majuscule)
- [x] Refuse `1tenant` (ne commence pas par une lettre)
- [x] Refuse `ab` / `a` (trop court)
- [x] Refuse identifiant > 63 caractères

### TenantProperties + TenantConfig / runner
Fichiers :
- `TenantConfigTest.java`
- `TenantIdValidationRunnerTest.java`

- [x] Binding `app.tenant.id=tenant_dev` expose la valeur via le bean
- [x] Absence de `app.tenant.id` → échec de contexte / binding (`@NotBlank`)
- [x] Blank `app.tenant.id=` → échec de contexte
- [x] `TenantIdValidationRunner` avec id valide → pas d’exception
- [x] `TenantIdValidationRunner` avec `tenant-a` → `InvalidTenantIdException`
- [x] Boot Spring (`SpringApplicationBuilder`, args CLI) avec `tenant-a` →
      `InvalidTenantIdException` (ApplicationRunner)
- [x] `DomainConfigTest` non régressé (TenantConfig cohabite, path non activé)

## Integration Tests
**How do we test component interactions?**

- [x] N/A DataSource / proxy / OIDC
- [x] Régression : `DomainConfigTest` + suite workflow verts
- [x] Boot fail-fast id invalide via `SpringApplicationBuilder` (voir ci-dessus)

## End-to-End Tests
**What user flows need validation?**

- [x] N/A — pas de parcours utilisateur UI/HTTP dans cette feature

## Architecture Tests (ArchUnit)
Fichier : `src/test/java/com/flowforge/ArchitectureRulesTest.java`

- [x] Domaine ne dépend pas de Spring / JPA / Flowable / `infrastructure`
- [x] Pas de dépendance inverse domaine ← infra introduite

## Test Data
**What data do we use for testing?**

| Fixture | Valeur | Usage |
|---|---|---|
| Tenant valide | `tenant_a`, `tenant_dev` | Validator OK ; workflow tests |
| Tenant invalide | `tenant-a`, `1x`, `AB`, `""` | Validator / runner / boot KO |
| Config locale | `app.tenant.id=tenant_dev` | application.yml / smoke |
| Override CLI | `--app.tenant.id=tenant-a` | boot fail-fast (priorité > yaml) |

## Test Reporting & Coverage
**How do we verify and communicate test results?**

```bash
./mvnw test          # Surefire + rapport Jacoco
./mvnw verify        # + jacoco:check (≥ 80 % lignes bundle)
```

### Evidence phase 8 (2026-07-31)

| Commande | Résultat |
|---|---|
| `./mvnw test` | **36** tests, 0 failure, BUILD SUCCESS |
| `./mvnw verify` | Jacoco check OK |
| Jacoco lignes (bundle) | **96.7 %** (59/61) — seul `FlowForgeApplication.main` non couvert |
| Packages feature (`tenant` + `Tenant*`) | **100 %** lignes |

Rapport : `target/site/jacoco/index.html`

## Manual Testing
**What requires human validation?**

- [x] Relire `application.yml` : `app.tenant.id` présent ; path / search_path commentés
- [x] Relire doc reverse-proxy dans implementation
- [x] Confirmer qu’aucun filtre Security n’a été ajouté

## Performance Testing
**How do we validate performance?**

- N/A — validator pur CPU, pas d’IO.

## Bug Tracking
**How do we manage issues?**

- Échec validator / régression ArchUnit / `jacoco:check` = bloquant PR.
- Activation accidentelle de `context-path` = bug P0.

## Phase 8 notes

- Écarts de couverture : aucun sur le code de feature ; `main()` non testé (acceptable).
- Ajouts phase 8 : messages d’exception validator ; boot fail-fast `tenant-a` via
  args CLI (`Builder.properties()` serait écrasé par `application.yml`).
- **Prochaine phase :** 9 — `dev-review`.
