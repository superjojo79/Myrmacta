---
phase: implementation
title: Implementation — LC3
status: reviewed
created: 2026-10-01
feature: lc3-assistant-conception
---

# Implementation — LC3

- Backend : `domain/assistant/**`, `infrastructure/assistant/**`, `infrastructure/persistence/assistant/**` (dépôts Spring Data de premier niveau : les interfaces imbriquées ne sont pas détectées), `infrastructure/web/assistant/**`, `infrastructure/config/{AssistantConfig,AssistantProperties}`, migration `033-assistant.yaml`, contrat OpenAPI.
- Frontend : `api/assistant.ts`, `views/admin/AssistantView.vue`, route et lien de navigation.
- Configuration : `app.assistant.*` (`.env.example`, `DEPLOIEMENT.md` §15, `GETTING-STARTED.md` §16).
- Pièges : `scrollTo` absent de jsdom ; `deploy_workflow` / `define_dossier_type` lisent leurs arguments dans la requête MCP brute (reconstruite par le catalogue).
