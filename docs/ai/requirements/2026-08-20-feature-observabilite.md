---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: draft
created: 2026-08-20
updated: 2026-08-20
feature: observabilite
phase1: 2026-08-20
phase2: 2026-08-20
stakeholder_decisions: 2026-08-20
todo_ref: TODO.md §29 · FILE-ATTENTE.md · REVUE-PRE-IMPLEMENTATION.md §29 · DEFERRED-FEATURES.md
---

# Requirements & Problem Understanding — `observabilite`

> Feature produit TODO **§29**. **D-FE** et **Q1 = B** actés. Phase 2
> **en cours** : Q2–Q6 à valider **une à une**. Status **draft**.

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
  1. âge du plus ancien PENDING de l’outbox **déclenchement** (**Q3 = A**) ;
  2. jobs Flowable en échec (dead-letter / failed) ;
  3. durée des rendus PDF (timer — histogramme **Prometheus**, pas UI) ;
  4. webhooks sortants `WEBHOOK` + `DEAD` ;
  5. nombre d’`SseEmitter` actifs.
- Écran admin lecture **`/admin/observabilite`** (**D-FE**) : cartes
  KPI + sévérité (**Q4 = A**), rôle **`TENANT_ADMIN`** (**Q5 = A**),
  refresh manuel (**Q6 = A**), contrat **`GET /api/observability`**.
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
- Chart.js, histogramme UI, historique / time-series persisté (**Q4 ≠ B/C**).
- SPA scrape `/actuator/prometheus`.
- Actuator sur le port BFF public (**Q2 = A**).
- Âge des outbox notification / purge (**Q3 = A**).
- Poll automatique de l’écran (**Q6 ≠ B**).
- Accès `WORKFLOW_ADMIN` à l’écran (**Q5 ≠ B**).
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

1. **En tant qu’** ops, **je veux** scraper Prometheus (port
   management) et lire l’âge de l’outbox déclenchement, **afin de**
   détecter un relais coincé.
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
   avec cinq cartes KPI (snapshot + sévérité + Rafraîchir), **afin de**
   juger la santé **sans** Prometheus.
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
- Outbox vide : âge `0` (**A-EMPTY-AGE**).
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

- [ ] Logs JSON stdout avec `tenantId` ; `dossierId` /
      `processInstanceId` quand le contexte les connaît.
- [ ] MDC nettoyé en `finally` (HTTP + jobs).
- [ ] Cinq métriques §29 scrapeables sur le **port management**
      uniquement (`health` + `prometheus` ; **Q2 = A**).
- [ ] Domaine pur : zéro MDC / Micrometer / actuator ; ArchUnit vert.
- [ ] Aucune valeur sensible dans les logs.
- [ ] `GET /api/observability` : `TENANT_ADMIN` → 200 + cinq SLI ;
      autre rôle → 403.
- [ ] SPA `/admin/observabilite` : nav « Observabilité » pour
      `TENANT_ADMIN` seulement ; cinq cartes KPI + sévérité ; bouton
      Rafraîchir ; **aucun** bouton métier.
- [ ] Q1 = B implémenté (GET détail dossier + content, succès
      seulement).
- [ ] Vitest (vue + nav AuthZ) **et** Playwright (admin voit /
      non-admin ne voit pas + régression).
- [ ] `./mvnw verify` vert.
- [ ] TODO §29 / FILE-ATTENTE / DEFERRED mis à jour **après**
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

**Recommandations Phase 2 (en attente de validation, Q1 d’abord) :**

| Id | Sujet | Choix |
|---|---|---|
| **D-APPROACH** | Socle | B — MDC + Micrometer Prometheus |
| **D-FE** | SPA | `/admin/observabilite` lecture (stakeholder) |
| **D-API-UI** | Contrat | `GET /api/observability` JSON BFF |
| **D-LOG** | Format | JSON structuré Boot (`logging.structured.format.console`) |
| **D-MDC** | Clés | `tenantId` / `dossierId` / `processInstanceId` |
| **D-OUTBOX** / **Q3** | Âge | Outbox **déclenchement** seulement |
| **D-DLQ** | Webhooks | `WEBHOOK` + `DEAD` |
| **D-PDF** | Timer | Autour de `PdfRenderer.render` ; UI = count/mean/max |
| **D-SSE** | Jauge | Somme des emitters |
| **D-FLOWABLE** | Jobs | Dead-letter + failed, adapter infra |
| **D-AUDIT-READ** / **Q1** | Lectures | **B acté** — logs `SECURITY_AUDIT` |
| **D-ACTUATOR** | Surface | `health` + `prometheus` |
| **D-MGMT** / **Q2** | Scrape | Port management séparé, non public |
| **Q4** | UI | Cartes KPI + sévérité ; pas Chart.js |
| **Q5** | Rôle | `TENANT_ADMIN` seul |
| **Q6** | Refresh | Ouverture + bouton Rafraîchir |

**Assumptions techniques (A-*, détail Phase 3) :**

- **A-BINDER :** `MeterBinder` / gauges ; pas d’annotation domaine.
- **A-PORT-AGE :** lecture âge sur `OutboxRepository`.
- **A-EMPTY-AGE :** outbox vide → `0` secondes.
- **A-HEALTH :** probes Boot sur le port management ; pas de DOWN métier.
- **A-JSON :** `ecs` vs `logstash` — choix Boot 4.1 en Phase 3.
- **A-THRESHOLDS :** seuils ok/warn/crit des cartes (alignés runbook).
- **A-FLOWABLE-API :** failed vs dead-letter Flowable 8.
- **A-PROM-NAMES :** noms métriques + **aucun** label `dossierId`.
- **A-UI-COMP :** `ObservabilityAdminView` + grille KPI +
  `api/observability.ts` ; Composition API ; PrimeVue existant.
- **A-UI-ACTION :** lecture seule.

## Questions & Open Items

### Clos

| Id | Décision | Choix |
|---|---|---|
| **D-FE** | Écran admin | Acté stakeholder 2026-08-20 — `/admin/observabilite` lecture |
| **Q1 / D-AUDIT-READ** | Lectures | **B** (2026-08-20) — `SECURITY_AUDIT` INFO sur GET dossier id/`ref` et GET content **réussis** ; pas de table ni d’écran d’audit |

### Ouvertes (Phase 2 — une à la fois)

| Id | Question | Options | Reco |
|---|---|---|---|
| **Q2** | Qui scrape Prometheus ? | **A** port management non public · **B** même port `TENANT_ADMIN` · **C** anonyme sur le BFF · **D** basic-auth | **A** |
| **Q3** | Quelles outbox pour l’âge ? | **A** déclenchement seul · **B** + notification + purge | **A** |
| **Q4** | Forme UI | **A** cartes KPI + sévérité · **B** + Chart.js · **C** historique persisté | **A** |
| **Q5** | Rôle écran / GET | **A** `TENANT_ADMIN` · **B** + `WORKFLOW_ADMIN` | **A** |
| **Q6** | Rafraîchissement | **A** load + Rafraîchir · **B** + poll 30 s | **A** |

Posée maintenant : **Q2**. Q3–Q6 attendent.

### Hors file

Rapports/SLA, OTel, Grafana-as-code, codegen, antivirus, `http-rest`
SERVICE, Testcontainers §28.

## Phase 2 — revue en cours (2026-08-20)

Template README : OK.

Les reco Q2–Q6 **ne sont pas actées**. Clos : **D-FE**, **Q1 = B**.

Status : **`draft`**.

**En cours :** Q2 (scrape Prometheus).
