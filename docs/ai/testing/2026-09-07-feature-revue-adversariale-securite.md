---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: draft
created: 2026-09-07
updated: 2026-09-07
feature: revue-adversariale-securite
---

# Testing Strategy — `revue-adversariale-securite`

## Test Coverage Goals

- Backend : TDD par finding ; Jacoco LINE/BRANCH du dépôt tenus (`./mvnw verify`).
- Frontend : **aucun fichier SPA visé** (A-SPA). Vitest régression. Playwright :
  régression `test:e2e` si l'environnement le permet ; complete-task e2e existants
  doivent rester verts (toujours l'assignee).

## Unit Tests

### H1 — `FlowableBpmnParserAdapter`
- [ ] `delegateExpression="${connectorServiceTask}"` → process id
- [ ] `scriptTask` → `InvalidBpmnException`
- [ ] `flowable:class` → `InvalidBpmnException`
- [ ] `flowable:expression` sur serviceTask → `InvalidBpmnException`
- [ ] `delegateExpression="${java.lang.Runtime}"` / bean inconnu → `InvalidBpmnException`

### H2 — `CompleteTaskUseCaseImpl`
- [ ] assignee == actor → complete
- [ ] autre user EDIT (simulé via use case, assignee mismatch) → `AccessDeniedException`
- [ ] TA/WA → complete même si assignee différent
- [ ] assignee null + actor non-admin → complete (EDIT déjà fait par le contrôleur)

### M1 — `RealmAccessAuthoritiesMapper`
- [ ] `API_SERVICE` realm **non** mappé
- [ ] `DOSSIER_MANAGER` mappé `ROLE_DOSSIER_MANAGER`

### M2 — `StorageProperties` / yaml (test de binding Spring)
- [ ] `APP_STORAGE_S3_ACCESS_KEY` → `storage.s3.accessKeyId`

### M3 — `HttpRestConnector`
- [ ] Host d'origine posé ; header `Host` appelant ignoré/écrasé

### M4 — `OutboundUrlGuard`
- [ ] `http://100.64.1.1/` (profil local http) ou HTTPS CGNAT rejeté
- [ ] `0.0.0.1` rejeté

### M5 — `ManageConnectorInstanceUseCaseImpl`
- [ ] clé inconnue → `ConnectorValidationException`
- [ ] `mapping` objet accepté sur http-rest

### M6 — `WebhookController`
- [ ] auth fail : `rateLimiter.tryConsume` jamais appelé (ou appelé après échec : 0)

### M7 — authenticators
- [ ] HMAC sans idempotency dans payload → fail
- [ ] HMAC avec timestamp.idempotency.body → ok
- [ ] shared-secret même body 2e fois (idempotency différente) → fail

### M8 — `GetTaskQuestionSchemaForTaskUseCaseImpl`
- [ ] non assignee → `AccessDeniedException`

### M9
- [ ] callback variables `tenantId` → `IllegalArgumentException`
- [ ] `ConnectorServiceTask` execute avec `tenantProperties.id()` même si var process

### M10 — `SmtpConnector`
- [ ] `to` multi-destinataires / CRLF → 400

### M11
- [ ] STORE contenu encodé trop long avant decode
- [ ] http-rest body > max → erreur

### M12 — `GenerateDocumentUseCaseImpl`
- [ ] DM sans type listant la clé → `AccessDeniedException`
- [ ] TA → OK

### M13 — `ThymeleafTemplateSyntaxChecker`
- [ ] `th:utext` → `InvalidTemplateSyntaxException`
- [ ] `[(${x})]` → idem

## Integration Tests

- [ ] MockMvc complete task non-assignee 403 (si IT existant facilement extensible)
- [ ] Webhook HMAC contrat mis à jour dans `WebhookAuthenticatorTest` / controller tests

## End-to-End Tests

- [ ] Pas de nouveau spec Playwright (backend-only)
- [ ] Régression e2e existante si binaire dispo

## Test Data

- Fixtures BPMN XML inline (comme `FlowableBpmnParserAdapterTest`)
- Secrets webhook ≥ 20 chars (`WebhookSecretPolicy`)
