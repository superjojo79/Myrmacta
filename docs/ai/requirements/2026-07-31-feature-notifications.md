---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
created: 2026-08-07
updated: 2026-08-07
stakeholder_decisions: 2026-08-07
phase2: 2026-08-07
phase2_stakeholder_confirm: 2026-08-07
---

# Requirements & Problem Understanding

## Problem Statement
**What problem are we solving?**

FlowForge orchestre dossiers et workflows, mais **aucun canal de
notification** n’est branché : packages `domain/notification` et
`infrastructure/notification` sont des placeholders. Les processus BPMN
ne peuvent ni alerter un utilisateur, ni pousser un événement vers
Make/n8n/Zapier, ni envoyer un email métier via un ServiceTask dédié.

- **Affectés :** auteurs BPMN, `TENANT_ADMIN` / `WORKFLOW_ADMIN`, utilisateurs
  SPA (SSE), intégrateurs noCode, features aval `frontend` /
  `visualisation-workflow`.
- **Situation actuelle :** outbox **workflow** (`outbox_event` +
  `TriggerOutboxPayload`) livrée ; crypto secrets connecteurs livrée ; pas de
  table/config webhook sortant notif ; pas de SSE ; pas de mail ;
  `NoOpWorkflowEventPublisher` reste un no-op (**D-PUBLISHER = 1** — hors
  branchement auto). Analyse : `analysis doc/09-notifications.md` + hexagone
  `01` ; roadmap `DEFERRED-FEATURES.md`.
- **UX :** pas d’écrans Vue V1 — API REST admin / send / stream + ServiceTask.
  Erreurs `ProblemDetail` alignées dossiers / connecteurs / documents.

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

- Livrer le **socle notifications** hexagonal (**D-SCOPE = B**) :
  `SendNotificationUseCase`, ports `EmailPort` / `WebhookPort` /
  `BrowserPushPort`, modèles `NotificationEvent` /
  `NotificationChannel`, adaptateurs SMTP / RestClient / SSE.
- **Outbox dédiée** (**D-OUTBOX = 1**) `notification_outbox` + relais
  scheduler : producteur (HTTP send ou Flowable) écrit N lignes pending
  (**D-FANOUT = 2**) ; relais appelle les ports **une fois** par passage ;
  retry = compteur outbox + `nextAttemptAt` (**D-RETRY = 2**,
  **D-BACKOFF = 1**, **A1**) puis `DEAD`.
- **Config webhook sortant** (**D-WH-CFG = 1**) : CRUD admin
  `notification_webhook` (URL, secret chiffré, enable, name) ;
  crypto via `ConnectorSecretCipher` (**D-SECRET = 1**) ; HMAC sortant si
  secret présent ; garde-fous SSRF minimaux (**D-SSRF = 1**).
- **Email SMTP** (**D-EMAIL = 1**, **D-EMAIL-VAL = 2**) : `to` / `subject` /
  `body` **tous requis** (blank → 400) ; envoi `text/plain` ; tests
  GreenMail ou mock port.
- **SSE navigateur** (**D-SSE = 1**) : `GET /api/notifications/stream`
  éphémère in-JVM ; ciblage par `sub` OIDC (**A6**) ; sans emitter →
  `PROCESSED` skipped (**A2**) — pas d’inbox.
- **`payload`** (**D-PAYLOAD = 1**) : alimente JSON webhook **et** `data`
  SSE ; ignoré pour EMAIL.
- **Entrées** (**D-HTTP = 2**, **D-FLOWABLE = 1**, **D-SEND = 1**) :
  - `notificationDispatchTask` → outbox
  - `POST /api/notifications/send` → **202** + ids outbox
  - admin CRUD webhooks + stream SSE
- **Authz** (**D-AUTHZ = 1**) :
  - Admin webhooks : `TENANT_ADMIN`
  - Send : `TENANT_ADMIN` \| `WORKFLOW_ADMIN`
  - SSE : tout utilisateur authentifié BFF (événements de son `sub`)
  - Flowable : `AccessContext.system("flowable-delegate")`
