---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
created: 2026-08-20
updated: 2026-08-20
feature: observabilite
phase1: 2026-08-20
phase2: 2026-08-20
phase3: 2026-08-20
stakeholder_decisions: 2026-08-20
todo_ref: TODO.md §29 · FILE-ATTENTE.md · REVUE-PRE-IMPLEMENTATION.md §29 · DEFERRED-FEATURES.md
---

# Requirements & Problem Understanding — `observabilite`

> Feature produit TODO **§29**. **D-FE** et **Q1–Q6 actés**. Phase 2
> **reviewed**. Design Phase 3 **reviewed** (A-* = DD-*). Phases 7–9
> **fermées** (PR #59).

## Problem Statement
**What problem are we solving?**

Ops et support ne peuvent **pas** corréler un incident au tenant, au
dossier ou à l’instance Flowable, ni mesurer la santé du déclenchement.
Aucun document d’analyse ne traitait l’observabilité ; la revue
pré-implémentation (point 29) a listé le minimum, encore **non livré**.
Le `TENANT_ADMIN` n’a pas non plus de vue santé dans le SPA.

- **Affectés :** ops / SRE (scraping, logs) ; **`TENANT_ADMIN`**
  (écran admin) ; support (corrélation d’incident) ; conformité
  (traçabilité des lectures — **Q1 = B**) ; la plateforme (outbox
  coincée).
- **Situation actuelle :**
  - Pas de `spring-boot-starter-actuator` ni de registre Prometheus.
  - Logs texte SLF4J ; MDC **absent**. `tenantId` = `app.tenant.id`
    (décision n° 13) jamais injecté.
  - `SecurityAuditLog` : 403 + PATCH champ `sensitive`. **Pas**
    d’audit des GET réussis.
  - Outbox déclenchement : pas de requête « plus ancien PENDING ».
  - PDF : pas de timer (ordres `07` non vérifiables en prod).
  - Webhooks `DEAD` / SSE / jobs Flowable : pas de jauge.
  - SPA admin : pas d’écran de santé instance.
- **Réf. :** TODO §29, REVUE §29, `analysis doc/12`, `07`, `09`,
  `11-frontend.md`, DEFERRED §1.2 / §4.

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

- Logs **structurés** avec MDC `tenantId`, `dossierId`,
  `processInstanceId`.
- Métriques **Micrometer** scrapeables Prometheus :
  1. âge du plus ancien événement **non traité** de **chaque** outbox
     (**Q3 = B**) : déclenchement, notification, purge — trois jauges
     distinctes ;
  2. jobs Flowable en échec (dead-letter / failed) ;
  3. durée des rendus PDF (timer — histogramme **Prometheus**, pas UI) ;
  4. webhooks sortants `WEBHOOK` + `DEAD` ;
  5. nombre d’`SseEmitter` actifs.
- Écran admin lecture **`/admin/observabilite`** (**D-FE**) : cartes
  KPI + sévérité (**Q4 = A**), y compris **les trois âges
  d’outbox** ; rôle **`TENANT_ADMIN`** (**Q5 = A**) ; ouverture +
  bouton Rafraîchir (**Q6 = A**).
- Traçabilité lectures **Q1 = B** : logs `SECURITY_AUDIT` INFO sur
  les GET dossier détail et contenu binaire (pas de table, pas d’UI
  d’audit).

### Secondary goals

- `tenantId` MDC depuis `TenantProperties` (HTTP **et** jobs).
- Runbook scrape (doc monitoring).
- ArchUnit : Micrometer / MDC hors domaine.
- OpenAPI : documenter `GET /api/observability` si flag on (§24).

### Non-goals (actés)

- OpenTelemetry / APM ; Grafana-as-code ; Alertmanager in-repo.
- **Rapports / SLA** (FILE-ATTENTE hors V1).
- Chart.js, histogramme UI, historique / time-series persisté (**Q4 = A**).
- SPA scrape `/actuator/prometheus`.
- Actuator sur le port BFF public (**Q2 = A**).
- Une seule jauge d’âge « outbox générique » (**Q3 ≠ A**).
- Accès `WORKFLOW_ADMIN` à l’écran / GET (**Q5 ≠ B**).
- Poll automatique de l’écran (**Q6 ≠ B**).
- Table d’audit + écran d’audit lecture (**Q1 ≠ C**).
- Codegen OpenAPI, antivirus PJ, `http-rest` SERVICE.
- `platform.tenant_migration_status`.
- Testcontainers / charge Flowable (§28).
- HealthIndicator DOWN sur lag d’outbox.
- Journaliser valeurs de champs, payloads, secrets, HTML PDF.
- Retry Flowable / replay webhook depuis l’écran.
- Changer le modèle multi-tenant (décision n° 13).

## Alternatives considérées (approche globale)

| Id | Approche | Trade-off | Verdict |
|---|---|---|---|
| A | Logs MDC seuls | Pas de SLI scrapeable | Écarté — §29 |
| B | **MDC + Micrometer/Prometheus + actuator** | Scrape ops standard | **Retenu (D-APPROACH)** |
| C | APM / OTel + Grafana-as-code | Hors stack / §29 | Écarté V1 |
| D | SPA admin uniquement | Ops sans scrape | Écarté — les deux canaux sont requis (B **et** D-FE) |

**D-FE** (stakeholder) complète B : même SLI, JSON BFF + cartes admin.

## User Stories & Use Cases

1. **En tant qu’** ops, **je veux** scraper l’âge de **chaque** outbox
   (déclenchement, notification, purge), **afin de** voir quel relais
   décroche sans tout mélanger.
2. **En tant qu’** ops, **je veux** `tenantId` (et dossier / PI quand
   connus) sur chaque ligne de log HTTP et job, **afin de** filtrer
   un incident.
3. **En tant qu’** ops, **je veux** un **timer** Micrometer des rendus
   PDF (count / mean / max + buckets scrape), **afin de** vérifier
   les ordres de `07` — **pas** un graphe dans le SPA.
4. **En tant qu’** ops, **je veux** le nombre de webhooks `DEAD` et de
   jobs Flowable en échec, **afin de** prioriser le retraitement.
5. **En tant qu’** ops, **je veux** le nombre d’`SseEmitter` vivants,
   **afin de** voir si le canal navigateur sature la JVM.
6. **En tant que** `TENANT_ADMIN`, **je veux** `/admin/observabilite`
   avec les SLI §29 **et** les trois âges d’outbox, plus un bouton
   **Rafraîchir** (**Q6 = A**), **afin de** juger la santé **sans**
   Prometheus.
7. **En tant que** `WORKFLOW_ADMIN` / `DOSSIER_MANAGER` /
   `DOSSIER_VIEWER`, **je ne veux pas** cet écran ni le lien nav.
8. **En tant que** conformité, **je veux** un log `SECURITY_AUDIT`
   INFO quand un utilisateur **lit avec succès** un dossier (id ou
   `ref`) ou un contenu de pièce, **afin de** tracer les accès
   assurance / secteur public **sans** écran d’audit.

### Edge cases

- Requête sans dossier / PI : MDC `tenantId` seul ; pas de clés
  fantômes.
- Jobs : put/clear par item de batch (pas de fuite thread pool).
- Outbox vide (chacune) : âge `0` (**A-EMPTY-AGE**).
- PDF jamais rendu : `pdfRender.count = 0` ; UI mean/max = **n/a**.
- PDF en erreur : timer observé (succès et échec).
- SSE timeout / erreur : jauge suit `unregister`.
- GET snapshot : 403 hors `TENANT_ADMIN` ; SPA n’appelle pas
  `/actuator/**`.
- **Q1 = B** : pas d’événement `dossier_read` sur liste paginée,
  éléments, relations, diagramme, templates. Pas de second log si
  la lecture est **refusée** (le `access_denied` WARN suffit). Pas
  de `element_content_read` si le contenu n’est pas servi (404 /
  non téléchargeable). Jamais de valeurs `fields` / bytes.

## Success Criteria
**How will we know when we're done?**

- [x] Logs JSON stdout avec `tenantId` ; `dossierId` /
      `processInstanceId` quand le contexte les connaît.
- [x] MDC nettoyé en `finally` (HTTP + jobs).
- [x] SLI §29 **plus** trois jauges d’âge outbox (déclenchement,
      notification, purge — **Q3 = B**) scrapeables sur le **port
      management** uniquement (`health` + `prometheus` ; **Q2 = A**).
- [x] Domaine pur : zéro MDC / Micrometer / actuator ; ArchUnit vert.
- [x] Aucune valeur sensible dans les logs.
- [x] `GET /api/observability` : `TENANT_ADMIN` → 200 + SLI dont **trois
      âges** ; autre rôle → 403.
- [x] SPA `/admin/observabilite` : nav « Observabilité » (`TENANT_ADMIN`)
      ; cartes KPI des SLI + trois âges ; bouton Rafraîchir ; **aucun**
      bouton métier.
- [x] Q1 = B implémenté (GET détail dossier + content, succès
      seulement).
- [x] Vitest (vue + nav AuthZ) **et** Playwright (admin voit /
      non-admin ne voit pas + régression).
- [x] `./mvnw verify` vert.
- [x] TODO §29 / FILE-ATTENTE / DEFERRED mis à jour **après**
      implémentation.

## Constraints & Assumptions

**Contraintes stables :**

- Hexagone : Micrometer / logback / Flowable `ManagementService` =
  infrastructure.
- Une instance = un tenant (`app.tenant.id`).
- BFF + SPA même origine ; actuator ≠ API SPA ; snapshot JSON = API SPA.
- Stack figée : pas de Redis, pas d’APM hors Boot/Micrometer.
- Task tracing `ai-devkit task` indisponible.
- Branche Cloud : `cursor/observabilite-9800` (lint `feature-*` = MISS
  attendu).

**Décisions produit Phase 2 (toutes actées) :**

| Id | Sujet | Choix |
|---|---|---|
| **D-APPROACH** | Socle | B — MDC + Micrometer Prometheus |
| **D-FE** | SPA | `/admin/observabilite` lecture (stakeholder) |
| **D-API-UI** | Contrat | `GET /api/observability` JSON BFF |
| **D-LOG** | Format | JSON structuré Boot (`logging.structured.format.console`) |
| **D-MDC** | Clés | `tenantId` / `dossierId` / `processInstanceId` |
| **D-OUTBOX** / **Q3** | Âge | **B acté** — trois jauges (déclenchement, notification, purge) |
| **D-DLQ** | Webhooks | `WEBHOOK` + `DEAD` |
| **D-PDF** | Timer | Autour de `PdfRenderer.render` ; UI = count/mean/max |
| **D-SSE** | Jauge | Somme des emitters |
| **D-FLOWABLE** | Jobs | Dead-letter + failed, adapter infra |
| **D-AUDIT-READ** / **Q1** | Lectures | **B acté** — logs `SECURITY_AUDIT` |
| **D-ACTUATOR** | Surface | `health` + `prometheus` |
| **D-MGMT** / **Q2** | Scrape | **A acté** — port management séparé, non public |
| **Q4** | UI | **A acté** — cartes KPI + sévérité ; pas Chart.js |
| **Q5** | Rôle | **A acté** — `TENANT_ADMIN` seul |
| **Q6** | Refresh | **A acté** — ouverture + bouton Rafraîchir |

**Assumptions techniques (A-* → DD-* Phase 3, figées) :**

- **A-BINDER / DD-NO-DOMAIN-UC :** `MeterBinder` infra ; pas d’annotation
  domaine ; pas de use case observabilité.
- **A-PORT-AGE :** lecture âge sur les trois ports outbox existants.
- **A-EMPTY-AGE / DD-EMPTY-AGE :** outbox vide → `0` secondes.
- **A-HEALTH / DD-HEALTH :** probes Boot sur le port management ; pas de
  DOWN métier.
- **A-JSON / DD-JSON :** `logging.structured.format.console=ecs`.
- **A-THRESHOLDS / DD-THRESHOLDS :** âges 60s/300s ; DLQ 0/1–9/≥10 ;
  webhook 0/1–4/≥5 ; PDF mean 2s/5s ; SSE 50/200 — calcul serveur.
- **A-FLOWABLE-API / DD-FLOWABLE-API :** dead-letter **et**
  `withException` (job + timer) ; pas de `FailedJobQuery` Community 8.
- **A-PROM-NAMES / DD-PROM-NAMES :** préfixe `flowforge.*` ; aucun label
  `tenant` / `dossierId`.
- **A-UI-COMP / DD-UI-WIDGETS :** vue + grille + cartes HTML ; pas
  Chart.js.
- **A-UI-ACTION :** lecture seule.

## Questions & Open Items

### Clos

| Id | Décision | Choix |
|---|---|---|
| **D-FE** | Écran admin | Acté stakeholder 2026-08-20 — `/admin/observabilite` lecture |
| **Q1 / D-AUDIT-READ** | Lectures | **B** (2026-08-20) — `SECURITY_AUDIT` INFO sur GET dossier id/`ref` et GET content **réussis** ; pas de table ni d’écran d’audit |
| **Q2 / D-MGMT** | Scrape Prometheus | **A** (2026-08-20) — `management.server.port` ; reverse-proxy public ne route pas ce port ; aucun `/actuator` sur le BFF |
| **Q3 / D-OUTBOX** | Âge outbox | **B** (2026-08-20) — trois jauges distinctes : déclenchement, notification, purge |
| **Q4** | Forme UI | **A** (2026-08-20) — cartes KPI + sévérité (ok/warn/crit), snapshot ; pas Chart.js ni historique |
| **Q5** | Rôle écran / GET | **A** (2026-08-20) — `TENANT_ADMIN` seul ; `WORKFLOW_ADMIN` exclus |
| **Q6** | Rafraîchissement | **A** (2026-08-20) — chargement à l’ouverture + bouton Rafraîchir ; pas de poll |

### Ouvertes

Aucune. A-* figés en Phase 3 (`docs/ai/design/2026-08-20-feature-observabilite.md`).

### Hors file

Rapports/SLA, OTel, Grafana-as-code, codegen, antivirus, `http-rest`
SERVICE, Testcontainers §28.

## Phase 2 — revue close (2026-08-20)

Template README : OK.

**Validé :** D-APPROACH = B + D-FE ; Q1–Q6 (une à une) ; non-goals ;
stories ; critères de succès ; `GET /api/observability` ; nav
« Observabilité ».

| Id | Choix |
|---|---|
| Q1 | **B** — logs `SECURITY_AUDIT` lectures réussies |
| Q2 | **A** — port management, Prometheus non public |
| Q3 | **B** — trois jauges d’âge outbox |
| Q4 | **A** — cartes KPI + sévérité |
| Q5 | **A** — `TENANT_ADMIN` seul |
| Q6 | **A** — load + Rafraîchir |

A-* figés Phase 3 (DD-JSON, DD-PROM-NAMES, DD-FLOWABLE-API,
DD-THRESHOLDS, DD-EMPTY-AGE, DD-HEALTH, …).

Status : **`reviewed`**. Phases 7–9 **fermées** (2026-08-20, PR #59).
