---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
created: 2026-08-26
updated: 2026-08-26
feature: reporting-sla
phase1: 2026-08-26
phase2: 2026-08-26
todo_ref: DEFERRED-FEATURES.md §1.6 · FILE-ATTENTE.md · analysis doc/16-reporting-sla.md · maquette UI
---

# Requirements & Problem Understanding — `reporting-sla`

## Phase 2 : Review Requirements

Phase 2 **reviewed** (2026-08-26). Template README OK (problem, goals,
stories, success, constraints, open items). Décisions Phase 1
conservées. Clarifications ci-dessous = **A-*** / précisions de
libellé, pas de nouveaux votes stakeholder.

Workspace : branche `cursor/reporting-sla-9009`, no-worktree (VM cloud).
Task tracing indisponible (`unknown command 'task'`). Lint feature :
docs OK ; check branche `feature-reporting-sla` = MISS attendu.

Écart connu pour la **Phase 3** : le design/planning du premier jet
est **stale** vs ces D-* (authz encore « A » dans le design, pas
d'API summary / `AT_RISK`, `D-SLA-NULL` vs **D-SLA-HOURS = B**). Ne
pas traiter ce design comme aligned tant que Phase 3 ne l'a pas
réécrit.

### Décisions déjà actées hors de cette phase

- Les **deux lots** Reporting **et** SLA sont dans le périmètre
  (analyse [16-reporting-sla.md](../../../analysis%20doc/16-reporting-sla.md),
  2026-08-26) — pas de version dégradée Lot 1 seul pour le MCP.

## Problem Statement
**What problem are we solving?**

Les gestionnaires et superviseurs n'ont **aucune vue métier** du
respect des délais. La maquette
[FlowForge - Maquettes UI.html](../../../analysis%20doc/FlowForge%20-%20Maquettes%20UI.html)
spécifie un écran **Rapports & SLA** (conformité, délai moyen,
répartition par type, temps par étape, charge par gestionnaire,
détail par type, export CSV). La feature `frontend` a **volontairement
masqué** cet écran (**D-MOCKGAP = A**) faute d'API et de cible SLA.

L'écran admin **Observabilité** (PR #59) mesure la santé *plateforme*
(outbox, Flowable DLQ, PDF, SSE) — ce n'est **pas** un SLA métier
dossiers.

- **Affectés :** `TENANT_ADMIN` / `WORKFLOW_ADMIN` (pilotage tenant) ;
  `DOSSIER_MANAGER` (charge, hors-délai sur les types visibles) ;
  gestionnaires en déplacement (échéance SLA sur la file de tâches).
- **Situation actuelle :**
  - Nav Pilotage **sans** « Rapports » (`AppShell`, E2E-9).
  - `DossierType` n'a pas de cible de délai ; `Dossier` a `createdAt` /
    `closedAt` mais aucun calcul de conformité.
  - Export CSV existant = lignes dossier (`GET /api/dossiers/export.csv`),
    pas un rapport agrégé.
  - Inbox tâches sans reste-à-courir SLA.
- **Réf. :** DEFERRED §1.6, FILE-ATTENTE « Hors V1 », requirements
  `frontend` D-MOCKGAP, maquette écran `Rapports`.

## Stakeholder decisions

### Actées en Phase 1

| Id | Décision | Source |
|---|---|---|
| **D-APPROACH = A+B** | Les **deux** formes : (A) API summary analyse (`groupBy`, durées, statut `ON_TRACK` / `AT_RISK` / `BREACHED`) **et** (B) snapshot maquette `/rapports` (KPI conformité, délai moyen, parts, temps/étape, charge gestionnaires, CSV, inbox remaining). BC domaine `reporting` + BFF. Pas Grafana, pas de recopie des KPI observabilité. | Stakeholder 2026-08-26 |
| **D-AUTHZ = C** | Lecture snapshot, CSV **et** summary : `TENANT_ADMIN` / `WORKFLOW_ADMIN` tout le tenant ; `DOSSIER_MANAGER` limité aux **types** avec ACL VIEW (même split que `SearchDossiersUseCaseImpl` : `DossierTypeAclEntry`, pas une ACL par dossier) ; `DOSSIER_VIEWER` / `API_SERVICE` → 403. Écriture `slaTargetHours` + settings : **`TENANT_ADMIN` uniquement** (déjà le cas des PATCH types). | Stakeholder 2026-08-26 |
| **D-SLA-HOURS = B** | Cible **uniquement par type**, **optionnelle**. Pas de défaut tenant heures, pas de sentinelle `NEVER` (contrairement à la rétention, décision n° 21). `null` = pas de SLA pour ce type : **exclu** du % de conformité (cellule « — »). Validation `> 0` si présent. | Stakeholder 2026-08-26 |
| **D-AT-RISK = A** | Convention **fixe 80 %** du délai. Dossier **ouvert** avec cible : `ON_TRACK` si `elapsed < 0,8 × slaTargetHours` ; `AT_RISK` si `0,8 × slaTargetHours ≤ elapsed ≤ slaTargetHours` ; `BREACHED` si `elapsed > slaTargetHours`. Dossier **clos** : seulement `ON_TRACK` / `BREACHED` (pas d'`AT_RISK`). Sans cible : statut `null`. Pas de champ `atRiskPercent` tenant. | Stakeholder 2026-08-26 |
| **D-GROUPBY = B** | Summary V1 : `groupBy=dossierTypeCode\|status` seulement. La période `from`/`to` est un **filtre**, pas des seaux calendaires. Jour / semaine / mois **hors V1** (le MCP rappelle le summary avec une autre fenêtre ; le snapshot garde le delta vs période précédente). | Stakeholder 2026-08-26 |
| **D-SLA-CLOCK = A** | Horloge **dossier** : début = `createdAt` ; fin = `closedAt` si clos (`CLOTURE` / `ARCHIVE`), sinon `now`. Hors délai / `BREACHED` ssi durée **>** `slaTargetHours`. Pas de `dueDate` Flowable. `ARCHIVE` sans `closedAt` : fin = `updatedAt` (**A-ARCHIVE-CLOCK**). Inbox : `slaRemainingHours = slaTargetHours - elapsed(createdAt, now)`. | Stakeholder 2026-08-26 |
| **D-PERIOD = A** | Cohorte = `createdAt ∈ [from, to]`. Défaut **30 jours** glissants UTC si `from`/`to` omis ; max **366 jours** ; `from >= to` ou trop large → 400. `to` exclusif (minuit du jour civil suivant si date seule). Tendance = même durée **immédiatement avant** `from`. Un dossier créé hors fenêtre n'entre pas dans le snapshot/summary (reste visible via l'inbox). | Stakeholder 2026-08-26 |
| **D-SETTINGS = A** | Objectif de conformité **paramétrable** : `TenantSlaSettings.complianceTargetPercent` (1–100). GET 404 si absent ; le calcul du snapshot utilise alors **95**. PUT upsert **TA only**. Singleton par schéma. **Pas** de `slaTargetHours` tenant ni de `atRiskPercent`. | Stakeholder 2026-08-26 |
| **D-COMPUTE = A** | Agrégats **à la volée** (SQL + historique Flowable). Pas de table de faits, pas de job nuit, pas de cache TTL V1. Garde-fou : 400 si cohorte > **10 000** dossiers (**A-COHORT-CAP**). Index lecture `(created_at)` : **A-INDEX**. | Stakeholder 2026-08-26 |
| **D-CHARTS = A** | `/rapports` : barres **CSS** + tableaux PrimeVue (tokens existants). **Pas** Chart.js, **pas** d'images serveur. | Stakeholder 2026-08-26 |
| **D-SCHEDULE = C** | Bouton « Planifier l'envoi » **visible, désactivé** (tooltip « bientôt »). Pas de cron, pas de mail, pas d'API schedule V1. | Stakeholder 2026-08-26 |

### Conséquences non démenties (verrouillées)

Déjà énoncées dès **D-APPROACH** / **D-AUTHZ**, jamais infirmées ensuite.

| Id | Décision | Source |
|---|---|---|
| **D-EXPORT = A** | `GET /api/reports/sla.csv` : mêmes filtres et authz que le snapshot ; `CsvWriter` anti-formule ; **agrégats seulement** (pas de champs dossier — l'export métier reste `GET /api/dossiers/export.csv`). | Conséquence **D-APPROACH = A+B** |
| **D-TASK-SLA = A** | Champ calculé `slaRemainingHours` (nullable) sur `GET /api/workflow-tasks/mine` si le type a une cible. | Conséquence **D-APPROACH = A+B** + **D-SLA-CLOCK** |
| **D-NAV = A** | Lien Pilotage « Rapports & SLA » pour TA / WA / DM. Masqué pour `DOSSIER_VIEWER`. E2E-9 : plus d'assertion « zéro Rapports ». | Conséquence **D-AUTHZ = C** |
| **D-SCOPE** | Écran maquette + summary + statut SLA + cible par type + objectif % + inbox. | Conséquence **D-APPROACH = A+B** |

Conséquences toujours valides :

- CSV **dans** V1 malgré le hors-périmètre analyse.
- Inbox `slaRemainingHours` **dans** V1 — uniquement si le type a une cible.
- Statut ternaire SLA **dans** V1 avec seuil 80 % figé.

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

1. **Cible SLA par type** : `DossierType.slaTargetHours` (`Integer`
   nullable, `> 0` si présent) éditable dans l'admin types
   (`TENANT_ADMIN`) — **D-SLA-HOURS = B**, pas de défaut tenant heures.
2. **Objectif de conformité tenant** : settings `complianceTargetPercent`
   (admin types, bloc voisin de la rétention).
3. **Snapshot rapport** `GET /api/reports/sla?from=&to=&dossierTypeCode=`
   aligné maquette :
   - Conformité SLA (%) + objectif + count hors délai
   - Délai moyen (jours, 1 décimale) + médiane + delta vs période
     précédente de même durée
   - Répartition volume par type (parts %)
   - Temps moyen par **nom d'activité userTask** Flowable (heures)
   - Charge par gestionnaire : en cours, hors délai, SLA % ; ligne
     **Non assignés**
   - Tableau par type : workflow, volume, délai moyen, SLA respecté,
     documents `PRODUCED_DOC`, tendance volume vs période précédente
4. **Export CSV** du tableau types + ligne KPI.
5. **SPA** `/rapports` : période, filtres type, cartes, tableaux, export,
   bouton planifier disabled. Barres CSS (**D-CHARTS**). Route existante
   maquette, pas `/admin/reporting`.
6. **Inbox** : badge reste SLA quand la cible existe.
7. **API summary** `GET /api/reports/sla/summary` : agrégats + statut
   `ON_TRACK` / `AT_RISK` / `BREACHED` selon **D-AT-RISK = A** (80 %) ;
   `groupBy` **type ou statut** seulement (**D-GROUPBY = B**).
8. **Qualité** : TDD ; `./mvnw verify` ; Vitest **et** Playwright
   (contrat flowforge-conventions FE).

### Secondary goals

- OpenAPI golden régénéré (nouveaux chemins).
- `GUIDE-UTILISATEUR.md` / nav : écran Rapports.
- ArchUnit : Flowable / JPA hors `domain.reporting` (ports only).

### Non-goals (actés)

- Envoi planifié / cron mail du rapport (**D-SCHEDULE = C**).
- Chart.js, Grafana, Alertmanager, time-series persistée (**D-CHARTS = A**).
- SLA **par étape** (cibles) — le temps/étape est **observationnel**.
- Recalcul matérialisé / job nuit / cache TTL (**D-COMPUTE = A**).
- Champs dossier dans l'export rapport (l'export métier reste
  `GET /api/dossiers/export.csv`).
- Changer Observabilité / Micrometer (pas de métrique Prometheus SLA
  métier V1 — A-NO-PROM).
- Connecteur `http-rest` SERVICE ; codegen OpenAPI.
- Pixel-perfect maquette (tokens existants, pas nouvelle lib UI).
- Cible SLA au niveau tâche Flowable (`dueDate` moteur) — on dérive
  du dossier.
- Seaux `groupBy` jour / semaine / mois (**D-GROUPBY = B**).

## Alternatives considérées (approche globale)

| Id | Approche | Trade-off | Verdict |
|---|---|---|---|
| A | API summary analyse + statut `ON_TRACK`/`AT_RISK`/`BREACHED` + UI sobre | Socle MCP ; s'écarte de la maquette | **Retenu avec B** |
| B | Snapshot maquette `/rapports` (KPI, CSV, inbox) | Parcours utilisateur FILE-ATTENTE | **Retenu avec A** |
| C | Hybride maquette sans CSV/inbox | Moins de surface, 2ᵉ feature ensuite | Écarté |
| Grafana | Brancher la SPA sur Grafana / Prometheus | SLA métier ≠ SLI ops | Écarté |

| Décision | Options | Retenu |
|---|---|---|
| Horloge SLA | A created→closed/now / B closedAt only / C Flowable dueDate | **A** |
| `slaTargetHours` | A défaut tenant + NEVER / B par type optionnel (exclu %) / C obligatoire chaque type | **B** |
| Période | A createdAt range / B closedAt range / C recouvrement | **A** |
| Authz lecture | A TA only / B TA+WA / C TA+WA+DM(ACL **type** VIEW) | **C** |
| Seuil `AT_RISK` | A fixe 80 % / B paramétrable tenant / C pas de AT_RISK V1 | **A** |
| `groupBy` | A jour/semaine/mois / B type\|statut seulement / C mois seul | **B** |
| Graphiques | A CSS / B Chart.js / C images serveur | **A** |
| Envoi planifié | A V1 mail / B hors scope hidden / C bouton disabled | **C** |
| Inbox SLA | A remainingHours / B hors V1 / C dueDate Flowable | **A** |
| Objectif conformité | A settings 1–100 défaut 95 / B pas d'objectif / C fixe 95 % | **A** |
| Calcul agrégats | A à la volée / B table de faits / C cache TTL | **A** |
| Formule conformité | A non-BREACHED / avec cible (AT_RISK OK) / B ON_TRACK only / C clos only | **A** (A-COMPLIANCE) |

## User Stories & Use Cases

1. **En tant que** `TENANT_ADMIN`, **je veux** saisir un délai cible
   (heures) **optionnel** par type de dossier **afin que** le rapport
   sache ce qu'est « hors délai », sans forcer un SLA sur tous les types.
2. **En tant que** `TENANT_ADMIN`, **je veux** un objectif de
   conformité (ex. 95 %) **afin que** la carte KPI compare le réel
   à la cible (maquette).
3. **En tant que** superviseur (`TA`/`WA`), **je veux** ouvrir
   Rapports & SLA sur une période **afin de** voir conformité, délai
   moyen, répartition, étapes lentes, charge par gestionnaire.
4. **En tant que** `DOSSIER_MANAGER`, **je veux** le même écran **limité
   aux types dont j'ai l'ACL VIEW** **afin de** ne pas voir le reste
   du tenant.
5. **En tant que** superviseur, **je veux** exporter le tableau en CSV
   **afin de** le coller dans un tableur (formules neutralisées).
6. **En tant que** gestionnaire, **je veux** voir le reste SLA sur
   « Mes tâches » **afin de** prioriser (maquette mobile).
7. **En tant que** `DOSSIER_VIEWER`, **je ne dois pas** voir le lien ni
   l'API rapport.
8. **En tant que** `WORKFLOW_ADMIN`, **je peux** lire Rapports et
   summary **sans** pouvoir PUT settings ni PATCH `slaTargetHours`.
9. **En tant que** `TENANT_ADMIN`, **je veux** **retirer** une cible
   SLA d'un type (**A-CLEAR-HOURS**) **afin de** l'exclure à nouveau
   du %.

### Edge cases

- Aucun dossier dans la période : KPI  « n/a » / 0, pas de division
  par zéro (A-EMPTY).
- Tous les types sans cible : conformité `null`, count hors délai 0.
- Conformité (**A-COMPLIANCE**) : dénominateur = dossiers de la cohorte
  dont le type a une cible ; numérateur = ceux qui ne sont **pas**
  `BREACHED` (`AT_RISK` compte comme conforme).
- Dossier ouvert à exactement 80 % du délai : `AT_RISK` ; à exactement
  100 % : encore `AT_RISK` ; au-delà : `BREACHED` (**D-AT-RISK**).
- Dossier clos : jamais `AT_RISK` (respecté ou dépassé seulement).
- Tous les statuts avec `createdAt` dans la fenêtre (**A-COHORT-STATUS**),
  y compris `DRAFT` / `SUSPENDU` ; l'horloge **ne pause pas**.
- `ARCHIVE` : traité comme clos (`closedAt` si présent, sinon `updatedAt`
  — **A-ARCHIVE-CLOCK**).
- Période `from >= to` ou > 366 j : **400**.
- Cohorte > 10 000 (après filtre ACL) : **400** `SLA_COHORT_TOO_LARGE`.
- `dossierTypeCode` inconnu ou hors ACL : snapshot / summary **vides**
  (comme search), pas 404.
- `groupBy` omis sur le summary : défaut `dossierTypeCode`
  (**A-GROUPBY-DEFAULT**) ; `day`/`week`/`month` → 400.
- Activité Flowable sans `endTime` : exclue de la moyenne d'étape.
- Gestionnaire = `assignee` de la userTask **courante** (dossiers non
  clos). Clos : non comptés dans « En cours ».
- Documents produits : count `element_type = PRODUCED_DOC` dont le
  dossier est dans la cohorte période.

## Success Criteria

- [ ] `slaTargetHours` persisté, validé `> 0` ou null, exposé
      POST/PATCH/GET types ; TA peut **effacer** la cible (**A-CLEAR-HOURS**).
- [ ] `TenantSlaSettings` GET 404 si absent ; PUT upsert **TA** ; 403 WA/DM/viewer ;
      défaut calcul 95.
- [ ] `GET /api/reports/sla` 200 JSON maquette-compatible (champs
      documentés en design) ; 403 viewer/service ; 400 période ou
      cohorte > 10 000 ; DM filtré types VIEW (vide = 200 vide, pas 403).
- [ ] CSV export 200, `CsvWriter`, pas de champs métier ; même authz.
- [ ] SPA `/rapports` réelle (plus de masquage D-MOCKGAP) ; barres CSS ;
      bouton planifier visible disabled.
- [ ] Inbox `slaRemainingHours` null si pas de cible.
- [ ] `GET /api/reports/sla/summary` expose `ON_TRACK` / `AT_RISK` /
      `BREACHED` (80 % fixe, clos sans `AT_RISK`) ; statut `null` sans cible ;
      `groupBy` `dossierTypeCode` / `status` (défaut type ; 400 si
      `day`/`week`/`month`) ; même authz que le snapshot.
- [ ] Conformité = non-`BREACHED` / dossiers avec cible (**A-COMPLIANCE**).
- [ ] `./mvnw verify` vert + Jacoco seuils tenus.
- [ ] Vitest + Playwright verts ; E2E-9 actualisé ; nouveaux E2E-SLA-*.
- [ ] OpenAPI golden à jour.
- [ ] FILE-ATTENTE / DEFERRED : Rapports & SLA plus « hors V1 » une
      fois la feature mergée (cette PR prépare le texte « en revue »).

## Constraints & Assumptions

### Constraints

- Hexagone : domaine pur ; JPA / Flowable dans infrastructure.
- Multi-tenant décision n° 13 : `app.tenant.id`, schéma dédié.
- BFF session ; pas de JWT browser. `API_SERVICE` n'accède pas aux
  rapports V1 (le MCP aura sa propre story d'authz plus tard).
- `CsvWriter` anti-formule obligatoire (précédent §23).
- FE : Vue 3 Composition API + TypeScript + PrimeVue patterns existants.
- Vitest **et** Playwright obligatoires.
- Séquencement FILE-ATTENTE : livrer **avant** `serveur-mcp`.
- Perf : calcul à la volée borné par 366 j et cap 10k (**D-COMPUTE**,
  **D-PERIOD**).
- Pas de feature flag : activé au merge (migration Liquibase schéma
  tenant).
- Branche Cloud `cursor/…-9009` (lint worktree `feature-*` = MISS
  attendu). Task tracing `ai-devkit task` indisponible.

### Assumptions (A-*)

| Id | Hypothèse |
|---|---|
| **A-EMPTY** | Division par zéro → `compliancePercent = null`, moyennes `null`, volumes 0. |
| **A-ARCHIVE-CLOCK** | Confirmé avec **D-SLA-CLOCK = A** : `ARCHIVE` sans `closedAt` : fin = `updatedAt`. |
| **A-NO-PROM** | Pas de jauge Micrometer SLA (observabilité reste ops). |
| **A-STEP-NAME** | Clé d'étape = `activityName` Flowable, fallback `activityId`. |
| **A-ASSIGNEE** | Charge = assignee de la userTask active du lien courant ; pas un champ dossier. |
| **A-TREND** | Tendance = `(volume_courant - volume_précédent) / volume_précédent` ; « stable » si abs(delta) < 0,5 % ou précédent = 0 et courant = 0. |
| **A-MEDIAN** | Médiane des durées (heures) convertie en jours (1 décimale), cohorte période. |
| **A-DELTA-MEAN** | `meanDays - previousMeanDays` (négatif = plus rapide, maquette « −0,8 j »). |
| **A-HOURS** | `slaTargetHours` en heures entières (maquette mixte j / h ; une unité côté API). Affichage UI : jours si ≥ 24 h. |
| **A-DEFAULT-PERIOD** | Confirmé avec **D-PERIOD = A** : si `from`/`to` omis : `[now-30d, now]` UTC. |
| **A-TASK-REMAINING** | `slaRemainingHours = slaTargetHours - elapsedHours(createdAt, now)` ; négatif si dépassé. |
| **A-AT-RISK-CMP** | Comparaison anti-flottant : `AT_RISK` ssi `elapsedHours * 5 >= slaTargetHours * 4` **et** `elapsedHours <= slaTargetHours` (dossier ouvert, cible présente). |
| **A-COHORT-CAP** | Confirmé avec **D-COMPUTE = A** : 400 `SLA_COHORT_TOO_LARGE` si plus de 10 000 dossiers dans `[from, to]` (après filtre ACL). |
| **A-INDEX** | Index `(created_at)` sur `dossier` (schéma tenant) pour le filtre **D-PERIOD**. Pas d'index composite V1 tant que les plans d'exécution ne le justifient pas. |
| **A-COMPLIANCE** | `compliancePercent` = 100 × (dossiers avec cible et statut ≠ `BREACHED`) / (dossiers avec cible). `AT_RISK` est conforme. Types sans cible exclus. |
| **A-COHORT-STATUS** | Aucun filtre de statut métier : tout `createdAt ∈ [from, to]`. `SUSPENDU` / `DRAFT` : horloge continue. |
| **A-GROUPBY-DEFAULT** | Summary : `groupBy` omis → `dossierTypeCode`. |
| **A-CLEAR-HOURS** | TA peut remettre `slaTargetHours` à « pas de SLA » (null). Le contrat PATCH (JsonNullable vs sentinelle) est **design** — le premier jet `null = keep` est **insuffisant** s'il n'offre pas d'autre chemin d'effacement. |
| **A-NO-FLAG** | Pas de flag runtime ; rollout = merge + migration. |

## Questions & Open Items

**Aucune question produit ouverte.** Phase 1 tranchée ; Phase 2 a nommé
les A-* ci-dessus et corrigé le libellé ACL (par **type**).

### Clos Phase 2 (2026-08-26)

1. ~~Forme produit~~ **D-APPROACH = A+B**.
2. ~~RBAC~~ **D-AUTHZ = C** (ACL **type** VIEW, comme search).
3. ~~`slaTargetHours`~~ **D-SLA-HOURS = B**.
4. ~~`AT_RISK`~~ **D-AT-RISK = A**.
5. ~~`groupBy`~~ **D-GROUPBY = B**.
6. ~~Horloge~~ **D-SLA-CLOCK = A**.
7. ~~Période~~ **D-PERIOD = A**.
8. ~~Objectif conformité~~ **D-SETTINGS = A**.
9. ~~Calcul~~ **D-COMPUTE = A**.
10. ~~Graphiques~~ **D-CHARTS = A**.
11. ~~Envoi planifié~~ **D-SCHEDULE = C**.
12. ~~Export / inbox / nav~~ **D-EXPORT / D-TASK-SLA / D-NAV**.
13. ~~Formule % / cohorte statuts / clear hours / groupBy défaut~~ **A-COMPLIANCE**, **A-COHORT-STATUS**, **A-CLEAR-HOURS**, **A-GROUPBY-DEFAULT**.

### Reporté hors feature (pas bloquant)

- Seaux `groupBy` jour / semaine / mois.
- Mail / cron « Planifier l'envoi ».
- Chart.js ; table de faits ; `API_SERVICE` / MCP authz.
- Alerte proactive de dépassement (notifications).

### À traiter en Phase 3 (design), pas ici

- Réaligner design/planning/testing du premier jet sur ces D-*.
- Contrat PATCH pour **A-CLEAR-HOURS**.
- Shape JSON exact du snapshot et du summary.
