---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: completed
created: 2026-08-07
updated: 2026-08-07
design_aligned: 2026-08-07
phase2_sync: 2026-08-07
phase3_sync: 2026-08-07
phase4_sync: 2026-08-07
phase5_evidence: 2026-08-07
phase8: 2026-08-07
phase9: 2026-08-07
---

# Testing Strategy

## Test Coverage Goals
**What level of testing do we aim for?**

- **Unitaires domaine :** `SendNotificationUseCase` ; Manage webhook ;
  `NotificationPayloadJson` ; `NotificationChannel.parse`.
- **Unitaires infra :** `OutboundUrlGuard` (A13) ; envelope + HMAC ;
  webhook adapter single-shot ; SSE multi + heartbeat ; SMTP ;
  cipher encrypt/decrypt ; exception handler 409.
- **Relais :** lock / backoff / DEAD / BROWSER skip / missing config /
  invalid JSON → recordFailure ; purge.
- **Delegate :** `NotificationDispatchTask`.
- **IT H2 + MockMvc :** admin (201, mask, soft-delete/restore, SSRF, 409
  duplicate, http localhost on profile `test`) ; send 202 ; SSE ; Liquibase.
- **Régression :** ArchUnit ; Jacoco ≥ 80 %.
- **Hors scope :** SMTP réel CI, Make/n8n réels, Testcontainers multi-pod SSE.

## Unit Tests

### SendNotificationUseCase
- [x] channels `[EMAIL, WEBHOOK]` → 1 `savePendingBatch` (2 writes)
- [x] validation multi-canal échoue → aucun write
- [x] channels vide / inconnu → erreur métier
- [x] EMAIL sans `to` / `subject` / `body` (blank) → erreur (D-EMAIL-VAL)
- [x] WEBHOOK sans configId ou config disabled/deleted → erreur (A8)
- [x] BROWSER sans userId → erreur
- [x] payload null → traité comme `{}` ; snapshot WEBHOOK+BROWSER
- [x] Domaine sans dépendance Spring Mail / RestClient / SSE (ArchUnit)

### NotificationPayloadJson / NotificationChannel
- [x] Escape quotes / backslash / newline
- [x] Nested map + list + null + boolean + number
- [x] browser null payload → `{}`
- [x] parse case-insensitive ; reject blank/unknown

### OutboundUrlGuard
- [x] https public OK
- [x] http public rejeté (même profil test)
- [x] http loopback OK seulement si allowLocalHttp
- [x] localhost / privés / metadata rejetés en https
- [x] IPv4-mapped (`::ffff:`), ULA (`fc`/`fd`), link-local (`fe80`), IPv4 décimal privés rejetés

### WebhookRestAdapter
- [x] Body envelope `{type,outboxId,at,payload}` (DD-ENVELOPE)
- [x] Appel RestClient unique ; Content-Type JSON
- [x] Header HMAC présent ssi secret (A9)
- [x] Erreur HTTP → exception (pas de retry interne)
- [x] Guard appelé avant HTTP

### SmtpEmailAdapter
- [x] JavaMailSender text/plain to/subject/body
- [x] Erreur transport → exception

### SseNotificationAdapter
- [x] push user connecté → true + event `notification`
- [x] deux emitters même sub → les deux reçoivent (DD-SSE-MULTI)
- [x] push user absent → false (A2)
- [x] heartbeat ping périodique (DD-HEARTBEAT)

### ConnectorSecretCipher extension
- [x] encrypt/decrypt round-trip string (DD-CIPHER)
- [x] password-fields existants non régressés

### NotificationOutboxRelay
- [x] EMAIL/WEBHOOK/BROWSER success → PROCESSED
- [x] WEBHOOK missing/disabled → DEAD (A8)
- [x] WEBHOOK fail → recordFailure
- [x] BROWSER false → PROCESSED skipped
- [x] invalid payload JSON → recordFailure
- [x] purge retention appelée

### NotificationDispatchTask
- [x] Variables CSV/list channels → use case
- [x] Variable manquante canal → échec delegate

### Exception handler
- [x] ConflictException → 409
- [x] DataIntegrityViolation → 409 sanitized

## Integration Tests

### MockMvc admin webhooks
- [x] POST create **201** ; secret masqué
- [x] GET liste hors soft-deleted ; DELETE + restore `restore:true`
- [x] PATCH update URL / enable
- [x] Non-`TENANT_ADMIN` → 403 ; anonyme → 401 ; CSRF → 403
- [x] URL SSRF (metadata) → 400
- [x] `http://localhost` autorisé profil `test` (A13)
- [x] duplicate name → **409**

### MockMvc send / SSE
- [x] POST send 202 + outboxIds ; authz TA|WA ; DOSSIER_MANAGER 403
- [x] Body invalide → 400
- [x] SSE subscribe auth ; anonyme 401

### Liquibase / persistence
- [x] Tables `notification_webhook` + `notification_outbox`
- [x] Index unique actif `(tenant_id, name)` webhook
- [x] `savePendingBatch` multi-lignes
- [x] Round-trip JSON + soft-delete + DEAD/backoff

## Test files

| Suite | Path |
|---|---|
| Send UC | `domain/notification/usecase/SendNotificationUseCaseImplTest` |
| Manage UC | `domain/notification/usecase/ManageNotificationWebhookUseCaseImplTest` |
| Payload JSON | `domain/notification/model/NotificationPayloadJsonTest` |
| Channel parse | `domain/notification/model/NotificationChannelTest` |
| Guard / Webhook / SMTP / SSE | `infrastructure/notification/*Test` |
| Relay | `infrastructure/notification/NotificationOutboxRelayTest` |
| Cipher | `infrastructure/connector/crypto/EnvMasterKeyCipherTest` |
| Properties | `infrastructure/config/NotificationPropertiesTest` |
| Persistence / Schema | `infrastructure/persistence/notification/*IT` |
| Admin / Send / SSE API | `infrastructure/web/notification/*ApiTest` |
| Exception handler | `infrastructure/web/notification/NotificationExceptionHandlerTest` |
| DispatchTask | `infrastructure/flowable/NotificationDispatchTaskTest` |

## Coverage Targets

| Zone | Cible | Phase 8 | Phase 9 |
|---|---|---|---|
| Projet Jacoco instructions | ≥ 80 % | ≈ 88 % | **87.8 %** |
| ArchUnit | OK | OK | OK |
| Régression | 0 fail | 487 / 0 | **490 / 0** |

## Phase 8 evidence (2026-08-07)

```text
JAVA_HOME=/tmp/jdk/jdk-25.0.4+7 ./mvnw verify
→ Tests run: 487, Failures: 0, Errors: 0
→ Jacoco instructions ≈ 88 %
→ spring.profiles.active=test (A13 IT)
→ Added: NotificationPayloadJsonTest, NotificationChannelTest,
  NotificationExceptionHandlerTest ; admin 409 + localhost http ;
  relay invalid JSON
```

## Phase 9 evidence (2026-08-07)

```text
JAVA_HOME=/tmp/jdk/jdk-25.0.4+7 ./mvnw verify
→ Tests run: 490, Failures: 0, Errors: 0
→ Jacoco instructions 87.8 %
→ SSRF mapped/ULA/decimal ; savePendingBatch ; unique webhook index
→ Lifecycle phase 9 READY — prêt à merger
```

## Sources

- Requirements + design `notifications` reviewed
- Implementation phase 7 checked / aligned ; phase 9 review READY
