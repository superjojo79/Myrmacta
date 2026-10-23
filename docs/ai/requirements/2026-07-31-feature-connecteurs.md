---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
reviewed: 2026-08-03
created: 2026-08-03
updated: 2026-08-03
---

# Requirements & Problem Understanding

## Problem Statement
**What problem are we solving?**

FlowForge a un **moteur workflow + outbox** prêts (`workflow-engine`) et des
packages `connector` vides. Aucun chemin **événement externe → dossier(s) →
outbox**, ni catalogue d’instances configurables, ni chiffrement des secrets
connecteurs. Sans cette tranche, les intégrations (webhooks, signaux Flowable)
ne peuvent pas démarrer de processus de façon durable.

- **Affectés :** `TENANT_ADMIN` (config instances), intégrateurs tiers
  (webhooks), auteurs BPMN (`flowable-signal` SERVICE), rôle `API_SERVICE`
  (execute M2M), features aval (`notifications`, `frontend`).
- **Situation actuelle :** placeholders `domain/connector` /
  `infrastructure/*/connector` ; seule chaîne Security BFF `@Order(3)` ;
  outbox + `TriggerOutboxPayload` déjà livrés. Analyse :
  `docs/specs/06-connecteurs.md` + décisions n° 3, 4, 5, 14, 15, 16, 17, 18.
- **UX :** pas d’écrans Vue — API REST admin + webhooks + execute. Erreurs
  `ProblemDetail` alignées dossiers / workflow.

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

- Livrer le **socle connecteurs** hexagonal : `ConnectorDefinition` /
  `TriggerConnector` / `ServiceConnector`, `ConnectorRegistry`,
  `ConnectorInstanceData` (record pur) vs `ConnectorInstanceJpaEntity`,
  ports + use cases admin, Liquibase.
- Builtins V1 :
  - **`http-rest` TRIGGER** — webhook entrant + mapping déclaratif ;
  - **`flowable-signal` TRIGGER + SERVICE** — réception / émission de
    signaux Flowable.
- **Admin CRUD** instances + catalogue définitions : **`TENANT_ADMIN` seul**
  (**D-ADMIN = 1**). Soft delete + restore via PATCH (**D-DELETE = 2**,
  **D-UNDELETE = 3**).
- **Crypto** (**D-CRYPTO = 3**) : port `ConnectorSecretCipher` + impl **clé
  maître env** ; DEK par tenant en base ; Vault = adaptateur hors happy-path /
  stub.
- **Webhooks** : chaîne Security dédiée (sans session/CSRF) ; auth
  **HMAC-SHA256** (`X-Signature-256` = `sha256=<hex>`) +
  **SHARED_SECRET_TOKEN** (`X-Webhook-Token`) (**D-WEBHOOK-AUTH = 3**,
  **D-TOKEN-HDR = 1** ; IP allowlist différée) ; échec auth → **401**
  (**D-WH-FAIL = 1**).
- **Mapping déclaratif** (**D-MAP = 2**, **D-DEFAULTS = 1**) : `itemsPath`,
  `fields`, `variables`, `existingDossierReferencePath`, `processKeyPath`
  (JSONPath simple `$.a.b`) → `WorkflowTriggerEvent` (1..N) ; **création +
  `existingDossierReference`** (**D-TARGET = 2**) via `AccessContext.system`.
- **Producteur outbox** (décision n° 16) : pas de start Flowable dans la TX
  webhook ; ACK **202** après commit.
- **Idempotence** webhook (**D-IDEM = 2**, **D-IDEM-HDR = 1**) : header
  **`Idempotency-Key` requis** (pas d’alias) → même ACK sans re-traiter ;
  TTL défaut **24h**.
- **Durcissement** (**D-HARDEN = 2**, **D-DEFAULTS = 1**) : max-body **1 MiB**,
  rate **120/min/instance** (in-memory).
- **SERVICE** (**D-SIGNAL = 2**, **D-SERVICE-INVOKE = 2**) :
  `ConnectorServiceTask` + execute dual-URL (**DD-EXECUTE-ROUTE**).
- **Authz execute** (**D-EXECUTE-AUTH = 3**) : BFF `TENANT_ADMIN` **et**
  M2M `API_SERVICE` via chemins séparés (phase 3).
- Tests unitaires + MockMvc/IT H2 ; ArchUnit ; Jacoco ≥ 80 %.

