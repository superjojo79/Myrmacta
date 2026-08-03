---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: draft
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
  `analysis doc/06-connecteurs.md` + décisions n° 3, 4, 5, 14, 15, 16, 17, 18.
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
  (**D-ADMIN = 1**).
- **Crypto** (**D-CRYPTO = 3**) : port `ConnectorSecretCipher` + impl **clé
  maître env** ; DEK par tenant en base ; Vault = adaptateur hors happy-path /
  stub.
- **Webhooks** : chaîne Security dédiée (sans session/CSRF) ; auth
  **HMAC-SHA256 + SHARED_SECRET_TOKEN** (**D-WEBHOOK-AUTH = 3**, IP
  allowlist différée) ; vérification avant runtime domaine.
- **Mapping déclaratif** (**D-MAP = 2**) dans la config d’instance →
  `WorkflowTriggerEvent` (1..N) ; **création dossier +
  `existingDossierReference`** (**D-TARGET = 2**) via `AccessContext.system`.
- **Producteur outbox** (décision n° 16) : pas de start Flowable dans la TX
  webhook ; ACK **202** après commit.
- **Idempotence** webhook (**D-IDEM = 2**) : header `Idempotency-Key` (ou
  `X-Delivery-Id`) → même ACK sans re-traiter.
- **Durcissement** (**D-HARDEN = 2**) : borne taille corps + rate limit simple
  sur `/api/webhooks/**`.
- **SERVICE** (**D-SIGNAL = 2**, **D-SERVICE-INVOKE = 2**) :
  `ConnectorServiceTask` + `POST /api/connectors/{id}/execute`.
- **Authz execute** (**D-EXECUTE-AUTH = 3**) : BFF `TENANT_ADMIN` **et**
  chaîne M2M `@Order(1)` `client_credentials` + `API_SERVICE`.
- Tests unitaires + MockMvc/IT H2 ; ArchUnit ; Jacoco ≥ 80 %.

### Surface API (acceptation)

| Méthode | Path | Authz / notes |
|---|---|---|
| GET | `/api/connector-definitions` | `TENANT_ADMIN` — catalogue types |
| GET | `/api/connectors` | `TENANT_ADMIN` — liste instances |
| POST | `/api/connectors` | `TENANT_ADMIN` — créer |
| GET | `/api/connectors/{id}` | `TENANT_ADMIN` — détail (secrets masqués) |
| PATCH | `/api/connectors/{id}` | `TENANT_ADMIN` — update / enable |
| DELETE | `/api/connectors/{id}` | `TENANT_ADMIN` |
| POST | `/api/connectors/{id}/execute` | BFF `TENANT_ADMIN` **ou** M2M `API_SERVICE` |
| POST | `/api/webhooks/{instanceId}` | Chaîne webhook ; HMAC ou shared secret ; `Idempotency-Key` |

Mutateurs BFF : CSRF. Webhooks / M2M : chaînes sans session/CSRF.
Anonyme hors webhook → 401. Erreurs : `ProblemDetail`.

### Secondary goals

- Documenter le contrat Vault / rotation DEK pour follow-up ops.
- Smoke BPMN minimal démontrant `ConnectorServiceTask` + signal (classpath).
- Réponses admin : champs `PASSWORD` **masqués** (jamais le clair).

### Non-goals

- Builtins `scheduler`, `smtp`, `s3-minio` ; **`http-rest` SERVICE** (SSRF
  TODO §30 partiel).
- `WebhookAuthType.IP_ALLOWLIST`.
- Vault/KMS réel en CI ; `RotateTenantEncryptionKeyUseCase` automatisé.
- UI Vue / OpenAPI formel ; métriques Micrometer riches (TODO §29).
- Rate limit distribué multi-pod (in-memory V1 = best-effort).

## Alternatives considérées

| Approche | Trade-off | Verdict |
|---|---|---|
| **A — Trigger-first** (+ `flowable-signal` BOTH ajouté) | E2E webhook utile ; SERVICE limité au signal | **Retenue** (D-SCOPE + D-SIGNAL) |
| B — Socle large stubs sans E2E | Large surface sans preuve bout-en-bout | Écartée |
| C — Catalogue analyse complet | Trop large (smtp/s3/scheduler/SSRF) | Écartée |

## User Stories & Use Cases

- En tant que **TENANT_ADMIN**, je crée une instance `http-rest` avec mapping
  et secret HMAC ; l’URL webhook est publiable sous le préfixe tenant.
- En tant qu’**intégrateur**, je POST un webhook signé → dossiers créés /
  ciblés + événements outbox ; 202 ; replay même `Idempotency-Key` → pas de
  doublon.
- En tant que **TENANT_ADMIN**, je configure `flowable-signal` et l’utilise
  depuis un BPMN (`ConnectorServiceTask`) ou via `/execute`.
- En tant que **`API_SERVICE`**, j’appelle `/execute` avec un JWT
  `client_credentials` (tenant_id aligné).
- En tant que **relais outbox** (déjà livré), je consomme les événements
  produits par les triggers connecteurs.

### Scénarios clés

