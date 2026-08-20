---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: draft
created: 2026-08-20
updated: 2026-08-20
feature: observabilite
phase1: 2026-08-20
stakeholder_decisions: 2026-08-20
todo_ref: TODO.md §29 · FILE-ATTENTE.md · REVUE-PRE-IMPLEMENTATION.md §29 · DEFERRED-FEATURES.md
---

# Requirements & Problem Understanding — `observabilite`

> Prochaine feature produit ([FILE-ATTENTE.md](../../../FILE-ATTENTE.md),
> 2026-08-19) après `openapi-pagination-admin` (PR #58). Lot TODO **§29**.
> Phase 1 **draft**. **D-FE acté** (2026-08-20) : visualisation dans
> l’UI admin **dans le périmètre**. Q1–Q3 et Q4–Q6 encore ouvertes
> (Phase 2). Ne pas trancher silencieusement un point ouvert de
> `TODO.md`.

## Problem Statement
**What problem are we solving?**

Ops et support ne peuvent **pas** corréler un incident au tenant, au
dossier ou à l’instance Flowable, ni mesurer la santé du déclenchement.
Aucun document d’analyse ne traitait l’observabilité ; la revue
pré-implémentation (point 29) a listé le minimum, encore **non livré**.

- **Affectés :** ops / SRE (scraping, logs) ; **`TENANT_ADMIN`**
  (écran admin de santé) ; support (corrélation d’incident) ;
  conformité (traçabilité des lectures — encore ouverte) ; la
  plateforme elle-même (détection d’une outbox coincée).
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
  - SPA admin : Types, Templates, Connecteurs, File purge, Webhooks,
    Workflows — **aucun** écran de santé instance.
- **Réf. :** TODO §29, REVUE §29, `analysis doc/12` (lectures),
  `07` (PDF), `09` (webhooks DLQ / SSE), `11-frontend.md` (PrimeVue /
  même origine), DEFERRED §1.2 / §4.

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
- Écran admin **lecture** des cinq SLI §29 (**D-FE** acté) : route
  `/admin/observabilite`, nav Admin, contrat JSON BFF (pas le scrape
  Prometheus dans le browser).
- Trancher **Q1** (traçabilité des lectures) et **Q4–Q6** (forme UI,
  rôle exact, rafraîchissement) en Phase 2.

### Secondary goals

- `tenantId` MDC depuis `TenantProperties` (filtre HTTP **et** threads
  de jobs), pas depuis un claim par requête.
- Noms de métriques / runbook scrape documentés (doc monitoring).
- ArchUnit : Micrometer / MDC **hors domaine**.
- OpenAPI : documenter le GET snapshot si `app.openapi.enabled`
  (même discipline que les GET admin §24).

### Non-goals (proposés)

- Traces distribuées OpenTelemetry / APM (Datadog, New Relic, etc.).
- Dashboards Grafana-as-code, Alertmanager dans le dépôt.
- **Rapports / SLA** (maquette FILE-ATTENTE, hors V1) — distincts de
  l’écran admin de **santé instance**.
- Historique / time-series persisté, Chart.js / librairie graphe
  (sauf si Q4 = B en Phase 2).
- Le SPA ne scrape **pas** `/actuator/prometheus`.
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
| B | **MDC + Micrometer/Prometheus + actuator** | Aligné §29 ; scrape ops standard Spring | **Recommandé (D-APPROACH)** — **complété par D-FE** |
| C | APM / OTel + Grafana-as-code | Lourd, hors liste §29, stack non figée | Écarté V1 |
| D | SPA admin **uniquement** (pas Prometheus) | TENANT_ADMIN voit la santé ; ops sans scrape | Écarté — §29 exige Micrometer **et** D-FE exige l’UI |

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
6. **En tant que** `TENANT_ADMIN`, **je veux** un écran admin qui
   visualise les cinq SLI §29 (âge outbox déclenchement, jobs
   Flowable en échec, durée PDF, webhooks DEAD, SSE actifs), **afin
   de** juger la santé de l’instance **sans** accès Prometheus.
7. **En tant que** `DOSSIER_MANAGER` / `DOSSIER_VIEWER`, **je ne
   veux pas** voir cet écran ni l’entrée de nav, **afin de** garder
   l’admin réservé (même pattern que File purge).
8. **En tant que** conformité (si **Q1 = B**), **je veux** un
   événement d’audit structuré quand un utilisateur **lit** un dossier
   ou un contenu de pièce, **afin de** répondre aux exigences
   assurance / secteur public **sans** écran d’audit dédié.

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
- Snapshot API : valeurs absentes / timer PDF jamais observé → `0` ou
  `null` documenté (Phase 3) ; l’UI affiche un état « n/a » pas un
  crash.
- 403 sur GET snapshot pour non-`TENANT_ADMIN` (Q5 reco A).
- SPA : **pas** d’appel à `/actuator/**` (port management hors BFF).

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
- [ ] `GET` snapshot JSON des cinq SLI, session BFF, **TENANT_ADMIN**
      (Q5 reco A) ; 403 sinon.
- [ ] SPA `/admin/observabilite` : nav Admin, lecture seule, les cinq
      indicateurs visibles (forme Q4) ; **aucun** bouton d’action
      métier (pas de retry job / purge webhook).
- [ ] Vitest : vue + AuthZ nav. Playwright : spec dédié (admin voit,
      non-admin ne voit pas) **et** régression suite existante.
- [ ] `./mvnw verify` vert.

## Constraints & Assumptions

**Contraintes stables :**

- Hexagone : infrastructure → domain. Micrometer / logback / Flowable
  `ManagementService` = adapters.
- Une instance = un tenant (`app.tenant.id`).
- BFF + SPA même origine ; actuator **n’est pas** un API métier SPA ;
  le snapshot JSON **l’est**.
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
| **D-FE** | SPA | **Acté 2026-08-20** — écran admin lecture `/admin/observabilite` |
| **D-API-UI** | Contrat SPA | `GET` JSON BFF (pas Prometheus text) |
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
- **A-UI-COMP :** vue `ObservabilityAdminView` + grille de KPI
  (composants enfants) + `frontend/src/api/observability.ts` ;
  Composition API `<script setup lang="ts">` ; PrimeVue existant
  (**pas** de nouvelle lib graphe si Q4 = A).
- **A-UI-ACTION :** lecture seule — pas de retry Flowable / replay
  webhook depuis cet écran.

## Questions & Open Items

À **confirmer en Phase 2** — une décision produit à la fois si besoin.
Recommandations Phase 1 ci-dessous ; **pas actées**.

### Ouvertes

| Id | Question | Pourquoi ça compte | Options | Reco |
|---|---|---|---|---|
| **Q1** | Traçabilité des **accès en lecture** (`12` l. 79-81 / 214-216) | TODO §29 la laisse ouverte ; assurance / secteur public | **A** hors V1 (écritures + 403 seulement) · **B** log `SECURITY_AUDIT` INFO sur `GET /api/dossiers/{id}` (et `?ref=`) + `GET /api/dossier-elements/{id}/content` — pas de table, pas d’UI, pas la liste paginée · **C** table d’audit + écran admin | **B** — minimum conformité sans nouvelle feature UI |
| **Q2** | Qui peut scraper Prometheus ? | Le BFF est internet-facing ; OIDC inadapté à Prometheus | **A** `management.server.port` distinct, endpoints non exposés par le reverse-proxy public · **B** même port, `TENANT_ADMIN` seulement · **C** même port, `prometheus`+`health` anonymes · **D** basic-auth scrape | **A** — scrape ops sans ouvrir l’internet |
| **Q3** | Faut-il aussi l’âge des outbox **notification** et **purge** ? | §29 dit « l’outbox » au singulier = déclenchement | **A** déclenchement seul · **B** trois jauges | **A** — rester sur la liste §29 ; B = follow-up |
| **Q4** | Forme de la visualisation admin | « Visualisation » ≠ Rapports/SLA ; pas de TSDB in-app | **A** cartes KPI + sévérité (ok/warn/crit) PrimeVue, snapshot courant (pattern File purge) · **B** + Chart.js / histogramme PDF · **C** historique persisté | **A** — pas de nouvelle dépendance graphe ; C hors V1 |
| **Q5** | Rôle de l’écran / GET | Alignement nav admin existante | **A** `TENANT_ADMIN` seul (comme File purge / Webhooks) · **B** + `WORKFLOW_ADMIN` | **A** |
| **Q6** | Rafraîchissement | Données live vs charge API | **A** chargement à l’ouverture + bouton Rafraîchir · **B** + poll automatique (ex. 30 s) | **A** — comme File purge ; B = nice-to-have Phase 3 |

### Clos (hors Phase 2 — déjà actés ailleurs)

| Id | Décision | Choix |
|---|---|---|
| Décision n° 13 | Tenant | Constante d’instance — MDC `tenantId` = `app.tenant.id` |
| **D-FE** (stakeholder 2026-08-20) | UI admin | Visualisation santé **dans** `observabilite` ; Rapports/SLA restent hors V1 |
| DEFERRED §0 | MinIO | Toujours pas |

## Phase 1 — synthèse

**Compréhension partagée :** livrer le socle §29 (logs MDC structurés
+ cinq métriques Micrometer + actuator) **et** un parcours admin
lecture (`GET` JSON + `/admin/observabilite`) pour visualiser ces
cinq SLI. Rapports/SLA hors V1. **Q1** reste le point TODO ouvert.
**Q2–Q6** à figer en revue requirements.

**Prochaine phase :** `dev-requirements` Phase 2 (revue) — confirmer
Q1–Q6 puis passer le status à `reviewed`.
