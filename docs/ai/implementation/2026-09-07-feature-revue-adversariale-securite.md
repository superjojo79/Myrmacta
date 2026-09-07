---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: draft
created: 2026-09-07
updated: 2026-09-07
feature: revue-adversariale-securite
---

# Implementation Guide — `revue-adversariale-securite`

## Development Setup

Branche `cursor/revue-adversariale-securite-da9e`. No-worktree (workspace cloud).
Task tracing `ai-devkit task` : **indisponible** (`unknown command 'task'`).

## Code Structure

Corrections localisées : pas de nouveau package domaine au-delà d'une constante
`AllowedBpmnDelegates` dans `domain.workflow`.

## Implementation Notes

TDD par finding (planning M1–M5). `InvalidBpmnException` déjà mappée HTTP.
`AccessDeniedException` déjà 403.

HMAC : grepper `signedPayload` / `X-Webhook-Timestamp` dans tests et docs.

## Security Notes

Cette feature **est** le durcissement. Ne pas logger secrets webhook, HMAC, S3.
