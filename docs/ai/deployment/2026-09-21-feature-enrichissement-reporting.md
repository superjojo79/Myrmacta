---
phase: deployment
title: Deployment Strategy
description: Define deployment process, infrastructure, and release procedures
status: noted
created: 2026-09-21
updated: 2026-09-21
feature: enrichissement-reporting
---

# Deployment Strategy — `enrichissement-reporting`

Pas de nouveau service. Les jobs tournent dans l’instance Spring Boot
déjà provisionnée (une instance = un tenant). Le storage est
`DocumentStoragePort` (filesystem ou S3 selon le déploiement existant).

Pas de variable d’environnement nouvelle : les interrupteurs sont des
réglages tenant, inactifs par défaut. Un déploiement n’envoie donc ni
alerte ni fichier tant qu’un `TENANT_ADMIN` n’active pas la fonction.

La migration Liquibase part au démarrage, comme les changelogs déjà
appliqués au schéma du tenant.