- Tests unitaires + MockMvc/IT H2 + ArchUnit ; Jacoco ≥ 80 %.

### Surface API (acceptation)

| Méthode | Path | Authz / notes |
|---|---|---|
| GET | `/api/notification-webhooks` | `TENANT_ADMIN` — liste hors soft-deleted (secret masqué) |
| POST | `/api/notification-webhooks` | `TENANT_ADMIN` — créer → **201** ; secret masqué |
| GET | `/api/notification-webhooks/{id}` | `TENANT_ADMIN` — détail ; soft-deleted → **404** |
| PATCH | `/api/notification-webhooks/{id}` | `TENANT_ADMIN` — update / enable ; **restore** soft-deleted (`restore:true`, pattern connecteurs) |
| DELETE | `/api/notification-webhooks/{id}` | `TENANT_ADMIN` — **soft delete** uniquement (**A7**) |
| POST | `/api/notifications/send` | `TENANT_ADMIN` \| `WORKFLOW_ADMIN` + CSRF ; **202** |
| GET | `/api/notifications/stream` | Authentifié BFF ; `text/event-stream` ; event `notification` |

Mutateurs BFF : CSRF. Anonyme → **401**. Erreurs : `ProblemDetail`.

### Contrat Flowable `notificationDispatchTask`

| Variable | Requis | Notes |
|---|---|---|
| `notificationChannels` | oui | Liste ou CSV : `EMAIL`, `WEBHOOK`, `BROWSER` (dédup **A12**) |
| `notificationTo` | si EMAIL | Destinataire ; blank → échec |
| `notificationSubject` | si EMAIL | Sujet ; blank → échec |
| `notificationBody` | si EMAIL | Corps ; blank → échec ; `text/plain` |
| `notificationUserId` | si BROWSER | OIDC `sub` (**A6**) |
| `notificationWebhookConfigId` | si WEBHOOK | UUID config admin |
| `notificationPayload` | non | Map/JSON → WEBHOOK + BROWSER (**D-PAYLOAD**) ; défaut `{}` ; ignoré EMAIL |

Ack BPMN dès persistence outbox (pas d’attente livraison).

### Mapping HTTP send ↔ domaine

| Body JSON | Domaine |
|---|---|
| `channels` | `List<NotificationChannel>` (dédup A12) |
| `to` / `subject` / `body` | email — **tous requis** si canal EMAIL |
| `userId` | browser target |
| `webhookConfigId` | webhook |
| `payload` | map pour WEBHOOK + BROWSER (D-PAYLOAD) |

Réponse **202** : `{ "outboxIds": ["…"] }` (un id par canal après dédup).

### Secondary goals

- Documenter chemin d’évolution SSE multi-répliques (LISTEN/NOTIFY) sans
  l’implémenter (**A4**).
- Logs structurés minimaux (`tenantId`, canal, outboxId) ; métriques riches
  Micrometer = soft (**A5**).
- Timeout HTTP webhook configurable (défaut raisonnable, ex. 5s).

### Non-goals (explicitly out of scope)

- Inbox REST / marquage lu / historique navigateur (**D-SSE**, **D-HTTP**).
- UI Vue / composable frontend (feature `frontend`).
- Templates email Thymeleaf / resolve `templates-documents`.
- Formats payload spécifiques Make/n8n/Zapier (**A3** — JSON générique).
- Strip champs `sensitive` (§23) (**D-SENSITIVE = 1**).
- Allowlist domaines, rate-limit sortant distribué, follow-redirect SSRF.
- M2M `API_SERVICE` sur send ; chemin send sans CSRF.
- Réutiliser / polymorphiser `outbox_event` workflow.
- Relais SSE pub/sub multi-répliques (**A4**).
- Brancher `WorkflowEventPublisher` / remplacer `NoOpWorkflowEventPublisher`
  (**D-PUBLISHER = 1**).
- `spring-retry` comme mécanisme de vérité du retry (**D-RETRY = 2**).
- OpenAPI formel ; observabilité Micrometer complète (§29).

