---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: verified
updated: 2026-07-31
checked: 2026-07-31
---

# Implementation Guide

## Development Setup
**How do we get started?**

- JDK **25**, Maven Wrapper : `./mvnw test` / `./mvnw verify`
- Worktree : `.worktrees/feature-multi-tenant`
- Aucune base / Keycloak / Flowable / reverse-proxy requis pour cette tranche
- Exclusions autoconfig JPA/Security/Flowable **conservées**

## Code Structure
**How is the code organized?**

```text
com.flowforge.infrastructure
├── config
│   ├── DomainConfig                 # inchangé (workflow)
│   ├── TenantProperties             # app.tenant.id (@NotBlank)
│   ├── TenantConfig                 # @EnableConfigurationProperties + runner bean
│   └── TenantIdValidationRunner     # ApplicationRunner → TenantIdValidator
└── tenant
    ├── TenantIdValidator            # regex ^[a-z][a-z0-9_]{2,62}$
    ├── InvalidTenantIdException
    └── package-info
```

## Key Implementation Details

### Tenant = config d’instance

`TenantProperties` est un `record` `@ConfigurationProperties(prefix = "app.tenant")`
avec `@Validated` + `@NotBlank` sur `id`. Jamais résolu par requête HTTP.

### Double fail-fast

1. Binding Spring échoue si `app.tenant.id` absent / blank.
2. `TenantIdValidationRunner` appelle `TenantIdValidator.validate(id)` au boot
   (ex. `tenant-a` → `InvalidTenantIdException`).

### YAML

```yaml
app:
  tenant:
    id: tenant_dev
```

`context-path` et `connection-init-sql` sont **commentés** (modèle cible, non actifs).

### Contrat reverse-proxy (ops / features aval)

```apache
ProxyPass        "/tenant_a" "http://127.0.0.1:8081/tenant_a"
ProxyPassReverse "/tenant_a" "http://127.0.0.1:8081/tenant_a"
```

Quand `context-path` sera activé, le préfixe reste identique côté client et instance
(décision n° 14).

### Règle pour adaptateurs futurs

Les adaptateurs HTTP / messaging doivent passer `TenantProperties.id()` dans les
commandes domaine (ex. `StartWorkflowCommand`) — non enforceé dans cette feature ;
domaine workflow inchangé structurellement.

## Changed files (phase 5)

- `infrastructure.tenant` : validator + exception + `package-info`
- `infrastructure.config` : `TenantProperties`, `TenantConfig`, `TenantIdValidationRunner`
- `application.yml` : `app.tenant.id` + blocs cibles commentés
- Tests : `TenantIdValidatorTest`, `TenantIdValidationRunnerTest`, `TenantConfigTest`
- Fixtures workflow : `tenant-a` → `tenant_a` (sauf cas d’invalidité volontaires)

## Design deviations

| Écart | Sévérité | Notes |
|---|---|---|
| Aucun écart fonctionnel | — | Aligné design phase 3 (`TenantConfig` dédié) |

## Edge cases handled

- Id `null` / blank / tiret / majuscule / digit leading / trop court / trop long → rejet
- Binding sans `app.tenant.id` → échec contexte
- `DomainConfig` + `TenantConfig` cohabitent ; path racine inchangé

## Phase 7 — Check Implementation

**Evidence (frais, 2026-07-31) :**

| Commande | Résultat |
|---|---|
| `./mvnw test` | **33** tests, 0 failure, BUILD SUCCESS |
| `./mvnw verify` | Jacoco check OK — **96.7 %** lignes (59/61) |
| Lint feature | OK |
| Fixtures workflow | plus de `tenant-a` hors cas d’invalidité volontaires |
| Scope creep | aucun filtre Security / Liquibase / DataSource ajouté |
| `DomainConfig` | inchangé vs `origin/main` |

### Couverture design ↔ code

| Élément design | Statut |
|---|---|
| `TenantProperties` `@NotBlank` + prefix `app.tenant` | OK |
| `TenantConfig` + `@EnableConfigurationProperties` | OK |
| `TenantIdValidationRunner` (`ApplicationRunner`) | OK |
| `TenantIdValidator` regex `^[a-z][a-z0-9_]{2,62}$` | OK |
| `InvalidTenantIdException` message null-safe | OK |
| `DomainConfig` inchangé | OK |
| YAML `tenant_dev` + path/search_path commentés | OK |
| Doc reverse-proxy | OK (ce document) |
| Fixtures `tenant_a` | OK |
| Non-goals DB / OIDC / Flowable / provisioning | OK (absents) |
| Couverture packages feature (tenant + Tenant*) | **100 %** lignes Jacoco |

### Couverture requirements ↔ code

| Success criterion | Statut |
|---|---|
| Bean `TenantProperties` requis | OK |
| Validator + exception package `tenant` | OK |
| Validation regex au démarrage | OK |
| Tests regex OK/KO | OK (`TenantIdValidatorTest`) |
| YAML commenté path / search_path | OK |
| Doc reverse-proxy | OK |
| Fixtures `tenant_a` | OK |
| `./mvnw test` + ArchUnit verts | OK |
| Docs alignés | OK |

**Verdict phase 7 :** implementation **alignée** — prêt pour phase 8 (`dev-testing`) puis phase 9 (`dev-review`).

### Follow-ups (hors scope tranche A)

- Activation `context-path` + datasource / Liquibase bootstrap
- `TenantResolutionFilter` (feature `authentification`)
- Outillage provisioning (point 31)
