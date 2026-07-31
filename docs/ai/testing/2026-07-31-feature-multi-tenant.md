---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: draft
created: 2026-07-31
---

# Testing Strategy

## Test Coverage Goals
**What level of testing do we aim for?**

- **Unitaires :** 100 % de `TenantIdValidator` / `InvalidTenantIdException` (chemins
  OK + rejet).
- **Config :** smoke — `TenantProperties` bindé avec `app.tenant.id` + validation
  regex au démarrage (sans DataSource).
- **Régression :** tests workflow existants verts avec fixtures `tenant_a`.
- **ArchUnit :** règles actuelles toujours vertes (domaine sans infra/framework).
- **E2E / UI / DB :** hors scope tranche A.
- **Jacoco :** conserver le seuil bundle ≥ 80 % (`./mvnw verify`).

Alignement success criteria : validator testé ; yaml cohérent ; `./mvnw test` vert.

## Unit Tests
**What individual components need testing?**

### TenantIdValidator
Fichier prévu :
`src/test/java/com/flowforge/infrastructure/tenant/TenantIdValidatorTest.java`

- [ ] Accepte `tenant_a`, `tenant_dev`, `abc` (borne basse longueur)
- [ ] Accepte longueur max raisonnable (≤ 63 chars total selon regex)
- [ ] Refuse `null` → `InvalidTenantIdException`
- [ ] Refuse `""` / blank
- [ ] Refuse `tenant-a` (tiret)
- [ ] Refuse `Tenant_a` (majuscule)
- [ ] Refuse `1tenant` (ne commence pas par une lettre)
- [ ] Refuse `ab` (trop court : moins de 3 chars après la 1ʳᵉ lettre… *voir note*)
- [ ] Refuse identifiant > 63 caractères

> **Note regex :** `^[a-z][a-z0-9_]{2,62}$` ⇒ total 3–63 caractères. Cas `ab` =
> 2 chars → refus. Documenter chaque assertion sur ce contrat.

### TenantProperties + validation au démarrage
Fichier(s) prévu(s) :
`src/test/java/com/flowforge/infrastructure/config/TenantPropertiesTest.java`
(et/ou test du runner de validation)

- [ ] Binding `app.tenant.id=tenant_dev` expose la valeur via le bean
- [ ] Absence de `app.tenant.id` → échec de contexte / binding (fail-fast)
- [ ] `app.tenant.id=tenant-a` (invalide) → échec au démarrage via `TenantIdValidator`

## Integration Tests
**How do we test component interactions?**

- [ ] N/A obligatoire — pas de DataSource / proxy / OIDC dans cette tranche
- [ ] Régression : `DomainConfigTest` + suite workflow restent verts (path non activé)

## End-to-End Tests
**What user flows need validation?**

- [ ] N/A — pas de parcours utilisateur UI/HTTP dans cette feature

## Architecture Tests (ArchUnit)
Fichier : `src/test/java/com/flowforge/ArchitectureRulesTest.java` (existant)

- [ ] Domaine ne dépend toujours pas de Spring / JPA / Flowable / `infrastructure`
- [ ] Nouveau code `infrastructure.tenant` / `TenantProperties` n’introduit pas de
      dépendance inverse vers le domaine workflow (pas requis ArchUnit dédié si
      aucune règle nouvelle)

## Test Data
**What data do we use for testing?**

| Fixture | Valeur | Usage |
|---|---|---|
| Tenant valide | `tenant_a`, `tenant_dev` | Validator OK ; workflow tests |
| Tenant invalide | `tenant-a`, `1x`, `AB`, `""` | Validator KO |
| Config locale | `app.tenant.id=tenant_dev` | application.yml / smoke |

- Remplacer toutes les occurrences test `tenant-a` → `tenant_a` dans
  `StartWorkflowUseCaseImplTest`, `WorkflowInstanceTest`, et tout autre test touché.

## Test Reporting & Coverage
**How do we verify and communicate test results?**

```bash
./mvnw test          # Surefire + Jacoco report
./mvnw verify        # + jacoco:check (≥ 80 % lignes bundle)
```

- Couverture attendue : 100 % lignes sur `TenantIdValidator` (+ exception si logique).
- Gaps acceptables : N/A hors classes de la feature ; `FlowForgeApplication.main`
  déjà non couvert.

## Manual Testing
**What requires human validation?**

- [ ] Relire `application.yml` : `app.tenant.id` présent ; path / search_path
      clairement non actifs par défaut
- [ ] Relire doc reverse-proxy dans design / implementation
- [ ] Confirmer qu’aucun filtre Security n’a été ajouté

## Performance Testing
**How do we validate performance?**

- N/A — validator pur CPU, pas d’IO.

## Bug Tracking
**How do we manage issues?**

- Échec validator / régression ArchUnit / `jacoco:check` = bloquant PR.
- Activation accidentelle de `context-path` cassant les tests = bug P0 de cette
  feature.

## Phase notes

- Scénarios dérivés des success criteria requirements + edge cases regex.
- **Prochaine phase lifecycle après implémentation :** exécuter ces cases en phase 5/8.
- Pour l’instant (phase 1 docs) : checkboxes restent `[ ]` jusqu’à l’implémentation.