## Alternatives considérées

| Approche | Trade-off | Verdict |
|---|---|---|
| Sync 3 canaux vs + outbox vs tranche mince | Fiabilité Flowable | **+ outbox** (D-SCOPE B) |
| Config dédiée vs ConnectorInstance vs inline BPMN | Clarté vs réemploi | **Dédiée** (D-WH-CFG 1) |
| Outbox dédiée vs outbox workflow poly | Isolation | **Dédiée** (D-OUTBOX 1) |
| Fan-out N lignes vs 1 ligne multi-canal | Retry partiel | **N lignes** (D-FANOUT 2) |
| SSE éphémère vs inbox | Scope | **Éphémère** (D-SSE 1) |
| Send 202 vs sync | Cohérence outbox | **202** (D-SEND 1) |
| SSRF minimal vs none vs allowlist | Sécurité / UX | **Minimal** (D-SSRF 1) |
| Retry adapter vs outbox vs double | Compteur de vérité | **Outbox** (D-RETRY 2) |
| payload webhook-only vs +BROWSER | Richesse SSE | **+BROWSER** (D-PAYLOAD 1) |
| Auto WorkflowEventPublisher vs Task/API only | Scope | **Task/API only** (D-PUBLISHER 1) |
| Email body optionnel vs requis | Strictness | **Requis** (D-EMAIL-VAL 2) |
| nextAttemptAt vs poll-only backoff | Fidélité A1 | **nextAttemptAt** (D-BACKOFF 1) |

## User Stories & Use Cases

1. **En tant que** modélisateur BPMN, **je veux** un ServiceTask de
   notification multi-canaux, **afin d’** alerter email / webhook / browser
   sans code custom.
2. **En tant que** `TENANT_ADMIN`, **je veux** CRUD des webhooks sortants
   (URL + secret), **afin d’** brancher Make/n8n/Zapier par tenant.
3. **En tant que** `WORKFLOW_ADMIN`, **je veux** `POST /send` asynchrone,
   **afin de** valider un canal sans démarrer un process.
4. **En tant qu’** utilisateur connecté, **je veux** un flux SSE, **afin de**
   recevoir les pushes BROWSER en temps réel dans la future SPA.
5. **En tant qu’** ops, **je veux** retry + dead-letter webhook, **afin de**
   ne pas perdre les intégrations lors d’une indisponibilité courte.

### Edge cases

- Canal WEBHOOK sans `webhookConfigId` / config soft-deleted / disabled →
  **400** (HTTP) ou échec task ; au relais → `DEAD` (**A8**).
- URL webhook non-HTTPS (hors profil local/test **A13**) ou cible
  privée/metadata → rejet validation (**D-SSRF**) → pas d’appel réseau.
- Secret webhook absent → envoi sans header HMAC.
- EMAIL sans `to` / `subject` / `body` (blank) → **400** (**D-EMAIL-VAL**).
- BROWSER sans `userId` → **400** ; user déconnecté → skipped **A2**.
- `channels` vide ou inconnu → **400** ; doublons → dédup **A12**.
- CSRF manquant sur mutateurs → **403** ; anonyme → **401**.
- Échec HTTP webhook : `attempts++`, `nextAttemptAt` 1s/2s/4s ; à 3 →
  `DEAD` + lastError (**A1**, **D-BACKOFF**).
- Crash après commit outbox / avant envoi → relais rejoue (pending éligible).

## Success Criteria
**How will we know when we're done?**

- [x] `SendNotificationUseCase` valide l’événement et persiste **N** lignes
      `notification_outbox` PENDING (1/canal dédup) dans une transaction.
