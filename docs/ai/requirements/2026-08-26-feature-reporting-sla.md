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
stakeholder_decisions: 2026-08-26
todo_ref: DEFERRED-FEATURES.md §1.6 · FILE-ATTENTE.md hors V1 · maquette UI · D-MOCKGAP
---

# Requirements & Problem Understanding — `reporting-sla`

## Phase 2: Review requirements

Revue 2026-08-26. Feature extraite du hors-V1 (FILE-ATTENTE / DEFERRED
§1.6) **sur demande explicite** de lancer le lifecycle — le lot
`durcissement-securite-post-revue` (PR #76) reste en revue humaine et
n'est pas mergé par cet agent.

D-* tranchés en Phase 1/2 par **recommandations cloud** (pas
d'`AskUserQuestion` dans cet environnement). Un humain peut relocker
n'importe quel D-* avant merge. Aucune question ouverte matérielle :
les hypothèses restantes sont **nommées** (A-*).

Task tracing : `npx ai-devkit@latest task` = `unknown command 'task'`
— continué sans traçage. Agent list vide. Mode **no-worktree** (VM
cloud isolée ; branche PR `cursor/reporting-sla-9009`).

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

### Phase 1 / 2 (2026-08-26) — recommandations cloud

| Id | Décision |
|---|---|
| **D-APPROACH = B** | Bounded context domaine `reporting` + snapshot JSON BFF + SPA Pilotage. Pas Grafana, pas Chart.js, pas de recopie des KPI observabilité. |
| **D-SCOPE = A** | Livrer l'écran maquette (KPI + répartition + temps/étape + charge gestionnaires + tableau types + export CSV) + cible SLA par type + objectif % tenant + reste SLA sur l'inbox. |
| **D-SLA-CLOCK = A** | Horloge dossier : `createdAt` → `closedAt` si clos (`CLOTURE`/`ARCHIVE`), sinon `now`. Hors délai ssi durée > `slaTargetHours`. |
| **D-SLA-NULL = A** | Type sans `slaTargetHours` : **exclu** du % de conformité (cellule « — », comme « Non assignés » dans la maquette). |
| **D-PERIOD = A** | Filtre `createdAt ∈ [from, to]` ; défaut **30 jours** glissants UTC ; max **366 jours** ; `to` exclusif minuit jour suivant si date civile. |
| **D-AUTHZ = A** | Lecture rapport : `TENANT_ADMIN` / `WORKFLOW_ADMIN` tout le tenant ; `DOSSIER_MANAGER` filtré ACL VIEW (même règle que `SearchDossiersUseCase`) ; `DOSSIER_VIEWER` / `API_SERVICE` → 403. Écriture settings + `slaTargetHours` : **`TENANT_ADMIN` uniquement**. |
| **D-CHARTS = A** | Barres CSS / tableaux PrimeVue-alignés maquette. **Pas** Chart.js (cohérent observabilité Q4). |
| **D-EXPORT = A** | `GET /api/reports/sla.csv` mêmes filtres/authz ; `CsvWriter` anti-formule. Agrégats seulement — **pas** de champs dossier. |
| **D-SCHEDULE = C** | Bouton « Planifier l'envoi » **visible désactivé** (« bientôt ») — envoi planifié hors scope. |
| **D-TASK-SLA = A** | Champ calculé `slaRemainingHours` (nullable) sur `GET /api/workflow-tasks/mine` quand le type du dossier a une cible. |
| **D-SETTINGS = A** | `TenantSlaSettings.complianceTargetPercent` (1–100, défaut **95** si GET 404 / non configuré côté calcul). Singleton par schéma, miroir rétention. |
| **D-COMPUTE = A** | Agrégation **à la volée** (SQL + Flowable history). Pas de table snapshot matérialisée V1. |
| **D-NAV = A** | Lien Pilotage « Rapports & SLA » pour TA / WA / DM. Masqué pour `DOSSIER_VIEWER`. E2E-9 mis à jour (plus « zéro Rapports »). |
| **D-INDEX = A** | Index lecture `(created_at)` sur `dossier` pour le filtre période. |

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

1. **Cible SLA par type** : `DossierType.slaTargetHours` (`Integer`
   nullable, `> 0` si présent) éditable dans l'admin types
   (`TENANT_ADMIN`).
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
   bouton planifier disabled.
6. **Inbox** : badge reste SLA quand la cible existe.
7. **Qualité** : TDD ; `./mvnw verify` ; Vitest **et** Playwright
   (contrat flowforge-conventions FE).

### Secondary goals

- OpenAPI golden régénéré (nouveaux chemins).
- `GUIDE-UTILISATEUR.md` / nav : écran Rapports.
- ArchUnit : Flowable / JPA hors `domain.reporting` (ports only).

### Non-goals (actés)

- Envoi planifié / cron mail du rapport (**D-SCHEDULE = C**).
- Chart.js, Grafana, Alertmanager, time-series persistée.
- SLA **par étape** (cibles) — le temps/étape est **observationnel**.
- Recalcul matérialisé / job nuit.
- Champs dossier dans l'export rapport (l'export métier reste
  `GET /api/dossiers/export.csv`).
- Changer Observabilité / Micrometer (pas de métrique Prometheus SLA
  métier V1 — A-NO-PROM).
- Connecteur `http-rest` SERVICE ; codegen OpenAPI.
- Pixel-perfect maquette (tokens existants, pas nouvelle lib UI).
- Cible SLA au niveau tâche Flowable (`dueDate` moteur) — on dérive
  du dossier.

## Alternatives considérées (approche globale)

| Id | Approche | Trade-off | Verdict |
|---|---|---|---|
| A | Brancher la SPA sur Grafana / Prometheus | SLA métier ≠ SLI ops ; pas de Grafana-as-code V1 | Écarté |
| B | **BC domaine + snapshot BFF + SPA** | Calcul à la volée, un tenant = un schéma | **Retenu (D-APPROACH)** |
| C | Table snapshot + job | Plus d'infra, trop tôt sans volume | Écarté V1 (**D-COMPUTE**) |
| D | Mock data dans la SPA | Contredit D-MOCKGAP (on masquait justement les faux KPI) | Écarté |

| Décision | Options | Retenu |
|---|---|---|
| Horloge SLA | A created→closed/now / B closedAt only / C Flowable dueDate | **A** |
| Type sans cible | A exclu % / B héritage tenant heures / C 0 % | **A** |
| Période | A createdAt range / B closedAt range / C updatedAt | **A** |
| Authz lecture | A TA+WA+DM(ACL) / B TA only / C tous authentifiés | **A** |
| Graphiques | A CSS / B Chart.js / C images serveur | **A** |
| Envoi planifié | A V1 mail / B hors scope hidden / C bouton disabled | **C** |
| Inbox SLA | A remainingHours / B hors V1 / C dueDate Flowable | **A** |

## User Stories & Use Cases

1. **En tant que** `TENANT_ADMIN`, **je veux** saisir un délai cible
   (heures) par type de dossier **afin que** le rapport sache ce
   qu'est « hors délai ».
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

### Edge cases

- Aucun dossier dans la période : KPI  « n/a » / 0, pas de division
  par zéro (A-EMPTY).
- Tous les types sans cible : conformité `null`, count hors délai 0.
- Dossier `DRAFT`/`OUVERT`/`EN_COURS`/`SUSPENDU` : durée jusqu'`now`.
- `ARCHIVE` : traité comme clos (`closedAt` si présent, sinon `updatedAt`
  — **A-ARCHIVE-CLOCK**).
- Période `from >= to` ou > 366 j : **400**.
- `dossierTypeCode` inconnu ou hors ACL : snapshot vide (comme search),
  pas 404.
- Activité Flowable sans `endTime` : exclue de la moyenne d'étape.
- Gestionnaire = `assignee` de la userTask **courante** (dossiers non
  clos). Clos : non comptés dans « En cours ».
- Documents produits : count `element_type = PRODUCED_DOC` dont le
  dossier est dans la cohorte période.

## Success Criteria

- [ ] `slaTargetHours` persisté, validé `> 0` ou null, exposé
      POST/PATCH/GET types.
- [ ] `TenantSlaSettings` GET 404 si absent ; PUT upsert TA ; défaut
      calcul 95.
- [ ] `GET /api/reports/sla` 200 JSON maquette-compatible (champs
      documentés en design) ; 403 viewer/service ; 400 période.
- [ ] CSV export 200, `CsvWriter`, pas de champs métier.
- [ ] SPA `/rapports` réelle (plus de masquage D-MOCKGAP).
- [ ] Inbox `slaRemainingHours` null si pas de cible.
- [ ] `./mvnw verify` vert + Jacoco seuils tenus.
- [ ] Vitest + Playwright verts ; E2E-9 actualisé ; nouveaux E2E-SLA-*.
- [ ] OpenAPI golden à jour.
- [ ] FILE-ATTENTE / DEFERRED : Rapports & SLA plus « hors V1 » une
      fois la feature mergée (cette PR prépare le texte « en revue »).

## Constraints & Assumptions

### Constraints

- Hexagone : domaine pur ; JPA / Flowable dans infrastructure.
- Multi-tenant décision n° 13 : `app.tenant.id`, schéma dédié.
- BFF session ; pas de JWT browser.
- `CsvWriter` anti-formule obligatoire (précédent §23).
- FE : Vue 3 Composition API + TypeScript + PrimeVue patterns existants.
- Vitest **et** Playwright obligatoires.

### Assumptions (A-*)

| Id | Hypothèse |
|---|---|
| **A-EMPTY** | Division par zéro → `compliancePercent = null`, moyennes `null`, volumes 0. |
| **A-ARCHIVE-CLOCK** | `ARCHIVE` sans `closedAt` : fin = `updatedAt` (rétention archive sans clôture métier). |
| **A-NO-PROM** | Pas de jauge Micrometer SLA (observabilité reste ops). |
| **A-STEP-NAME** | Clé d'étape = `activityName` Flowable, fallback `activityId`. |
| **A-ASSIGNEE** | Charge = assignee de la userTask active du lien courant ; pas un champ dossier. |
| **A-TREND** | Tendance = `(volume_courant - volume_précédent) / volume_précédent` ; « stable » si abs(delta) < 0,5 % ou précédent = 0 et courant = 0. |
| **A-MEDIAN** | Médiane des durées (heures) convertie en jours (1 décimale), cohorte période. |
| **A-DELTA-MEAN** | `meanDays - previousMeanDays` (négatif = plus rapide, maquette « −0,8 j »). |
| **A-HOURS** | `slaTargetHours` en heures entières (maquette mixte j / h ; une unité côté API). Affichage UI : jours si ≥ 24 h. |
| **A-DEFAULT-PERIOD** | Si `from`/`to` omis : `[now-30d, now]`. |
| **A-TASK-REMAINING** | `slaRemainingHours = slaTargetHours - elapsedHours(createdAt, now)` ; négatif si dépassé. |

## Questions & Open Items

Aucune question matérielle ouverte. Relock humain possible sur D-*
avant merge. Suivi hors feature : envoi planifié (D-SCHEDULE).
