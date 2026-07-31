---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: implemented
updated: 2026-07-31
---

# Testing Strategy

## Test Coverage Goals
**What level of testing do we aim for?**

- **Unitaires :** 100 % de `TenantIdValidator` / chemins runner.
- **Config :** smoke `TenantProperties` + fail missing/blank.
- **Régression :** workflow + `DomainConfigTest` + ArchUnit.
- **Jacoco :** seuil bundle ≥ 80 % — **96.7 %** lignes (59/61) après phase 5.

## Unit Tests
**What individual components need testing?**

### TenantIdValidator
Fichier :
`src/test/java/com/flowforge/infrastructure/tenant/TenantIdValidatorTest.java`

- [x] Accepte `tenant_a`, `tenant_dev`, `abc` (borne basse longueur)
- [x] Accepte longueur max (63 chars)
- [x] Refuse `null` → `InvalidTenantIdException`
- [x] Refuse `""` / blank
- [x] Refuse `tenant-a` (tiret)
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
- [x] `TenantIdValidationRunner` avec id valide → pas d’exception
- [x] `TenantIdValidationRunner` avec `tenant-a` → `InvalidTenantIdException`
- [x] `DomainConfigTest` non régressé (TenantConfig cohabite, path non activé)
- [x] Blank `app.tenant.id=` → échec de contexte

## Integration Tests
**How do we test component interactions?**

- [x] N/A obligatoire — pas de DataSource / proxy / OIDC dans cette tranche
- [x] Régression : `DomainConfigTest` + suite workflow restent verts (path non activé)

## End-to-End Tests
**What user flows need validation?**

- [x] N/A — pas de parcours utilisateur UI/HTTP dans cette feature

## Architecture Tests (ArchUnit)
Fichier : `src/test/java/com/flowforge/ArchitectureRulesTest.java` (existant)

- [x] Domaine ne dépend toujours pas de Spring / JPA / Flowable / `infrastructure`
- [x] Nouveau code infra n’introduit pas de dépendance inverse domaine ← infra

## Test Data
**What data do we use for testing?**

| Fixture | Valeur | Usage |
|---|---|---|
| Tenant valide | `tenant_a`, `tenant_dev` | Validator OK ; workflow tests |
| Tenant invalide | `tenant-a`, `1x`, `AB`, `""` | Validator / runner KO |
| Config locale | `app.tenant.id=tenant_dev` | application.yml / smoke |

## Test Reporting & Coverage
**How do we verify and communicate test results?**

```bash
./mvnw test          # Surefire + Jacoco report
./mvnw verify        # + jacoco:check (≥ 80 % lignes bundle)
```

### Evidence phase 5 (2026-07-31)

| Commande | Résultat |
|---|---|
| `./mvnw test` | **33** tests, 0 failure, BUILD SUCCESS |
| `./mvnw verify` | Jacoco check OK — **96.7 %** lignes (59/61) |

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

## Phase notes

- Phase 5 : scénarios exécutés et cochés.
- **Prochaine phase lifecycle :** 7 check implementation, puis formaliser phase 8 si besoin.
