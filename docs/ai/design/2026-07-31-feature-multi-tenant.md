---
phase: design
title: System Design & Architecture
description: Define the technical architecture, components, and data models
status: draft
created: 2026-07-31
---

# System Design & Architecture

## Architecture Overview
**What is the high-level system structure?**

Tranche **socle multi-tenant config-driven** (décisions n° 13 / 14) sans activer
PostgreSQL ni Security. Le tenant est une propriété d’instance ; le modèle cible
(schéma + path + proxy) est documenté pour les features aval.

```mermaid
flowchart TB
  subgraph deploy["Déploiement cible (doc / ops)"]
    RP[Reverse-proxy statique<br/>/tenant_x → instance]
    INST[Instance Spring Boot<br/>app.tenant.id=tenant_x]
    PG[(PostgreSQL schema tenant_x)]
    RP --> INST
    INST -.->|search_path futur| PG
  end

  subgraph this["Cette feature — code livré"]
    TP[TenantProperties<br/>infrastructure.config]
    VAL[TenantIdValidator<br/>infrastructure.tenant]
    EX[InvalidTenantIdException]
    YML[application.yml<br/>app.tenant.id actif]
    TP --> YML
    VAL --> EX
  end

  INST --- TP
  VAL -.->|au provisioning futur| PG
```

### Flux conceptuel (cible, hors implémentation complète)

```mermaid
sequenceDiagram
  participant Ops as Provisioning
  participant Val as TenantIdValidator
  participant App as Instance Boot
  participant Props as TenantProperties

  Ops->>Val: validate(tenantId)
  Val-->>Ops: OK / InvalidTenantIdException
  Note over Ops: CREATE SCHEMA + config instance + bloc proxy
  App->>Props: bind app.tenant.id
  Note over App: context-path / search_path activés plus tard
```

### Responsabilités

| Zone | Rôle |
|---|---|
| `infrastructure.config.TenantProperties` | Record / `@ConfigurationProperties(prefix="app.tenant")` — `id` requis |
| `infrastructure.tenant.TenantIdValidator` | Validation regex provisioning / bootstrap futur |
| `infrastructure.tenant.InvalidTenantIdException` | Erreur d’identifiant |
| `application.yml` | `app.tenant.id` ; commentaires ou profil pour path / `connection-init-sql` |
| Docs design/implementation | Contrat reverse-proxy chemin + instance dédiée |

### Stack

Java 25 · Spring Boot 4.1 · Configuration Properties — **sans** DataSource / JPA /
Liquibase / Security / Flowable (exclusions autoconfig conservées).

## Data Models
**What data do we need to manage?**

Pas d’entité métier persistée dans cette tranche.

| Type | Emplacement | Champs / comportement |
|---|---|---|
| `TenantProperties` | `infrastructure.config` | `id: String` — requis au binding |
| `TenantIdValidator` | `infrastructure.tenant` | `validate(String)` statique ; pattern `^[a-z][a-z0-9_]{2,62}$` |
| `InvalidTenantIdException` | `infrastructure.tenant` | `RuntimeException` avec id fautif |

### Domaine workflow (inchangé structurellement)

- `StartWorkflowCommand.tenantId` / `WorkflowInstance.tenantId` restent des champs
  domaine fournis par l’appelant.
- Fixtures de test : `tenant_a` (conforme regex) à la place de `tenant-a`.
- Règle aval (doc) : les adaptateurs HTTP / messaging devront injecter
  `TenantProperties.id()` dans la commande.

## API Design
**How do components communicate?**

### Configuration

```yaml
app:
  tenant:
    id: tenant_dev   # actif — unique par instance déployée

# Cible (non actif par défaut) — commenté ou profil `tenant` :
# server:
#   servlet:
#     context-path: /${app.tenant.id}
#   forward-headers-strategy: framework
# spring:
#   datasource:
#     hikari:
#       connection-init-sql: SET search_path TO ${app.tenant.id}, public
```

Activation de `@EnableConfigurationProperties(TenantProperties.class)` (ou
enregistrement équivalent) depuis `infrastructure.config` — sans toucher au domaine.

### Validator

