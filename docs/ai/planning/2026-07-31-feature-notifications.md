---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: completed
created: 2026-08-07
updated: 2026-08-07
design_aligned: 2026-08-07
phase2_sync: 2026-08-07
phase3_sync: 2026-08-07
phase4: 2026-08-07
phase5: 2026-08-07
phase6: 2026-08-07
sequencing: layered
---

# Project Planning & Task Breakdown

Plan **phase 4** — dérivé des docs `requirements` / `design` (`reviewed`)
et `testing` (`draft`, scénarios `- [ ]`). Aucune tâche d’implémentation
démarrée.

Traçage `task` CLI : **indisponible** (`npx ai-devkit@latest task list --json`
→ `unknown command 'task'`).

## Milestones
**What are the major checkpoints?**

- [x] **M1 — Domaine** : modèles, ports, send + manage webhook use cases
- [x] **M2 — Persistance** : Liquibase 006, JPA, cipher encrypt/decrypt
- [x] **M3 — Canaux** : Email, Webhook (SSRF/envelope/HMAC), SSE multi+heartbeat
- [x] **M4 — Relais** : lock / backoff / DEAD / purge
- [x] **M5 — HTTP** : admin + send 202 + SSE stream
- [x] **M6 — Flowable** : `NotificationDispatchTask`
- [x] **M7 — Preuves & docs** : verify, Jacoco ≥ 80 %, sync docs

## Task Breakdown
**What specific work needs to be done?**

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Domaine

- [x] **T1. Modèles + ports in/out notification**
  - **Outcome :** `NotificationChannel`, `NotificationEvent`,
    `NotificationOutboxRecord` / status, `NotificationWebhookConfig`,
    ports `EmailPort` / `WebhookPort` / `BrowserPushPort` /
    `NotificationOutboxRepository` / `NotificationWebhookRepository`,
    ports in `SendNotificationUseCase` + `ManageNotificationWebhookUseCase`,
    exceptions métier.
  - **Deps :** aucune.
  - **Validation :** tests modèles / contrats ports légers ; ArchUnit package
    `domain.notification` sans Spring Mail/RestClient/SSE.
  - **Tests :** prépare SendNotificationUseCase ; ArchUnit.

- [x] **T2. `SendNotificationUseCaseImpl` + fan-out**
  - **Outcome :** validation D-EMAIL-VAL / A8 / A12 ; `channels[]` → N
    `savePending` en TX ; snapshots payloadJson ; **pas d’I/O canal**.
  - **Deps :** T1.
  - **Validation :** `SendNotificationUseCaseImplTest` (tous cas unitaires
    testing § SendNotificationUseCase + edge A12).
  - **Tests :** SendNotificationUseCase ; edge channels dupliqués.

- [x] **T3. `ManageNotificationWebhookUseCaseImpl`**
  - **Outcome :** create/get/list/update/softDelete/restore ; name unique
    parmi actives ; délègue cipher + validation URL au wiring infra
    (ou port `OutboundUrlValidator` si besoin domaine pur — sinon validate
    dans adapter admin avant UC, documenté en impl).
  - **Deps :** T1.
  - **Validation :** tests unitaires manage (soft-delete/restore/not found).
  - **Tests :** Manage webhook use case (coverage goals).

### M2 — Persistance & crypto

- [x] **T4. Liquibase `006-notification-tables` + entities JPA**
  - **Outcome :** `notification_webhook` + `notification_outbox`
    (`next_attempt_at`, attempts, indexes).
  - **Deps :** T1.
  - **Validation :** SchemaIT / migrate IT.
  - **Tests :** Liquibase / persistence.

- [x] **T5. Adapters repos + extension `ConnectorSecretCipher`**
  - **Outcome :** JPA adapters webhook/outbox (`lockNextPending`,
    `recordFailure`+`nextAttemptAt`, `markProcessed`/`markDead`,
    `purgeTerminalOlderThan`) ; **DD-CIPHER** : ajouter
    `encrypt`/`decrypt` sur le port + `EnvMasterKeyCipher` ; secret
    webhook via encrypt/decrypt ; **non-régression** password-fields
    connecteurs.
  - **Deps :** T4.
  - **Validation :** persistence IT round-trip ; cipher string round-trip ;
    tests connecteurs crypto existants verts.
  - **Tests :** ConnectorSecretCipher extension ; Cipher reuse ; Liquibase
    round-trip ; lockNextPending nextAttemptAt.

- [x] **T6. Config `NotificationProperties` + `pom` mail**
  - **Outcome :** `app.notification.outbox.*` / webhook timeouts /
    `sse.heartbeat-interval` ; `spring-boot-starter-mail` (A11) ; beans
    config.
  - **Deps :** aucune (peut parallèle T4).
  - **Validation :** bind properties test ; compile avec mail.
  - **Tests :** support T8/T6-mail/T7.

### M3 — Canaux

- [x] **T7. `OutboundUrlGuard` + `WebhookRestAdapter`**
  - **Outcome :** SSRF D-SSRF/A13 ; envelope DD-ENVELOPE ; HMAC A9 ;
    1 HTTP RestClient ; pas de redirect.
  - **Deps :** T1, T5 (cipher decrypt pour secret).
  - **Validation :** OutboundUrlGuardTest + WebhookRestAdapterTest.
  - **Tests :** OutboundUrlGuard ; WebhookRestAdapter.

- [x] **T8. `SmtpEmailAdapter`**
  - **Outcome :** JavaMailSender `text/plain` ; échec transport → exception.
  - **Deps :** T1, T6.
  - **Validation :** SmtpEmailAdapterTest (mock ou GreenMail).
  - **Tests :** SmtpEmailAdapter.