### Surface API (acceptation)

| Méthode | Path | Authz / notes |
|---|---|---|
| GET | `/api/connector-definitions` | `TENANT_ADMIN` — catalogue types |
| GET | `/api/connectors` | `TENANT_ADMIN` — liste **hors soft-deleted** |
| POST | `/api/connectors` | `TENANT_ADMIN` — créer ; nom unique parmi actives |
| GET | `/api/connectors/{id}` | `TENANT_ADMIN` — détail (secrets masqués) ; soft-deleted → **404** |
| PATCH | `/api/connectors/{id}` | `TENANT_ADMIN` — update / enable ; **restore** soft-deleted (`deletedAt=null`) |
| DELETE | `/api/connectors/{id}` | `TENANT_ADMIN` — **soft delete** (`deletedAt` + désactivation) |
| POST | `/api/connectors/{id}/execute` | BFF `TENANT_ADMIN` + CSRF ; soft-deleted → 404 |
| POST | `/api/m2m/connectors/{id}/execute` | M2M JWT `API_SERVICE` ; soft-deleted → 404 |
| POST | `/api/webhooks/{instanceId}` | Chaîne webhook ; HMAC ou `X-Webhook-Token` ; **`Idempotency-Key` requis** |

Mutateurs BFF : CSRF. Webhooks / M2M : chaînes sans session/CSRF.
Anonyme hors webhook → 401. Auth webhook KO → **401**. Erreurs :
`ProblemDetail`.

### Secondary goals

- Documenter le contrat Vault / rotation DEK pour follow-up ops.
- Smoke BPMN minimal démontrant `ConnectorServiceTask` + signal (classpath).
- Réponses admin : champs `PASSWORD` **masqués** (jamais le clair).

### Non-goals

- Builtins `scheduler`, `smtp`, `s3-minio` ; **`http-rest` SERVICE** (SSRF
  TODO §30 partiel).
- `WebhookAuthType.IP_ALLOWLIST`.
- Vault/KMS réel en CI ; `RotateTenantEncryptionKeyUseCase` automatisé.
- Hard delete / purge physique des soft-deleted ; endpoint `/restore` dédié.
- Alias `X-Delivery-Id` pour l’idempotence.
- UI Vue / OpenAPI formel ; métriques Micrometer riches (TODO §29).
- Rate limit distribué multi-pod (in-memory V1 = best-effort).

## Alternatives considérées

| Approche | Trade-off | Verdict |
|---|---|---|
| **A — Trigger-first** (+ `flowable-signal` BOTH) | E2E webhook utile ; SERVICE limité au signal | **Retenue** |
| B — Socle large stubs sans E2E | Large surface sans preuve bout-en-bout | Écartée |
| C — Catalogue analyse complet | Trop large (smtp/s3/scheduler/SSRF) | Écartée |
| Hard delete vs soft delete | Hard plus simple ; soft plus sûr ops | **Soft** (D-DELETE 2) |
| Undelete route vs PATCH | Route dédiée vs PATCH | **PATCH** (D-UNDELETE 3) |

## User Stories & Use Cases

- En tant que **TENANT_ADMIN**, je crée une instance `http-rest` avec mapping
  et secret HMAC ; l’URL webhook est publiable sous le préfixe tenant.
- En tant qu’**intégrateur**, je POST un webhook signé + `Idempotency-Key` →
  dossiers créés / ciblés + outbox ; 202 ; replay → pas de doublon.
- En tant que **TENANT_ADMIN**, je soft-delete une instance ; je la restaure
  plus tard via PATCH.
- En tant que **TENANT_ADMIN**, je configure `flowable-signal` et l’utilise
  depuis un BPMN (`ConnectorServiceTask`) ou via `/execute`.
- En tant que **`API_SERVICE`**, j’appelle `/execute` avec un JWT
  `client_credentials` (tenant_id aligné).
- En tant que **relais outbox** (déjà livré), je consomme les événements
  produits par les triggers connecteurs.

### Scénarios clés

