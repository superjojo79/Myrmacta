---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: in-progress
created: 2026-08-26
updated: 2026-08-26
feature: reporting-sla
phase1: 2026-08-26
todo_ref: DEFERRED-FEATURES.md §1.6 · FILE-ATTENTE.md · analysis doc/16-reporting-sla.md · maquette UI
---

# Requirements & Problem Understanding — `reporting-sla`

## Phase 1 : New Requirement (relancée)

Phase 1 **relancée**. Actés : **D-APPROACH = A+B**, **D-AUTHZ = C**,
**D-SLA-HOURS = B**, **D-AT-RISK = A**. Les autres D-* restent des
propositions tant qu'ils n'ont pas été tranchés ici, une décision à
la fois.

Workspace : branche `cursor/reporting-sla-9009`, no-worktree (VM cloud).
Task tracing indisponible (`unknown command 'task'`). Lint feature :
docs OK ; le check de branche `feature-reporting-sla` échoue (contrainte
cloud `cursor/…-9009`).

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
| **D-AUTHZ = C** | Lecture snapshot **et** summary : `TENANT_ADMIN` / `WORKFLOW_ADMIN` tout le tenant ; `DOSSIER_MANAGER` filtré ACL VIEW (même split que `SearchDossiersUseCase`) ; `DOSSIER_VIEWER` / `API_SERVICE` → 403. Écriture `slaTargetHours` + settings : **`TENANT_ADMIN` uniquement**. | Stakeholder 2026-08-26 |
| **D-SLA-HOURS = B** | Cible **uniquement par type**, **optionnelle**. Pas de défaut tenant heures, pas de sentinelle `NEVER` (contrairement à la rétention, décision n° 21). `null` = pas de SLA pour ce type : **exclu** du % de conformité (cellule « — »). Validation `> 0` si présent. | Stakeholder 2026-08-26 |
| **D-AT-RISK = A** | Convention **fixe 80 %** du délai. Dossier **ouvert** avec cible : `ON_TRACK` si `elapsed < 0,8 × slaTargetHours` ; `AT_RISK` si `0,8 × slaTargetHours ≤ elapsed ≤ slaTargetHours` ; `BREACHED` si `elapsed > slaTargetHours`. Dossier **clos** : seulement `ON_TRACK` / `BREACHED` (pas d'`AT_RISK`). Sans cible : statut `null`. Pas de champ `atRiskPercent` tenant. | Stakeholder 2026-08-26 |

Conséquences immédiates (tant que non démenties par une question suivante) :

- CSV **dans** V1 (B) malgré le hors-périmètre analyse.
- Inbox `slaRemainingHours` **dans** V1 (B) — uniquement si le type a une cible.
- Statut ternaire SLA **dans** V1 (A) avec seuil 80 % figé (**D-AT-RISK**).
- `groupBy` période (jour/semaine/mois) reste à trancher (question analyse n° 4).
- `TenantSlaSettings` ne porte **pas** un `slaTargetHours` défaut ni un `atRiskPercent` (l'objectif % conformité, D-SETTINGS, est une autre question).

### Propositions (non verrouillées)

À confirmer ou remplacer, une question à la fois. Le code existant
suit surtout **B** ; A (summary + `AT_RISK`) n'y est pas encore.

| Id | Proposition |
|---|---|
| **D-SCOPE** | Écran maquette + API summary + statut SLA + cible par type + objectif % tenant + inbox (conséquence de A+B). |
| **D-SLA-CLOCK = A** | Horloge dossier : `createdAt` → `closedAt` si clos (`CLOTURE`/`ARCHIVE`), sinon `now`. Hors délai ssi durée > `slaTargetHours`. |
| **D-PERIOD = A** | Filtre `createdAt ∈ [from, to]` ; défaut **30 jours** glissants UTC ; max **366 jours** ; `to` exclusif minuit jour suivant si date civile. |
| **D-CHARTS = A** | Barres CSS / tableaux PrimeVue-alignés maquette. **Pas** Chart.js (cohérent observabilité Q4). |
| **D-EXPORT = A** | `GET /api/reports/sla.csv` mêmes filtres/authz ; `CsvWriter` anti-formule. Agrégats seulement — **pas** de champs dossier. |
| **D-SCHEDULE = C** | Bouton « Planifier l'envoi » **visible désactivé** (« bientôt ») — envoi planifié hors scope. |
| **D-TASK-SLA = A** | Champ calculé `slaRemainingHours` (nullable) sur `GET /api/workflow-tasks/mine` quand le type du dossier a une cible. |
| **D-SETTINGS = A** | `TenantSlaSettings.complianceTargetPercent` uniquement (1–100, défaut **95** si GET 404). **Pas** de `slaTargetHours` tenant (**D-SLA-HOURS = B**) ni de `atRiskPercent` (**D-AT-RISK = A**). Singleton par schéma. |
| **D-COMPUTE = A** | Agrégation **à la volée** (SQL + Flowable history). Pas de table snapshot matérialisée V1. |
| **D-NAV = A** | Lien Pilotage « Rapports & SLA » pour TA / WA / DM. Masqué pour `DOSSIER_VIEWER`. E2E-9 mis à jour (plus « zéro Rapports »). |
| **D-INDEX = A** | Index lecture `(created_at)` sur `dossier` pour le filtre période. |

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
   bouton planifier disabled.
6. **Inbox** : badge reste SLA quand la cible existe.
7. **API summary** `GET /api/reports/sla/summary` : agrégats + statut
   `ON_TRACK` / `AT_RISK` / `BREACHED` selon **D-AT-RISK = A** (80 %).
8. **Qualité** : TDD ; `./mvnw verify` ; Vitest **et** Playwright
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
| A | API summary analyse + statut `ON_TRACK`/`AT_RISK`/`BREACHED` + UI sobre | Socle MCP ; s'écarte de la maquette | **Retenu avec B** |
| B | Snapshot maquette `/rapports` (KPI, CSV, inbox) | Parcours utilisateur FILE-ATTENTE | **Retenu avec A** |
| C | Hybride maquette sans CSV/inbox | Moins de surface, 2ᵉ feature ensuite | Écarté |
| Grafana | Brancher la SPA sur Grafana / Prometheus | SLA métier ≠ SLI ops | Écarté |

| Décision | Options | Retenu |
|---|---|---|
| Horloge SLA | A created→closed/now / B closedAt only / C Flowable dueDate | **A** |
| `slaTargetHours` | A défaut tenant + NEVER / B par type optionnel (exclu %) / C obligatoire chaque type | **B** |
| Période | A createdAt range / B closedAt range / C updatedAt | **A** |
| Authz lecture | A TA only / B TA+WA / C TA+WA+DM(ACL) | **C** |
| Seuil `AT_RISK` | A fixe 80 % / B paramétrable tenant / C pas de AT_RISK V1 | **A** |
| Graphiques | A CSS / B Chart.js / C images serveur | **A** |
| Envoi planifié | A V1 mail / B hors scope hidden / C bouton disabled | **C** |
| Inbox SLA | A remainingHours / B hors V1 / C dueDate Flowable | **A** |

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

### Edge cases

- Aucun dossier dans la période : KPI  « n/a » / 0, pas de division
  par zéro (A-EMPTY).
- Tous les types sans cible : conformité `null`, count hors délai 0.
- Dossier ouvert à exactement 80 % du délai : `AT_RISK` ; à exactement
  100 % : encore `AT_RISK` ; au-delà : `BREACHED` (**D-AT-RISK**).
- Dossier clos : jamais `AT_RISK` (respecté ou dépassé seulement).
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
- [ ] `GET /api/reports/sla/summary` expose `ON_TRACK` / `AT_RISK` /
      `BREACHED` (80 % fixe, clos sans `AT_RISK`) ; statut `null` sans cible.
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
| **A-AT-RISK-CMP** | Comparaison anti-flottant : `AT_RISK` ssi `elapsedHours * 5 >= slaTargetHours * 4` **et** `elapsedHours <= slaTargetHours` (dossier ouvert, cible présente). |

## Questions & Open Items

Phase 1 en cours. Actés : **D-APPROACH = A+B**, **D-AUTHZ = C**,
**D-SLA-HOURS = B**, **D-AT-RISK = A**. Reste :

1. ~~Forme produit~~ **A+B**.
2. ~~RBAC exact~~ **C** (TA/WA unrestricted ; DM filtré ACL VIEW ; viewer / API_SERVICE 403 ; écriture TA only).
3. ~~`slaTargetHours` obligatoire ou optionnel~~ **B** (par type, nullable ; pas de défaut tenant heures ; type sans cible exclu du %).
4. ~~Seuil `AT_RISK`~~ **A** (convention fixe 80 % ; clos = ON_TRACK/BREACHED seulement).
5. **Granularité `groupBy`** (jour/semaine/mois) dès V1 ou uniquement
   type/statut. ← **en cours**
6. Autres D-* proposés (horloge, période, charts, schedule, settings)
   une fois 5 tranché.