- [x] **T9. `SseNotificationAdapter`**
  - **Outcome :** `Map<sub, Set<emitter>>` (DD-SSE-MULTI) ; push true/false ;
    heartbeat ping (DD-HEARTBEAT).
  - **Deps :** T1, T6 (intervalle).
  - **Validation :** SseNotificationAdapterTest (multi + heartbeat + miss).
  - **Tests :** SseNotificationAdapter.

### M4 — Relais

- [x] **T10. `NotificationOutboxRelay` scheduler**
  - **Outcome :** poll `lockNextPending` ; dispatch EMAIL/WEBHOOK/BROWSER ;
    backoff 1s/2s/4s ; DEAD à maxAttempts=3 ; BROWSER skip A2 ; config
    absente au relais → DEAD A8 ; purge retention DD-PURGE.
  - **Deps :** T2, T5, T7–T9.
  - **Validation :** NotificationOutboxRelayTest (+ IT optionnel EMAIL).
  - **Tests :** NotificationOutboxRelay ; Relais IT ; edge config
    supprimée entre send et relais.

### M5 — HTTP

- [x] **T11. Admin API `/api/notification-webhooks`**
  - **Outcome :** CRUD + restore ; **201** create ; secret masqué ; authz
    `TENANT_ADMIN` ; CSRF ; SSRF → 400 ; ProblemDetail.
  - **Deps :** T3, T5, T7.
  - **Validation :** MockMvc admin (tous cas testing § admin).
  - **Tests :** MockMvc admin webhooks.

- [x] **T12. `POST /api/notifications/send`**
  - **Outcome :** **202** + `outboxIds` ; authz TA|WA ; CSRF ; validation 400.
  - **Deps :** T2, T5.
  - **Validation :** MockMvc send.
  - **Tests :** MockMvc send.

- [x] **T13. `GET /api/notifications/stream`**
  - **Outcome :** SSE BFF auth ; register emitter ; 401 anonyme.
  - **Deps :** T9.
  - **Validation :** tests stream / subscribe.
  - **Tests :** SSE integration.

### M6 — Flowable

- [x] **T14. `NotificationDispatchTask`**
  - **Outcome :** variables D-FLOWABLE → `SendNotificationUseCase` ;
    échec si champs canal manquants.
  - **Deps :** T2.
  - **Validation :** NotificationDispatchTaskTest.
  - **Tests :** NotificationDispatchTask.

### M7 — Preuves & docs

- [x] **T15. Régression `./mvnw verify` + Jacoco ≥ 80 %**
  - **Deps :** T1–T14.
  - **Validation :** exit 0 ; Jacoco instructions ; ArchUnit ; suites
    connecteurs/workflow/documents non régressées.
  - **Tests :** Coverage targets ; régression.

- [x] **T16. Docs implementation + sync planning/testing**
  - **Outcome :** cocher scénarios testing réalisés ; planning statuses ;
    notes implementation.
  - **Deps :** T15.
  - **Validation :** `npx ai-devkit lint --feature notifications`.

## Traceability testing → tasks

| Scénario testing | Tâche(s) |
|---|---|
| SendNotificationUseCase (+ A12 dédup) | T1, T2 |
| ArchUnit domaine notification | T1, T15 |
| Manage webhook use case | T3, T11 |
| OutboundUrlGuard | T7, T11 |
| WebhookRestAdapter (envelope/HMAC) | T7 |
| SmtpEmailAdapter | T8 |
| SseNotificationAdapter (multi/heartbeat/A2) | T9, T13 |
| ConnectorSecretCipher encrypt/decrypt | T5 |
| Cipher reuse (admin chiffré) | T5, T11 |
| NotificationOutboxRelay (+ lock/backoff/DEAD/purge/A8) | T10 |
| NotificationDispatchTask | T14 |
| MockMvc admin webhooks | T11 |
| MockMvc send | T12 |
| SSE subscribe / push / 401 | T13, T9, T10 |
| Liquibase / persistence round-trip | T4, T5 |
| Relais IT optionnel EMAIL | T10 |
| Edge secret update N/A / gros payload smoke | T7, T12 (soft) |
| Jacoco ≥ 80 % + régression | T15–T16 |

**Couverture :** chaque section scénario du testing doc a ≥ 1 tâche.

## Dependencies
**What needs to happen in what order?**

```text
T1 → T2 → T12, T14
T1 → T3 → T11
T1 → T4 → T5 → T7, T10, T11, T12
T6 ∥ T4  (puis T6 → T8, T9)
T2 + T5 + T7 + T8 + T9 → T10
T9 → T13
T1–T14 → T15 → T16
```

External : SMTP réel hors CI (A10) ; pas de Make/n8n réels.

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| Extension cipher casse connecteurs | T5 inclut non-régression password-fields ; verify T15 |
| Double-envoi outbox | `lockNextPending` (DD-LOCK) calqué workflow |
| SSE MockMvc flaky | Unit adapter prioritaire ; smoke controller léger |
| Régression `outbox_event` workflow | Ne pas modifier table/relais workflow |
| SSRF faux positifs IT | A13 profil test autorise http localhost |
| Scope creep publisher/inbox | Non-goals D-PUBLISHER / D-SSE — ne pas implémenter |

## Resources Needed

- Worktree `feature-notifications` (actif).
- Patterns : `OutboxRelayScheduler`, `EnvMasterKeyCipher`,
  `WebhookControllerTest`, `DocumentGenerationApiTest`.
- Docs AI feature déjà `reviewed` (req/design).

## Progress summary

Phase 5–9 : **T1–T16 done** ; implementation **aligned** ; testing
**completed** ; review **READY**.  
Task CLI indisponible.  
**Lifecycle :** Complete — prêt à merger (PR #30).
