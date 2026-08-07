---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: checked
created: 2026-08-07
updated: 2026-08-07
phase5: 2026-08-07
phase6: 2026-08-07
phase7: 2026-08-07
phase8: 2026-08-07
phase9: 2026-08-07
alignment: aligned
review: ready
---

# Implementation Guide

## Development Setup

- Worktree `.worktrees/feature-notifications`
- JDK 25 — `JAVA_HOME=/tmp/jdk/jdk-25.0.4+7`
- `spring-boot-starter-mail` au pom ; `MailSenderAutoConfiguration` non exclu

## Code Structure

```
domain/notification/**          — modèles, ports, Send/Manage use cases
domain/connector/...Cipher      — encrypt/decrypt string (DD-CIPHER)
infrastructure/persistence/notification — JPA + Liquibase 006
infrastructure/notification     — Guard, Webhook, SMTP, SSE, Relay
infrastructure/web/notification — Admin / Send / SSE + ProblemDetail handler
infrastructure/flowable         — NotificationDispatchTask
infrastructure/config           — NotificationConfig / NotificationProperties
```

## Phase 7 — Check Implementation (2026-08-07)

### Alignment status: **aligned**

Revue vs requirements/design `reviewed` : **16/16** checkpoints ALIGNED après
correctif A13 SSRF. Aucun écart bloquant restant. Pas de retour design requis.

### Checklist design → code

| Id | Exigence | Verdict |
|---|---|---|
| DD-UC-NO-IO | Send UC → outbox only | aligned |
| DD-SOFT-DEL / A7 | Soft-delete + restore `restore:true` | aligned |
| Liquibase 006 | webhook + outbox + `next_attempt_at` | aligned |
| DD-CIPHER | `encrypt`/`decrypt` + webhook secrets | aligned |
| D-SSRF / A13 | https ; http loopback only test/local | aligned (fix phase 7) |
| DD-ENVELOPE / A9 | envelope + HMAC signature | aligned |
| D-EMAIL-VAL | SMTP text/plain, champs requis | aligned |
| DD-SSE-MULTI / HEARTBEAT | Set emitters + ping | aligned |
| DD-RETRY / BACKOFF / LOCK / PURGE | relay attempts/backoff/DEAD/purge | aligned |
| D-AUTHZ / D-HTTP / D-SEND | admin TA ; send 202 TA\|WA ; SSE sub | aligned |
| D-FLOWABLE | `notificationDispatchTask` bean | aligned |
| D-PUBLISHER | NoOpWorkflowEventPublisher no-op | aligned |
| ArchUnit | domain sans Spring mail/web | aligned |

### Deviations / notes (non bloquant)

| Sévérité | Item | Disposition |
|---|---|---|
| note | SSRF = contrôles host littéraux (pas de résolution DNS) | Accepté V1 (design) |
| note | `lockNextPending` sans `FOR UPDATE` (comme workflow) | Follow-up multi-réplica |
| note | Restore API = `restore:true` (pas `deletedAt=null`) | Docs sync phase 7 |
| note | DispatchTask sans `AccessContext.system` | OK — pas d’ACL dossier |

### Correctif phase 7

`OutboundUrlGuard` : en profils `test`/`local`, autorise uniquement
`http://localhost|127.0.0.1|[::1]` ; refuse `http` public et HTTPS privés.
Tests `OutboundUrlGuardTest` mis à jour (red → green).

### Evidence (phase 7, fresh)

```text
JAVA_HOME=/tmp/jdk/jdk-25.0.4+7 ./mvnw verify
→ Tests run: 468, Failures: 0, Errors: 0
→ Jacoco instructions ≈ 87 %
→ ArchUnit OK
→ npx ai-devkit lint --feature notifications → All checks passed
→ check-status → Phase 8 (Write Tests) suggested after phase 7 close
```

### Follow-ups

- SKIP LOCKED réel si scaling multi-réplica
- Micrometer (§29), strip sensitive (§23), inbox/`frontend`

### Phase 8 note

Coverage gaps fermés (PayloadJson, Channel.parse, handler 409, admin
duplicate/localhost A13, relay JSON invalide).  
`spring.profiles.active=test` dans `src/test/resources/application.properties`.

## Phase 9 — Code Review (2026-08-07)

**Verdict :** prêt à merger (PR #30). Blockers corrigés ; risques résiduels
documentés.

### Checklist finale

| Critère | Statut |
|---|---|
| Design match (D-* / DD-*) | OK |
| Pas de trou logique bloquant | OK après fan-out atomique |
| Sécurité (SSRF A13, secrets, authz) | OK après durcissement guard |
| Points d’intégration outbox / Flowable / SSE | OK |
| Tests couvrent les changements | OK (voir evidence) |
| Docs à jour | OK |

### Findings phase 9

| Sévérité | ID | Issue | Action |
|---|---|---|---|
| Blocking | REV-SSRF | Trous `::ffff:`, ULA `fc`/`fd`, IPv4 décimal | **Corrigé** `OutboundUrlGuard` + tests |
| Blocking | REV-FANOUT | Fan-out N×`savePending` non atomique | **Corrigé** `savePendingBatch` (validate-all puis 1 TX) |
| Important | REV-UNIQ | Pas d’index unique partiel webhook name | **Corrigé** Liquibase 006 (+ rollback) |
| Important | REV-LOCK | `lockNextPending` sans `FOR UPDATE`/`SKIP LOCKED` | Accepté V1 (aligné workflow) — follow-up multi-réplica |
| Nice | REV-DNS | SSRF = host littéral (pas résolution DNS) | Accepté V1 (design) |

### Correctifs phase 9

- `PendingOutboxWrite` + `NotificationOutboxRepository.savePendingBatch`
- `SendNotificationUseCaseImpl` valide tous les canaux puis batch
- `OutboundUrlGuard` : mapped IPv6, ULA, link-local, IPv4 décimal
- Liquibase 006 : `uq_notification_webhook_tenant_name_active` + rollbacks

### Evidence (phase 9)

```text
JAVA_HOME=/tmp/jdk/jdk-25.0.4+7 ./mvnw verify
→ Tests run: 490, Failures: 0, Errors: 0
→ Jacoco instructions 87.8 % (seuil ≥ 80 % OK)
→ ArchUnit OK
→ npx ai-devkit lint --feature notifications → All checks passed
```

### Follow-ups post-merge

- `SKIP LOCKED` si scaling multi-réplica du relais
- Micrometer (§29), strip sensitive (§23), inbox/`frontend`

**Lifecycle :** phase 9 terminée — prêt à merger.
