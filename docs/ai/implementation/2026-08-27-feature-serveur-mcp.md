---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: not-started
created: 2026-08-27
updated: 2026-08-27
feature: serveur-mcp
phase1: 2026-08-27
phase3: 2026-08-27
---

# Implementation Guide — `serveur-mcp`

Pas d'implémentation avant Phase 4 (plan) puis Phase 5 TDD. Cible :
design Phase 3 `docs/ai/design/2026-08-27-feature-serveur-mcp.md`.

## Development Setup

Workspace : branche `cursor/serveur-mcp-e12d`, no-worktree.
Stack : Java 25 / Spring Boot 4.1. Commandes :

```bash
./mvnw verify -Dmaven.compiler.release=21
cd frontend && npm run test && npm run test:e2e
```

## Code Structure

- `com.flowforge.infrastructure.mcp` — tools + factory JWT.
- Domaine : gardes **D-POLICY-AS**, `requireTenantAdminOrApiService`,
  `createdBy`/`updatedBy`, `CheckDossierTypeSchemaCompatibilityUseCase`.
- Liquibase `017-mcp-audit-authors.yaml`.
- `pom.xml` : BOM Spring AI 2.0.0 + starter MCP WebMVC.

## Implementation Notes

TDD (`tdd` + `verify`). Ne pas lire `Dossier.fields()` depuis un tool
MCP. Ne pas élargir `requireTenantAdmin`. Ne pas ajouter de starter
chat.