1. Admin crée instance `http-rest` (HMAC) → 201 ; GET masque le secret.
2. Webhook HMAC valide + `Idempotency-Key` + mapping → N dossiers / outbox → 202.
3. Webhook signature / token invalide → **401** ProblemDetail ; rien en base.
4. Sans `Idempotency-Key` → **400** ; replay même clé → 202 sans second traitement.
5. Corps > 1 MiB → 413 ; rate > 120/min → 429.
6. Instance `enabled=false` ou soft-deleted → webhook **404**.
7. Mapping avec `existingDossierReference` → résolution système + outbox.
8. `flowable-signal` SERVICE via ServiceTask ou `/execute` → signal émis.
9. Execute BFF `/api/connectors/{id}/execute` OK ; M2M
   `/api/m2m/connectors/{id}/execute` OK ; mauvais rôle / tenant → 401/403.
10. DELETE → soft-deleted hors liste ; PATCH restore → visible / webhook OK.
11. Nom dupliqué parmi actives → 409 ; réemploi après soft delete OK.
12. Boot IT : tables `connector_instance` (+ `deleted_at`) + DEK + idempotency.

### Edge cases

- Soft-deleted : GET/liste/execute/webhook → 404 ; PATCH restore `TENANT_ADMIN` only.
- Config `IP_ALLOWLIST` → 400 à la création/update.
- Batch mapping : échec au milieu → rollback TX (pas d’ACK 202 partiel).
- Secret shared : header `X-Webhook-Token` uniquement (pas Bearer).

## Success Criteria

- [ ] Catalogue : au moins `http-rest` et `flowable-signal` découverts.
- [ ] CRUD + soft delete + restore PATCH `TENANT_ADMIN` ; secrets masqués.
- [ ] Webhook HMAC (`X-Signature-256`) + `X-Webhook-Token` ; auth KO → 401.
- [ ] `Idempotency-Key` requis ; TTL 24h ; max-body 1 MiB ; rate 120/min.
- [ ] Mapping déclaratif (D-DEFAULTS) → 1..N ; create + existing ref.
- [ ] Outbox alimentée ; aucun `RuntimeService.start` dans la TX webhook.
- [ ] `ConnectorServiceTask` + dual execute (BFF + `/api/m2m/...`) pour signal.
- [ ] Chaînes Security : M2M `@Order(1)`, webhook `@Order(2)`, BFF `@Order(3)`.
- [ ] ArchUnit + Jacoco ≥ 80 % ; suites existantes vertes.
- [ ] Docs design / testing alignés ; non-goals explicites.

**Performance :** bornes ci-dessus documentées ; pas de cible latence formelle.

## Constraints & Assumptions

### Technical constraints

- Java 25 · Spring Boot 4.1 · Flowable 8 · Liquibase · H2 MODE=PostgreSQL IT.
- Mono-tenant instance (décision n° 13) ; routage chemin (n° 14).
- Domaine pur records (n° 17) ; `webhookAuthType` dans `config` (n° 18).
- Outbox option B (n° 16) — producteur ici, relais déjà livré.

### Business / product

- Seul `TENANT_ADMIN` administre et restaure les instances.
- Intégrateurs webhook non authentifiés OIDC — confiance = HMAC/token.
- `API_SERVICE` = execute machine-to-machine uniquement (pas admin CRUD).

### Time / rollout

- Branche `feature-connecteurs` / PR ; pas de feature flag.
- Rollout = merge + déploiement instance tenant (ops hors docs).

### Assumptions (acceptées)

- A1 / **D-SCOPE** — Trigger-first + `flowable-signal` BOTH ; autres builtins hors scope.
- A2 / **D-CRYPTO** — Port + clé maître env V1 ; Vault hors happy-path.
- A3 / **D-ADMIN** — Mutateurs + lecture admin = `TENANT_ADMIN` seul.
- A4 / **D-WEBHOOK-AUTH** — HMAC + SHARED_SECRET ; IP différée.
- A5 / **D-MAP** — Mapping déclaratif dans config instance.
- A6 / **D-HARDEN** — Max body + rate limit in-memory.
- A7 / **D-TARGET** — Batch 1..N ; create + existing reference.
- A8 / **D-IDEM** + **D-IDEM-HDR** — `Idempotency-Key` **seul**, requis.
- A9 / **D-SIGNAL** — `flowable-signal` TRIGGER + SERVICE.
- A10 / **D-SERVICE-INVOKE** — ServiceTask + `/execute`.
- A11 / **D-EXECUTE-AUTH** — BFF admin + M2M `API_SERVICE`.
- A12 — `enabled=false` **ou soft-deleted** → webhook **404**.
- A13 — Secrets `PASSWORD` masqués dans réponses admin.
- A14 — Rate limit in-memory = best-effort multi-pod V1.
- A15 — `http-rest` SERVICE hors scope (SSRF).
- A16 / **D-WH-FAIL** — Auth webhook KO → **401**.
- A17 / **D-TOKEN-HDR** — Shared secret via `X-Webhook-Token`.
- A18 / **D-DELETE** — Soft delete (`deletedAt` + disabled).
- A19 / **D-UNDELETE** — Restore via **PATCH** (`TENANT_ADMIN`).
- A20 / **D-DEFAULTS** — Mapping + bornes design (1 MiB / 120/min / 24h).