1. Admin crée instance `http-rest` (HMAC) → 201 ; GET masque le secret.
2. Webhook HMAC valide + mapping → N dossiers / outbox → 202.
3. Webhook signature invalide → 401/403 ProblemDetail ; rien en base.
4. Replay même `Idempotency-Key` → 202 sans second traitement.
5. Corps > max → 413 ; rate limit dépassé → 429.
6. Instance `enabled=false` → 404.
7. Mapping avec `existingDossierReference` → résolution système + outbox.
8. `flowable-signal` SERVICE via ServiceTask ou `/execute` → signal émis.
9. `/execute` : admin BFF OK ; M2M `API_SERVICE` OK ; autre rôle 403.
10. Boot IT : tables `connector_instance` + DEK + idempotency webhook.

## Success Criteria

- [ ] Catalogue : au moins `http-rest` et `flowable-signal` découverts.
- [ ] CRUD instances `TENANT_ADMIN` ; secrets masqués en lecture.
- [ ] Webhook HMAC + shared secret ; IP non supportée → 400 config claire.
- [ ] Mapping déclaratif → 1..N événements ; create + existing ref.
- [ ] Outbox alimentée ; aucun `RuntimeService.start` dans la TX webhook.
- [ ] Idempotency-Key + max-body + rate limit opérationnels.
- [ ] `ConnectorServiceTask` + `/execute` (BFF + M2M) pour signal.
- [ ] Chaînes Security : M2M `@Order(1)`, webhook `@Order(2)`, BFF `@Order(3)`.
- [ ] ArchUnit + Jacoco ≥ 80 % ; suites existantes vertes.
- [ ] Docs design / testing alignés ; non-goals explicites.

**Performance :** N/A formel — bornes config (`max-body-bytes`, rate limit)
documentées ; pas de cible latence.

## Constraints & Assumptions

### Technical constraints

- Java 25 · Spring Boot 4.1 · Flowable 8 · Liquibase · H2 MODE=PostgreSQL IT.
- Mono-tenant instance (décision n° 13) ; routage chemin (n° 14).
- Domaine pur records (n° 17) ; `webhookAuthType` dans `config` (n° 18).
- Outbox option B (n° 16) — producteur ici, relais déjà livré.

### Assumptions (acceptées)

- A1 / **D-SCOPE** — Trigger-first + `flowable-signal` BOTH ; autres builtins hors scope.
- A2 / **D-CRYPTO** — Port + clé maître env V1 ; Vault hors happy-path.
- A3 / **D-ADMIN** — Mutateurs + lecture admin = `TENANT_ADMIN` seul.
- A4 / **D-WEBHOOK-AUTH** — HMAC + SHARED_SECRET ; IP différée.
- A5 / **D-MAP** — Mapping déclaratif dans config instance.
- A6 / **D-HARDEN** — Max body + rate limit in-memory.
- A7 / **D-TARGET** — Batch 1..N ; create + existing reference.
- A8 / **D-IDEM** — `Idempotency-Key` / `X-Delivery-Id`.
- A9 / **D-SIGNAL** — `flowable-signal` TRIGGER + SERVICE.
- A10 / **D-SERVICE-INVOKE** — ServiceTask + `/execute`.
- A11 / **D-EXECUTE-AUTH** — BFF admin + M2M `API_SERVICE`.
- A12 — `enabled=false` → webhook **404**.
- A13 — Secrets `PASSWORD` masqués dans réponses admin.
- A14 — Rate limit in-memory = best-effort multi-pod V1.
- A15 — `http-rest` SERVICE hors scope (SSRF).

## Questions & Open Items

| # | Item | Disposition |
|---|---|---|
| Q1 | Périmètre builtins V1 | **Clos** — http-rest TRIGGER + flowable-signal BOTH |
| Q2 | Backend crypto V1 | **Clos** — D-CRYPTO 3 |
| Q3 | Authz admin | **Clos** — D-ADMIN 1 |
| Q4 | Auth webhook | **Clos** — D-WEBHOOK-AUTH 3 |
| Q5 | Mapping payload | **Clos** — D-MAP 2 |
| Q6 | Hardening | **Clos** — D-HARDEN 2 |
| Q7 | Cibles trigger | **Clos** — D-TARGET 2 |
| Q8 | Idempotence | **Clos** — D-IDEM 2 |
| Q9 | Invoke SERVICE | **Clos** — D-SERVICE-INVOKE 2 + D-EXECUTE-AUTH 3 |
| Q10 | Schéma exact JSON mapping | **Phase 3 design** — champs config nommés |
| Q11 | TTL idempotency / fenêtres rate limit | **Phase 3 design** — defaults properties |
| Q12 | Provider Vault réel | **Différé** — non-goal V1 |

### Décisions actées (phase 1)

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
| D-SIGNAL | flowable-signal | TRIGGER + SERVICE |
| D-SERVICE-INVOKE | Invoke SERVICE | ServiceTask + /execute |
| D-EXECUTE-AUTH | Authz execute | BFF admin + M2M API_SERVICE |

**Phase 1** — draft prêt pour revue requirements (phase 2).

## Sources

- `analysis doc/06-connecteurs.md`, `03-authentification.md`, `05-workflow-engine.md`
- Décisions TODO n° 3, 4, 5, 14, 15, 16, 17, 18 ; TODO §30 (partiel)
- `DEFERRED-FEATURES.md` — ordre feature connecteurs
- Features livrées : `workflow-engine` (outbox), `authentification` (BFF),
  `autorisation-conformite` (rôles / method security)
