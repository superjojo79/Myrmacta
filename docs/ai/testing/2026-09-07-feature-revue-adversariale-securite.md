---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: reviewed
created: 2026-09-07
updated: 2026-09-07
feature: revue-adversariale-securite
---

# Testing Strategy — `revue-adversariale-securite`

Phase 8 : critères Q1–Q12 = A couverts par les tests déjà listés. Code inchangé.

## Test Coverage Goals

- Backend : TDD par finding ; Jacoco LINE/BRANCH du dépôt tenus (`./mvnw verify`).
- Frontend : **aucun fichier SPA visé** (A-SPA). Vitest + Playwright = régression.

Preuve Phase 8 (JDK Temurin 25.0.4.1, 2026-09-07) :

- `./mvnw verify` → **1705 tests, 0 échec**, ArchUnit conforme, Jacoco `All coverage checks have been met.` (`BUILD SUCCESS`, 01:05).
- `cd frontend && npm run test` → **289/289**.
- `cd frontend && npm run test:e2e` → **65 passed** (Chromium installé dans ce bac).

## Unit Tests

### H1 — `FlowableBpmnParserAdapter`

- [x] `delegateExpression="${connectorServiceTask}"` → process id — `extractProcessDefinitionKey_allowedDelegateExpression_returnsProcessId`
- [x] `scriptTask` → `InvalidBpmnException` — `extractProcessDefinitionKey_scriptTask_rejected`
- [x] `flowable:class` → `InvalidBpmnException` — `extractProcessDefinitionKey_classImplementation_rejected`
- [x] `flowable:expression` sur serviceTask → `InvalidBpmnException` — `extractProcessDefinitionKey_expressionImplementation_rejected`
- [x] `delegateExpression="${java.lang.Runtime}"` / bean inconnu → `InvalidBpmnException` — `extractProcessDefinitionKey_unknownDelegate_rejected`

### H2 — `CompleteTaskUseCaseImpl`

- [x] assignee == actor → complete — `complete_allowsAssigneeToComplete`
- [x] autre user EDIT (assignee mismatch) → `AccessDeniedException` — `complete_rejectsWhenActorIsNotTheAssignee`
- [x] TA/WA → complete même si assignee différent — `complete_allowsAdminToCompleteAnotherUsersTask`
- [x] assignee null + actor non-admin → complete (Mockito `Optional.empty` par défaut) — `complete_forwardsVariablesToEnginePort`

### M1 — `RealmAccessAuthoritiesMapper`

- [x] `API_SERVICE` realm **non** mappé — `ignoresMachineRoleApiService`
- [x] `DOSSIER_MANAGER` mappé `ROLE_DOSSIER_MANAGER` — `mapsRealmAccessRoles_toRolePrefixedAuthorities`

### M2 — S3 yaml

- [x] Clés déplacées sous `app.storage.s3` dans `application.yml` ; `StorageConfigTest` continue de binder `StorageProperties.S3(accessKeyId, secret)`. Pas de test Spring `@ConfigurationProperties` dédié à `APP_STORAGE_S3_ACCESS_KEY` (interpolation Spring standard).

### M3 — `HttpRestConnector`

- [x] Host d'origine posé ; header `Host` appelant ignoré/écrasé — `execute_callerHostHeaderIsOverwrittenWithOriginalHostname`

### M4 — `OutboundUrlGuard`

- [x] CGNAT `100.64.0.1` rejeté — `OutboundUrlGuardTest`
- [x] `0.0.0.1` rejeté — `OutboundUrlGuardTest`

### M5 — `ManageConnectorInstanceUseCaseImpl`

- [x] clé inconnue → `ConnectorValidationException` — `create_unknownConfigKey_isRejected`
- [x] `mapping` objet accepté sur http-rest — `create_mappingConfigKey_isAccepted`

### M6 — `WebhookController`

- [x] auth fail : 401, pas de dispatch (`receive_invalidHmacReturns401WithoutDispatch`) ; `tryConsume` est **après** `authenticate` dans `WebhookController` / `CallbackWebhookController`

### M7 — authenticators

- [x] HMAC = `timestamp.idempotencyKey.body` — `WebhookAuthenticatorTest` helper `hmacHex`
- [x] shared-secret même body 2e fois (idempotency différente) → fail — `WebhookAuthenticatorTest`

### M8 — `GetTaskQuestionSchemaForTaskUseCaseImpl`

- [x] non assignee → `AccessDeniedException` — `getForTask_rejectsWhenActorIsNotTheAssignee`
- [x] non assignee **avant** mismatch formKey → 403 pas 404 — `getForTask_rejectsNonAssigneeBeforeFormKeyMismatch`

### M9

- [x] callback variables `tenantId` → `IllegalArgumentException` — `CallbackConnectorRuntimeImplTest`
- [x] `ConnectorServiceTask` execute avec `tenantProperties.id()` même si var process — `ConnectorServiceTaskTest.execute_ignoresProcessTenantIdVariable`

### M10 — `SmtpConnector`

- [x] `to` multi-destinataires / CRLF → 400 — `SmtpConnectorTest`

### M11

- [x] STORE contenu encodé trop long avant decode — `DocumentStorageConnectorTest`
- [x] http-rest body > max → erreur — `HttpRestConnectorTest` `maxResponseBytes`

### M12 — generate ACL

- [x] DM sans type listant la clé → 403 — `DocumentGenerationApiTest`
- [x] TA / DM avec type VIEW → OK — `DocumentGenerationApiTest`

### M13 — `ThymeleafTemplateSyntaxChecker`

- [x] `th:utext` → `InvalidTemplateSyntaxException` — `unescapedUtext_fails`
- [x] `[(${x})]` → idem — `unescapedInlineInterpolation_fails`

## Integration Tests

- [x] MockMvc complete non-assignee 403, complete assignee 204 — `WorkflowApiTest.configCrudAndManualStartExposeHistory`
- [x] Runtime schema : 403 non-assignee (y compris clé mismatch) ; 404 mismatch pour l’assignee — `TaskQuestionSchemaApiTest`
- [x] Webhook HMAC contrat mis à jour — `WebhookAuthenticatorTest` / `WebhookControllerTest`
- [x] Preview HTML CSP — `DocumentPreviewApiTest`

## End-to-End Tests

- [x] Pas de nouveau spec Playwright (backend-only, A-SPA)
- [x] Régression e2e : `npm run test:e2e` → **65 passed** (dont E2E-FORM / E2E-USERQ complete par l’assignee)

## Test Data

- Fixtures BPMN XML inline (`FlowableBpmnParserAdapterTest`)
- Secrets webhook ≥ 20 chars (`WebhookSecretPolicy`)
