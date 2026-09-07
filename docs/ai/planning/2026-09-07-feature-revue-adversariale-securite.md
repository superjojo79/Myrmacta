---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: draft
created: 2026-09-07
updated: 2026-09-07
feature: revue-adversariale-securite
---

# Project Planning & Task Breakdown — `revue-adversariale-securite`

## Milestones

- [ ] M1 — H1 BPMN allowlist (parser + tests exploit)
- [ ] M2 — H2 + M8 assignee (port engine, complete, runtime schema)
- [ ] M3 — M1 OIDC allowlist + M12 generate ACL
- [ ] M4 — M2 S3 yaml, M3/M4 SSRF, M5 schema keys, M9 callback/tenant
- [ ] M5 — M6/M7 webhooks, M10 SMTP, M11 caps, M13 templates/CSP
- [ ] M6 — docs GUIDE / `.env.example` / CHANGELOG ; `./mvnw verify`

## Task Breakdown

### M1 H1
- [ ] T1.1 Test parser `scriptTask` / `class` / `expression` / bean inconnu → rouge
- [ ] T1.2 `AllowedBpmnDelegates` + walk `BpmnModel` dans `FlowableBpmnParserAdapter`
- [ ] T1.3 Smoke BPMN du dépôt toujours extraits

### M2 H2/M8
- [ ] T2.1 `findAssigneeByTaskId` + tests complete
- [ ] T2.2 Controller passe `AccessContext.User`
- [ ] T2.3 Runtime schema + actor

### M3 M1/M12
- [ ] T3.1 Mapper OIDC
- [ ] T3.2 Generate use case + controller Authentication → AccessContext

### M4 SSRF / secrets / callback
- [ ] T4.1 yaml S3
- [ ] T4.2 OutboundUrlGuard CGNAT / 0.0.0.0/8
- [ ] T4.3 HttpRestConnector Host/TLS
- [ ] T4.4 assertKnownConfigKeys + mapping schema
- [ ] T4.5 reserved keys callback + ConnectorServiceTask

### M5 webhooks / smtp / caps / preview
- [ ] T5.1 ordre auth/rate-limit
- [ ] T5.2 HMAC idempotency + shared-secret replay cache
- [ ] T5.3 SmtpConnector
- [ ] T5.4 maxResponseBytes + document-storage
- [ ] T5.5 th:utext + CSP preview

### M6 close
- [ ] T6.1 GUIDE-CONCEPTION-BPMN, GUIDE-UTILISATEUR webhook HMAC, .env.example
- [ ] T6.2 `./mvnw verify` + Vitest
- [ ] T6.3 Phase 7/8/9 docs

## Dependencies

H2 avant M8 (même `findAssigneeByTaskId`). M5 mapping schema avant ou avec M5 reject unknown.

## Risks & Mitigation

- Rupture HMAC : tests + docs. Callers internes du dépôt à grepper.
- `mapping` hors schéma : instances existantes cassées si on reject avant d'ajouter le champ.
- HttpClient TLS sur IP : tester avec mock sender si HTTPS réel trop lourd.
- `CompleteTaskUseCase` signature : tous les tests appelants.

## Resources Needed

JDK 21+/25, Maven wrapper, pas de Docker requis pour les UT.