```text
TenantIdValidator.validate(String tenantId)
  → void | throws InvalidTenantIdException
```

Aucun endpoint HTTP. Aucun filtre servlet.

### Contrat reverse-proxy (documentation)

```apache
ProxyPass        "/tenant_a" "http://127.0.0.1:8081/tenant_a"
ProxyPassReverse "/tenant_a" "http://127.0.0.1:8081/tenant_a"
```

Le préfixe n’est pas réécrit : même path côté client et instance (quand
`context-path` sera activé). Webhooks / M2M / SPA partagent ce routage (n° 14).

### Auth (hors scope — contrat)

`TenantResolutionFilter` (feature `authentification`) comparera
`OidcUser.claim("tenant_id")` à `TenantProperties.id()` → 403 si mismatch / absent.

## Component Breakdown
**What are the major building blocks?**

- **Config :** `TenantProperties` + enablement Spring.
- **Tenant infra :** package `infrastructure.tenant` (`package-info`, validator,
  exception).
- **YAML :** `app.tenant.id` ; blocs cibles path / search_path documentés inactifs.
- **Tests fixtures :** remplacement `tenant-a` → `tenant_a` dans tests workflow.
- **Docs :** reverse-proxy + règles d’usage pour features aval.
- **Hors scope code :** bootstrap schéma, Liquibase, filtre OIDC, Flowable tenantId,
  outillage provisioning.

## Design Decisions
**Why did we choose this approach?**

| Décision | Choix | Alternatives écartées |
|---|---|---|
| Périmètre V1 feature | Socle config + validator (tranche A) | Runtime DB complet ; filtre OIDC inclus |
| `context-path` | Documenté, inactif par défaut | Activation immédiate (casse URLs/tests) |
| Domaine workflow | Inchangé + fixtures `tenant_a` | Couplage use case → `TenantProperties` |
| Packages | Properties dans `config` ; validator dans `tenant` | Tout dans `config` ; validator en `domain` |
| Binding `id` | Propriété **requise** (fail-fast au démarrage) | Défaut silencieux / optionnel |
| Isolation | Instance dédiée + schéma (n° 13) — doc | ThreadLocal / SPI Hibernate multi-tenant |
| Routage | Chemin + proxy statique (n° 14) — doc | Sous-domaine ; port dédié |

### Alternatives revalidées

- Validator en domaine : rejette — contrainte PostgreSQL/ops, pas règle métier dossier.
- Activer `connection-init-sql` sans DataSource : inutile tant que JDBC est exclu.
- Provisioning outillé (point 31) : différé explicitement.

## Non-Functional Requirements
**How should the system perform?**

- **Sécurité (fondation) :** pas de sélection de schéma depuis une entrée HTTP ; id
  d’instance figé ; validation regex avant tout SQL de provisioning futur.
- **Ops :** onboarding = provisioning (schéma + instance + bloc proxy), pas un API
  runtime dans cette feature.
- **Testabilité :** validator 100 % unitaire sans Spring ; smoke optionnel
  `TenantProperties` via contexte Spring léger si utile.
- **Compatibilité :** ne pas casser `DomainConfigTest` ni le serving racine actuel.
- **Rollout :** merge PR `feature-multi-tenant` ; activation path/datasource feature
  par feature.
- **Perf / scale :** N/A pour la tranche ; scaling répliques = évolution documentée
  dans l’analyse.

## Requirements coverage (phase 1 draft)

| Exigence | Couverture design |
|---|---|
| `TenantProperties` / `app.tenant.id` | Overview, API config, components |
| Validator + regex + exception | Data models, API validator |
| YAML path / search_path documentés inactifs | API Design yaml |
| Doc reverse-proxy | API contrat proxy |
| Fixtures `tenant_a` | Data models workflow |
| Non-goals DB / OIDC / Liquibase / Flowable | Component hors scope + décisions |
| Packages config + tenant | Responsabilités |
| Fail-fast id requis | Design Decisions binding |

## Review notes

- Draft phase 1 — à valider en phase 2 (requirements) puis phase 3 (design review).
- **Prochaine phase lifecycle :** revue requirements (`dev-requirements` phase 2).