## Questions & Open Items

| # | Item | Disposition |
|---|---|---|
| Q1–Q9 | Décisions phase 1 | **Clos** |
| Q10 | Schéma mapping | **Clos** — D-DEFAULTS 1 (détail design) |
| Q11 | TTL / bornes | **Clos** — D-DEFAULTS 1 |
| Q12 | Provider Vault réel | **Différé** — non-goal V1 |
| Q13 | Header idempotence | **Clos** — D-IDEM-HDR 1 |
| Q14 | Code HTTP auth webhook KO | **Clos** — D-WH-FAIL 1 |
| Q15 | Header shared secret | **Clos** — D-TOKEN-HDR 1 |
| Q16 | Delete / restore | **Clos** — D-DELETE 2 + D-UNDELETE 3 |
| Q17 | Accroche Flowable signal TRIGGER | **Clos** — DD-SIGNAL-HOOK (`FlowableEventListener`) |
| Q18 | Routes execute BFF vs M2M | **Clos** — DD-EXECUTE-ROUTE dual URL |
| Q19 | Impl JSONPath | **Clos** — DD-JSONPATH parser maison |
| Q20 | Unicité name | **Clos** — DD-NAME-UQ unique partiel |

### Décisions actées

| Id | Sujet | Choix |
|---|---|---|
| D-SCOPE | Tranche | A Trigger-first |
| D-CRYPTO | Clés | Port + env ; Vault hors path |
| D-ADMIN | Authz admin | TENANT_ADMIN seul |
| D-WEBHOOK-AUTH | Auth webhook | HMAC + SHARED_SECRET |
| D-MAP | Mapping | Déclaratif config |
| D-HARDEN | Durcissement | Max body + rate limit |
| D-TARGET | Cibles | Create batch + existing ref |
| D-IDEM | Replay | Idempotency-Key |
| D-IDEM-HDR | Header | `Idempotency-Key` seul requis |
| D-WH-FAIL | Auth KO | 401 |
| D-TOKEN-HDR | Shared secret | `X-Webhook-Token` |
| D-DELETE | Suppression | Soft delete |
| D-UNDELETE | Restauration | PATCH (`TENANT_ADMIN`) |
| D-DEFAULTS | Mapping + bornes | Schéma design + 1 MiB / 120 / 24h |
| D-SIGNAL | flowable-signal | TRIGGER + SERVICE |
| D-SERVICE-INVOKE | Invoke SERVICE | ServiceTask + /execute |
| D-EXECUTE-AUTH | Authz execute | BFF admin + M2M API_SERVICE |
| DD-EXECUTE-ROUTE | Chemins execute | BFF + `/api/m2m/...` |
| DD-SIGNAL-HOOK | TRIGGER signal | FlowableEventListener global |
| DD-JSONPATH | Mapping paths | Parser maison |
| DD-NAME-UQ | Name unique | Partiel WHERE deleted_at IS NULL |

**Phase 2 close** (2026-08-03) — statut `reviewed`.
**Phase 3** — design `reviewed` ; requirements API alignée dual execute.

## Sources

- `docs/specs/06-connecteurs.md`, `03-authentification.md`, `05-workflow-engine.md`
- Décisions TODO n° 3, 4, 5, 14, 15, 16, 17, 18 ; TODO §30 (partiel)
- `DEFERRED-FEATURES.md` — ordre feature connecteurs
- Features livrées : `workflow-engine` (outbox), `authentification` (BFF),
  `autorisation-conformite` (rôles / method security)
- Mémoire : `Connecteurs V1 scope decisions (tranche A)`
