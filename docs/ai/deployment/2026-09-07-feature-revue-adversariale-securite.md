---
phase: deployment
title: Deployment Strategy
description: Define deployment process, infrastructure, and release procedures
status: reviewed
created: 2026-09-07
updated: 2026-09-07
feature: revue-adversariale-securite
---

# Deployment Strategy — `revue-adversariale-securite`

## Infrastructure

Inchangé : une instance Spring Boot = un tenant. Pas de nouveau service.

## Breaking change HMAC (M7)

Les partenaires qui signent `timestamp + "." + body` doivent passer à
`timestamp + "." + Idempotency-Key + "." + body`. Documenté dans
`GUIDE-UTILISATEUR.md` et `analysis doc/06-connecteurs.md`. Déployer **après**
avoir prévenu les intégrateurs (401 sinon).

## Environment Configuration

- `APP_STORAGE_S3_ACCESS_KEY` / `SECRET` : désormais bindés sous `app.storage.s3`
  (M2). `.env.example` déjà aligné — pas de nouvelle variable.
- Aucune migration Liquibase.

## Deployment Pipeline

CI GitHub Actions `./mvnw verify` (Jacoco + ArchUnit) + job frontend.
Rollback : revert git ; HMAC redevient l’ancien contrat (partenaires déjà migrés cassés
dans l’autre sens).

## Secrets Management

Inchangé. Clés connecteur hors schéma désormais **rejetées** (M5) — un `backupToken`
en clair en base n’est plus créable via l’API.
