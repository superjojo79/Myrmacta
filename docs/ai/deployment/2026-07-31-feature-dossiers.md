---
phase: deployment
title: Deployment Strategy
description: Define deployment process, infrastructure, and release procedures
status: drafted
updated: 2026-08-01
---

# Deployment Strategy

## Infrastructure
**Where will the application run?**

Instance Spring Boot dédiée par tenant (décision n° 13). PostgreSQL multi-schéma ;
schéma `app.tenant.id` **pré-créé** avant démarrage (Liquibase ne crée pas le schéma).

## Environment Configuration

| Variable | Rôle |
|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | Datasource PostgreSQL |
| `app.tenant.id` | Identifiant tenant (= nom de schéma) |
| `OIDC_ISSUER_URI` / `OIDC_CLIENT_SECRET` | Auth BFF (existant) |

Hikari : `SET search_path TO ${app.tenant.id}, public`.  
`spring.jpa.hibernate.ddl-auto=none` — schéma via Liquibase uniquement.

## Database Migrations

Changelog : `db/changelog/db.changelog-master.yaml` → `001-dossier-tables`
(tables `dossier_type`, `dossier`, `dossier_type_acl`, `dossier_reference_seq`).

### Rollback

- Tables Liquibase standard : rollback auto `dropTable` si besoin.
- `001-dossier-reference-seq` : rollback SQL explicite `DROP TABLE dossier_reference_seq`.
- Forward-only recommandé en prod ; backup schéma tenant avant migrate.

## Deployment Steps

1. Provisionner / vérifier schéma PostgreSQL du tenant.
2. Déployer artefact ; renseigner `DB_*` + `app.tenant.id` + OIDC.
3. Boot → Liquibase applique les changesets dossiers.
4. Smoke : `POST /api/dossier-types` (admin) puis `POST /api/dossiers`.

## Secrets Management

Secrets DB/OIDC via env / vault ops — pas de défauts secrets en prod.

## Rollback Plan

1. Redéployer version précédente de l’app.
2. Si besoin : rollback Liquibase du changeset dossiers (ou restore dump schéma).
3. Pas de migration de données métier destructive dans cette tranche.
