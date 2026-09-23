---
phase: deployment
title: Deployment Guide
description: Deployment procedures and configuration
status: done
created: 2026-09-22
updated: 2026-09-23
feature: outillage-dmn
---

# Deployment Guide — `outillage-dmn`

Pas de nouvelle variable d’environnement, pas de migration Liquibase, pas
de service additionnel. Le déploiement applicatif standard suffit : les
quatre tools MCP et les routes REST sont enregistrés avec l’application.
L’évaluation et le redéploiement lisent les tables déjà présentes dans
`ACT_DMN_*`.
