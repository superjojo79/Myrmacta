---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: draft
created: 2026-08-20
updated: 2026-08-20
feature: observabilite
phase1: 2026-08-20
todo_ref: TODO.md §29 · FILE-ATTENTE.md · REVUE-PRE-IMPLEMENTATION.md §29 · DEFERRED-FEATURES.md
---

# Requirements & Problem Understanding — `observabilite`

> Prochaine feature produit ([FILE-ATTENTE.md](../../../FILE-ATTENTE.md),
> 2026-08-19) après `openapi-pagination-admin` (PR #58). Lot TODO **§29**.
> Phase 1 **draft** — recommandations ci-dessous, pas encore actées
> (Phase 2). Ne pas trancher silencieusement un point ouvert de `TODO.md`.

## Problem Statement
**What problem are we solving?**

Ops et support ne peuvent **pas** corréler un incident au tenant, au
dossier ou à l’instance Flowable, ni mesurer la santé du déclenchement.
Aucun document d’analyse ne traitait l’observabilité ; la revue
pré-implémentation (point 29) a listé le minimum, encore **non livré**.

- **Affectés :** ops / SRE (scraping, logs) ; support (corrélation
  d’incident) ; conformité (traçabilité des lectures — encore ouverte) ;
  la plateforme elle-même (détection d’une outbox coincée).
- **Situation actuelle :**
  - Pas de `spring-boot-starter-actuator` ni de registre Prometheus
    (`pom.xml`).
  - Logs texte SLF4J ; MDC **absent**. `tenantId` est une constante
    d’instance (`app.tenant.id`, décision n° 13) jamais injectée dans
    les logs.
  - `SecurityAuditLog` : 403 (tenant mismatch / access denied) + PATCH
    champ `sensitive`. **Pas** d’audit des GET.
  - Outbox déclenchement (`OutboxRepository`) : PENDING / DEAD, **pas**
    de requête « plus ancien non traité ».
  - PDF (`FlyingSaucerPdfAdapter`) : pas de timer ; `07` donne des
    ordres de grandeur (50 ms – 2 s) **non vérifiables** en prod.
  - Webhooks sortants : `notification_outbox` statut `DEAD` après
    `max-attempts` — **pas** de jauge DLQ.
  - SSE : `SseNotificationAdapter` tient les `SseEmitter` en mémoire —
    **pas** de jauge.
  - Jobs Flowable : `async-executor-activate: true` — **pas** de
    métrique d’échecs / dead-letter.
- **Réf. :** TODO §29, REVUE §29, `analysis doc/12` (lectures),
  `07` (PDF), `09` (webhooks DLQ / SSE), DEFERRED §1.2 / §4.

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

- Logs **structurés** avec MDC `tenantId`, `dossierId`,
  `processInstanceId` (clés exactes TODO §29).
- Métriques **Micrometer** exposées en Prometheus :
  1. âge du plus ancien événement **non traité** de l’outbox
     **déclenchement** (santé n° 1) ;
  2. jobs Flowable en échec (dead-letter / failed) ;
  3. durée des rendus PDF ;
  4. webhooks sortants en dead-letter (`notification_outbox` `WEBHOOK`
     + `DEAD`) ;
  5. nombre d’`SseEmitter` actifs.
- Trancher **Q1** (traçabilité des lectures) en Phase 2 — options
  ci-dessous, recommandation **B**.

### Secondary goals

- `tenantId` MDC depuis `TenantProperties` (filtre HTTP **et** threads
  de jobs), pas depuis un claim par requête.
- Noms de métriques / runbook scrape documentés (doc monitoring).
- ArchUnit : Micrometer / MDC **hors domaine**.

### Non-goals (proposés)

- Traces distribuées OpenTelemetry / APM (Datadog, New Relic, etc.).
- Dashboards Grafana-as-code, Alertmanager dans le dépôt.
- Écran SPA « métriques » / Rapports / SLA (hors V1, FILE-ATTENTE).
- Codegen OpenAPI, antivirus PJ, `http-rest` SERVICE.
- `platform.tenant_migration_status` (provisioning, DEFERRED §1.2).
- Testcontainers / charge Flowable (TODO §28).
- HealthIndicator qui passe DOWN sur lag d’outbox (flapping) — jauge
  seulement.
- Journaliser valeurs de champs, payloads webhook, secrets, HTML PDF.
- Changer le modèle multi-tenant (décision n° 13).

## Alternatives considérées (approche globale)

| Id | Approche | Trade-off | Verdict Phase 1 |
|---|---|---|---|
| A | Logs MDC seuls | Corrélation OK, pas de SLI scrapeable | Écarté — §29 exige Micrometer |
| B | **MDC + Micrometer/Prometheus + actuator** | Aligné §29 ; scrape ops standard Spring | **Recommandé (D-APPROACH)** |
| C | APM / OTel + Grafana-as-code | Lourd, hors liste §29, stack non figée | Écarté V1 |

## User Stories & Use Cases

1. **En tant qu’** ops, **je veux** scraper `/actuator/prometheus` et
   lire l’âge de l’outbox déclenchement, **afin de** détecter un relais
   coincé avant les tickets métier.
2. **En tant qu’** ops, **je veux** `tenantId` (et dossier / PI quand
   connus) sur **chaque** ligne de log HTTP et job, **afin de** filtrer
   un incident sans grep manuel d’UUID dans le message.
3. **En tant qu’** ops, **je veux** un histogramme de durée PDF, **afin
   de** vérifier les ordres de grandeur de `07` en production.
4. **En tant qu’** ops, **je veux** le nombre de webhooks `DEAD` et de
   jobs Flowable en échec, **afin de** prioriser le retraitement
   manuel.
5. **En tant qu’** ops, **je veux** le nombre d’`SseEmitter` vivants,
   **afin de** voir si le canal navigateur sature la JVM (décision
   n° 13 : une réplique).
6. **En tant que** conformité (si **Q1 = B**), **je veux** un
   événement d’audit structuré quand un utilisateur **lit** un dossier
   ou un contenu de pièce, **afin de** répondre aux exigences
   assurance / secteur public **sans** écran dédié.

### Edge cases

- Requête sans dossier / PI (listes admin, health) : MDC `tenantId`
  seul ; clés dossier/PI **absentes** (pas de `null` / `-`).
- Job planifié : même `tenantId` ; `dossierId` / `processInstanceId`
  posés autour du traitement d’une ligne, **cleared** ensuite (pas de
  fuite inter-jobs sur le thread pool).
- Outbox vide : âge = `0` (ou jauge absente documentée — Phase 3).
- SSE timeout / erreur : la jauge suit `unregister` existant.
- Prometheus ne fait pas d’OIDC : **Q2** (exposition actuator).
- PDF en erreur : timer enregistré (succès **et** échec).
- Lecture liste dossiers (`GET /api/dossiers` paginé) : **hors** audit
  lecture V1 si Q1 = B (bruit) ; seulement détail + contenu binaire.

## Success Criteria
**How will we know when we're done?**

- [ ] Logs applicatifs structurés (JSON stdout, **D-LOG**) contenant
      `tenantId` ; `dossierId` / `processInstanceId` présents sur les
      requêtes / jobs qui les connaissent.
- [ ] MDC nettoyé en `finally` (HTTP + jobs) — pas de fuite entre
      requêtes.
- [ ] Cinq métriques §29 scrapeables (noms figés en Phase 3) :
      âge outbox déclenchement, jobs Flowable failed/DLQ, timer PDF,
      compteur webhooks DEAD, jauge SSE.
- [ ] Actuator : seuls `health` + `prometheus` exposés ; sécurité
      selon **Q2** (recommandation A).
- [ ] Domaine pur : zéro `MDC` / `MeterRegistry` / actuator dans
      `com.flowforge.domain` ; ArchUnit vert.
- [ ] Aucune valeur sensible dans les logs (aligné `SecurityAuditLog`).
- [ ] Q1 tranchée en Phase 2 et implémentée (ou explicitement hors
      lot).
- [ ] `./mvnw verify` vert.
- [ ] SPA non modifié : pas de nouveau contrat consommé par le
      frontend ; Playwright = **régression** suite existante (pas de
      spec actuator). Vitest inchangé.

## Constraints & Assumptions

**Contraintes stables :**

- Hexagone : infrastructure → domain. Micrometer / logback / Flowable
  `ManagementService` = adapters.
- Une instance = un tenant (`app.tenant.id`).
- BFF + SPA même origine ; actuator **n’est pas** un API métier SPA.
- Secrets / payloads : jamais dans les logs (TODO §30 partiel).
- Stack figée : pas de Redis, pas d’APM hors Boot/Micrometer.
- Task tracing `ai-devkit task` **indisponible** (`task` unknown).
- Branche Cloud : `cursor/observabilite-9800` (lint `feature-*` = MISS
  attendu).

**Recommandations Phase 1 (à confirmer Phase 2) :**

| Id | Sujet | Recommandation |
|---|---|---|
| **D-APPROACH** | Socle | B — MDC + Micrometer Prometheus |
| **D-LOG** | Format | JSON structuré Spring Boot (`logging.structured.format.console`) |
| **D-MDC** | Clés | `tenantId` / `dossierId` / `processInstanceId` ; put/clear |
| **D-OUTBOX** | Âge | Outbox **déclenchement** seulement (`OutboxRepository`) |
| **D-DLQ** | Webhooks | `notification_outbox` `channel=WEBHOOK` `status=DEAD` |
| **D-PDF** | Timer | Autour de `PdfRenderer.render` (adapter) |
| **D-SSE** | Jauge | `SseNotificationAdapter` (somme des emitters) |
| **D-FLOWABLE** | Jobs | Dead-letter + failed via API Flowable **infra** |
| **D-FE** | SPA | Aucun écran / contrat ; E2E = régression |
| **D-AUDIT-READ** | Lectures | **B** si Q1 confirmée (voir ouvertures) |
| **D-ACTUATOR** | Surface | `health` + `prometheus` uniquement |
| **D-MGMT** | Auth scrape | **A** — port management séparé, scrape réseau |

**Assumptions techniques (A-*, détail Phase 3) :**

- **A-BINDER :** `MeterBinder` / `@Scheduled` gauges, pas d’annotation
  Micrometer dans le domaine.
- **A-PORT-AGE :** nouvelle requête lecture sur `OutboxRepository`
  (âge / `createdAt` du plus ancien PENDING) — port out, pas de
  framework.
- **A-EMPTY-AGE :** outbox vide → âge `0` secondes.
- **A-HEALTH :** probes liveness/readiness Boot ; pas de check métier
  outbox.
- **A-JSON :** format `ecs` ou `logstash` (choix exact Phase 3 selon
  Boot 4.1).

## Questions & Open Items

À **confirmer en Phase 2** — une décision produit à la fois si besoin.
Recommandations Phase 1 ci-dessous ; **pas actées**.

### Ouvertes

| Id | Question | Pourquoi ça compte | Options | Reco |
|---|---|---|---|---|
| **Q1** | Traçabilité des **accès en lecture** (`12` l. 79-81 / 214-216) | TODO §29 la laisse ouverte ; assurance / secteur public | **A** hors V1 (écritures + 403 seulement) · **B** log `SECURITY_AUDIT` INFO sur `GET /api/dossiers/{id}` (et `?ref=`) + `GET /api/dossier-elements/{id}/content` — pas de table, pas d’UI, pas la liste paginée · **C** table d’audit + écran admin | **B** — minimum conformité sans nouvelle feature UI |
| **Q2** | Qui peut scraper Prometheus ? | Le BFF est internet-facing ; OIDC inadapté à Prometheus | **A** `management.server.port` distinct, endpoints non exposés par le reverse-proxy public · **B** même port, `TENANT_ADMIN` seulement · **C** même port, `prometheus`+`health` anonymes · **D** basic-auth scrape | **A** — scrape ops sans ouvrir l’internet |
| **Q3** | Faut-il aussi l’âge des outbox **notification** et **purge** ? | §29 dit « l’outbox » au singulier = déclenchement | **A** déclenchement seul · **B** trois jauges | **A** — rester sur la liste §29 ; B = follow-up |

### Clos (hors Phase 2 — déjà actés ailleurs)

| Id | Décision | Choix |
|---|---|---|
| Décision n° 13 | Tenant | Constante d’instance — MDC `tenantId` = `app.tenant.id` |
| FILE-ATTENTE | SPA métriques / SLA | Hors V1 |
| DEFERRED §0 | MinIO | Toujours pas |

## Phase 1 — synthèse

**Compréhension partagée :** livrer le **socle ops** de §29 (logs MDC
structurés + cinq métriques Micrometer + actuator restreint), sans
parcours SPA. La seule décision produit encore ouverte **dans TODO**
est **Q1** (lectures). **Q2** et **Q3** sont des précisions d’implémentation
/ périmètre métriques à figer en revue requirements.

**Prochaine phase :** `dev-requirements` Phase 2 (revue) — confirmer
Q1–Q3 puis passer le status à `reviewed`.
