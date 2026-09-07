---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: reviewed
created: 2026-09-07
updated: 2026-09-07
feature: revue-adversariale-securite
---

# Implementation Guide — `revue-adversariale-securite`

## Development Setup

Branche `cursor/revue-adversariale-securite-da9e`. No-worktree (workspace cloud).
Task tracing `ai-devkit task` : **indisponible** (`unknown command 'task'`).
PR : https://github.com/superjojo79/FlowForge/pull/113

## Code Structure

Corrections localisées. Nouveaux types domaine : `AllowedBpmnDelegates`, `TaskAssignmentGuard`.
`HttpRestConnector` envoie via `HttpURLConnection` (Host + TLS hostname), plus le JDK `HttpClient`.

## Implementation Notes

| Finding | Fichiers clés |
|---|---|
| H1 | `FlowableBpmnParserAdapter`, `AllowedBpmnDelegates` |
| H2 / M8 | `TaskAssignmentGuard`, `CompleteTaskUseCaseImpl`, `GetTaskQuestionSchemaForTaskUseCaseImpl` |
| M1 | `RealmAccessAuthoritiesMapper` |
| M2 | `application.yml` `app.storage.s3` |
| M3 | `HttpRestConnector` Host overwrite + `HttpURLConnection` |
| M4 | `OutboundUrlGuard.isBlockedIpv4Literal` CGNAT + `0.0.0.0/8` |
| M5 | `ManageConnectorInstanceUseCaseImpl.assertKnownConfigKeys` + schéma `mapping` |
| M6 | `WebhookController` / `CallbackWebhookController` : auth puis rate-limit |
| M7 | HMAC `timestamp.idempotencyKey.body` ; cache replay shared-secret 5 min |
| M9 | `CallbackConnectorRuntimeImpl.assertNoReservedKeys` ; `ConnectorServiceTask` tenantProperties |
| M10 | `SmtpConnector` `InternetAddress(..., true)` |
| M11 | `maxResponseBytes` ; taille Base64 avant decode ; cap RETRIEVE |
| M12 | `DocumentGenerationController.assertCanGenerate` (TA/WA libres, DM si type VIEW) |
| M13 | `ThymeleafTemplateSyntaxChecker` ; header CSP preview |

Écarts vs design : M12 dans le **contrôleur** (pas le use case) pour ne pas casser
`DocumentGenerationTask` BPMN. HMAC rupture documentée GUIDE / analysis doc.

## Security Notes

Cette feature **est** le durcissement. Ne pas logger secrets webhook, HMAC, S3.

