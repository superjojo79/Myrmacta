---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: implemented
updated: 2026-07-31
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

## Evidence (phase 5)

| Commande | Résultat |
|---|---|
| `./mvnw test` | **33** tests, 0 failure, BUILD SUCCESS |
| `./mvnw verify` | Jacoco check OK ; lignes bundle **96.7 %** (59/61) |
| Lint feature | OK |

**Prochaine phase :** 6 (planning reconcile — reflété) puis 7 check implementation.
