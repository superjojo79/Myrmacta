---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: not-started
created: 2026-08-27
updated: 2026-08-27
feature: serveur-mcp
phase1: 2026-08-27
---

# Implementation Guide — `serveur-mcp`

Pas d'implémentation en Phase 1. Rédigé en Phases 5–7.

## Development Setup

Workspace prévu : branche `cursor/serveur-mcp-e12d`, no-worktree.
Stack : Java 25 / Spring Boot 4.1. Commandes :

```bash
./mvnw verify -Dmaven.compiler.release=21
cd frontend && npm run test && npm run test:e2e
```

## Code Structure

Cible : `com.flowforge.infrastructure.mcp` (voir design draft).
Domaine : touches limitées aux gardes **D-POLICY-AS**.

## Implementation Notes

Suivre TDD (`tdd` + `verify`). Ne pas lire `Dossier.fields()` depuis
un tool MCP.