- [x] Relais traite EMAIL (SMTP), WEBHOOK (1 HTTP/passage), BROWSER (SSE).
- [x] Webhook : outbox retry **A1**/**D-BACKOFF** puis `DEAD` ; SSRF refus
      avant call ; **pas** de spring-retry comme compteur.
- [x] HMAC sortant présent ssi secret configuré (**D-SECRET**).
- [x] CRUD admin webhooks : soft-delete + restore PATCH ; secret chiffré /
      masqué ; create **201**.
- [x] `POST /api/notifications/send` → **202** + `outboxIds` ; authz D-AUTHZ.
- [x] `GET /api/notifications/stream` pousse JSON (`payload` inclus) au bon
      `sub`.
- [x] `notificationDispatchTask` écrit outbox depuis variables D-FLOWABLE.
- [x] `NoOpWorkflowEventPublisher` inchangé (non branché).
- [x] Liquibase tables `notification_webhook` + `notification_outbox`
      (dont `next_attempt_at`).
- [x] `./mvnw verify` vert ; Jacoco instructions ≥ 80 % ; ArchUnit OK.
- [x] `npx ai-devkit lint --feature notifications` OK (branche feature).

## Constraints & Assumptions

- Java 25 · Spring Boot 4.x · hexagone mono-module · H2 MODE=PostgreSQL IT.
- Tenant = instance dédiée / `TenantProperties` (décision n° 13).
- Pattern outbox inspiré workflow (décision n° 16) mais **table séparée**.
- Crypto : réutiliser `ConnectorSecretCipher` / `EnvMasterKeyCipher`.
- **A1** — Retry **via outbox uniquement** (**D-RETRY = 2**) : max **3**
  tentatives HTTP (1 call / passage relais) ; sur échec :
  `attempts++`, `nextAttemptAt` = now + 1s / 2s / 4s (**D-BACKOFF = 1**) ;
  à 3 → `DEAD` ; timeout HTTP client configurable.
- **A2** — BROWSER sans emitter actif → `PROCESSED` (skipped), pas `DEAD`.
- **A3** — Payload webhook = JSON générique uniquement V1.
- **A4** — SSE multi-répliques (LISTEN/NOTIFY / Redis) hors V1.
- **A5** — Métriques Micrometer riches (§29) hors gate ; logs min. requis.
- **A6** — `notificationUserId` / `userId` = OIDC `sub`.
- **A7** — Delete webhook = **soft delete** ; liste/GET hors deleted ;
  restore via PATCH `{ "restore": true }` (pattern connecteurs).
- **A8** — Config webhook introuvable au **relais** → `DEAD` ;
  au **send/task** → rejet immédiat **400** / exception delegate.
- **A9** — Header HMAC sortant : `X-FlowForge-Signature: sha256=<hex>` du
  corps brut.
- **A10** — Email IT : GreenMail **ou** `EmailPort` mockable ; pas de SMTP
  réel en CI.
- **A11** — `spring-boot-starter-mail` ajouté au `pom.xml` ;
  `spring-retry` **non requis** V1 (D-RETRY 2).
- **A12** — `channels` dédupliqués (Set order-preserving) avant fan-out.
- **A13** — Profils `test` / `local` : `http://localhost` /
  `http://127.0.0.1` / `http://[::1]` uniquement ; **pas** de `http` public ;
  hors ces profils → HTTPS obligatoire ; HTTPS vers hosts privés/loopback
  toujours rejeté (D-SSRF).

## Questions & Open Items

| # | Item | Disposition |
|---|---|---|
| Q1 | Périmètre V1 (outbox + 3 canaux) | **Clos** — D-SCOPE B |
| Q2 | Config webhook | **Clos** — D-WH-CFG 1 |
| Q3 | Surface HTTP | **Clos** — D-HTTP 2 |
| Q4 | Authz | **Clos** — D-AUTHZ 1 |
| Q5 | Modèle outbox | **Clos** — D-OUTBOX 1 |
| Q6 | Fan-out | **Clos** — D-FANOUT 2 |
| Q7 | Persistance SSE | **Clos** — D-SSE 1 |
| Q8 | SSRF | **Clos** — D-SSRF 1 |
| Q9 | Sensitive strip | **Clos** — D-SENSITIVE 1 (différé) |
| Q10 | Email | **Clos** — D-EMAIL 1 |
| Q11 | Secrets / HMAC | **Clos** — D-SECRET 1 |
| Q12 | ServiceTask | **Clos** — D-FLOWABLE 1 |
| Q13 | Sémantique send | **Clos** — D-SEND 1 |
| Q14 | Retry / skip / formats / soft-delete / dédup | **Clos** — A1–A13 + phase 2 |
| Q15 | Compteur retry (adapter vs outbox) | **Clos** — D-RETRY 2 |
| Q16 | Portée payload | **Clos** — D-PAYLOAD 1 |
| Q17 | WorkflowEventPublisher | **Clos** — D-PUBLISHER 1 |
| Q18 | Validation EMAIL | **Clos** — D-EMAIL-VAL 2 |
| Q19 | Backoff outbox | **Clos** — D-BACKOFF 1 |

### Décisions actées

| Id | Sujet | Choix |
|---|---|---|
| D-SCOPE | Tranche | 3 canaux + outbox notif + relais |
| D-WH-CFG | Config webhook | Table/API `notification_webhook` |
| D-HTTP | REST | Admin + send 202 + SSE ; pas d’inbox |
| D-AUTHZ | Rôles | Admin TA ; send TA\|WA ; SSE auth ; system Flowable |
| D-OUTBOX | Persistence async | `notification_outbox` dédiée |
| D-FANOUT | Multi-canal | `channels[]` → N lignes |
| D-SSE | Browser | Éphémère mémoire |
| D-SSRF | URL sortante | HTTPS + blocage privé/metadata |
| D-SENSITIVE | Fields sensibles | Différé §23 |
| D-EMAIL | Mail | SMTP + corps événement |
| D-SECRET | Crypto / HMAC | ConnectorSecretCipher + sig si secret |
| D-FLOWABLE | Task | `notificationDispatchTask` |
| D-SEND | HTTP send | Async 202 après outbox |
| D-RETRY | Retry | Outbox attempts seulement (1 HTTP/passage) |
| D-PAYLOAD | payload | WEBHOOK + BROWSER |
| D-PUBLISHER | WorkflowEventPublisher | Reste no-op |
| D-EMAIL-VAL | Champs email | to + subject + body requis |
| D-BACKOFF | Rejeu | `nextAttemptAt` 1s→2s→4s |

**Phase 1 close** (2026-08-07) — D-SCOPE…D-SEND + assumptions initiales.  
**Phase 2 close** (2026-08-07) — statut `reviewed` ; R1–R6 tranchés
(D-RETRY, D-PAYLOAD, D-PUBLISHER, D-EMAIL-VAL, D-BACKOFF) ; A1/A7/A11
corrigés ; A13 ajouté.  
**Phase 3 close** (2026-08-07) — design `reviewed` (DD-ENVELOPE, DD-CIPHER
encrypt/decrypt, DD-HEARTBEAT, DD-PURGE, DD-LOCK, DD-SSE-MULTI).  
**Phase 4 close** (2026-08-07) — planning `ready` (T1–T16).  
**Phase 5–6 close** (2026-08-07) — T1–T16 done.  
**Phase 7 close** (2026-08-07) — implementation `checked` / aligned.  
**Phase 8 close** (2026-08-07) — testing `completed` ; verify **487/0**,
Jacoco ≈88 % ; gaps PayloadJson/Channel/409/A13 IT fermés.  
**Phase 9 close** (2026-08-07) — review READY ; blockers SSRF/fan-out/unique
index corrigés ; verify **490/0**, Jacoco **87.8 %**.  
**Lifecycle :** Complete — prêt à merger (PR #30).

## Sources

- `analysis doc/09-notifications.md`, `01-architecture-hexagonale.md`,
  `06-connecteurs.md` (crypto, HMAC entrant), `02-multi-tenant.md` (déc. 13)
- `DEFERRED-FEATURES.md` §2 ; TODO §23, §29, §30
- Décisions TODO n° 13, 15, 16 ; pattern outbox workflow livré
- Features livrées : `connecteurs`, `workflow-engine`, `authentification`
- Clarifications lifecycle 2026-08-07 (phases 1–2)
