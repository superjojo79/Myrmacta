---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
created: 2026-09-21
updated: 2026-09-21
feature: enrichissement-reporting
phase1: 2026-09-21
phase2: 2026-09-21
stakeholder_decisions: 2026-09-21
todo_ref: TODO.md §65 · analysis doc/29-enrichissement-reporting.md
---

# Requirements & Problem Understanding — `enrichissement-reporting`

## Phase 2: Review requirements

Revue 2026-09-21. Quatorze décisions produit tranchées une par une en Phase 1.
Aucune question produit ouverte. Les hypothèses **A-*** ci-dessous alignent le
cycle sur le code existant ; ce ne sont pas des **D-***.

Template README respecté (problem, goals/non-goals, stories, success,
constraints, questions). Le lot C (ClamAV) reste hors surface : pas de
`AttachmentScanPort`, pas de `docker-compose`, pas de `GETTING-STARTED.md`.

## Problem Statement

**What problem are we solving?**

Le socle `reporting-sla` (PR #80) calcule déjà `ON_TRACK` / `AT_RISK` /
`BREACHED` et les affiche sur `/rapports`, avec un export CSV manuel des
dossiers et du rapport SLA. Rien ne prévient quand un dossier franchit un
seuil, l’écran ne montre pas les process en erreur ni les tâches sans
assigné, et il n’existe pas d’export planifié réutilisable par un outil
externe.

Qui est touché : `TENANT_ADMIN` (réglages), `WORKFLOW_ADMIN` et
`DOSSIER_MANAGER` (lecture du rapport), les participants d’un dossier
(notification navigateur), les destinataires e-mail et webhooks choisis
par l’admin.

Contournement actuel : consulter `/rapports` et relancer les exports CSV
à la main.

Source : [29-enrichissement-reporting.md](../../../analysis%20doc/29-enrichissement-reporting.md).

## Goals & Objectives

**What do we want to achieve?**

- Un cycle unique livre les trois écarts du document 29 : alerting SLA,
  deux widgets opérationnels, export BI planifié.
- L’alerting réutilise `SendNotificationUseCase` (EMAIL, WEBHOOK, BROWSER)
  et les statuts SLA déjà calculés.
- L’export BI réutilise le masquage et les colonnes des exports manuels,
  et `DocumentStoragePort` (pas de vue JDBC).

### Non-goals

- Adaptateur ClamAV, Compose, en-têtes HTTP (lots C et A4).
- Vue JDBC / Power BI direct, Chart.js, Redis, inbox de notifications
  persistée, portail externe, signature, outillage DMN.
- Second seuil SLA (le ratio 80 % de `SlaStatus` ne change pas).
- Rattrapage : à l’activation de l’alerting, l’état courant est mémorisé
  sans envoi.
- Digest : une alerte = un dossier × un franchissement, pas un résumé.

## User Stories & Use Cases

**How will users interact with the solution?**

- En tant que `TENANT_ADMIN`, j’active l’alerting dans les réglages SLA
  pour qu’un franchissement ultérieur parte en e-mail, webhook et
  notification navigateur, sans rejouer le passé.
- En tant que `TENANT_ADMIN`, je saisis une liste d’e-mails et une
  sélection de webhooks activés du tenant, et je règle l’intervalle de
  scan (défaut 1 h, minimum 15 min).
- En tant que participant (démarreur, assigné, observateur), je vois une
  notification navigateur si je suis connecté au SSE au moment de
  l’envoi.
- En tant que lecteur de `/rapports`, je vois le nombre de jobs Flowable
  en dead-letter et le nombre de tâches humaines ouvertes sans assigné,
  que l’alerting soit actif ou non.
- En tant que `TENANT_ADMIN`, j’active l’export BI avec un intervalle
  (défaut 24 h après l’activation ou le dernier succès) et une rétention
  en jours (défaut 30, bornes 1–365).
- En tant qu’acteur autorisé, je liste sur `/rapports` les fichiers encore
  dans la rétention et je télécharge ceux que mon droit `VIEW` (CSV de
  type) ou mon rôle rapport (CSV SLA) m’autorise.

### Edge cases

- Pas de `slaTargetHours` : dossier non scoré, pas d’alerte.
- Dossier clos : jamais `AT_RISK` (règle actuelle) ; un clos déjà en
  retard reste `BREACHED`.
- Fenêtre `AT_RISK` manquée entre deux scans : seule l’entrée observée
  (`BREACHED`) est signalée.
- Retour à `ON_TRACK` puis nouveau franchissement : nouvelle alerte.
- Liste d’e-mails vide ou webhook désactivé au moment de l’envoi : ce
  canal est sauté ; BROWSER part quand même vers les participants si
  l’alerting est actif.
- Participant hors ligne : le franchissement est consommé, pas de nouvel
  essai SSE (pas d’inbox persistée dans ce cycle).
- Échec d’un export : `last_success` n’avance pas ; les objets storage
  de la tentative sont supprimés au mieux.

## Success Criteria

**How will we know when we're done?**

- Alerting inactif par défaut. À l’activation, snapshot sans envoi.
  Ensuite, au plus une alerte par dossier et par entrée en `AT_RISK`,
  et une par entrée en `BREACHED`.
- Les trois canaux passent par `SendNotificationUseCase`. Le payload
  navigateur a `title` et `body` pour le tiroir SSE existant, sans
  valeur de champ sensible.
- `GET /api/reports/operations` (rôles du rapport SLA) retourne les deux
  compteurs.
- Export inactif par défaut. Une exécution due écrit un CSV par type de
  dossier (colonnes de l’export manuel de ce type, champs sensibles
  masqués) et un CSV SLA sans filtre (période par défaut de 30 jours,
  `SlaPeriod`).
- `GET /api/reports/bi-exports` ne liste que les fichiers que l’acteur
  peut télécharger. Le téléchargement d’un CSV de type réapplique
  `VIEW`. Le CSV SLA exige un rôle du rapport.
- Les fichiers plus vieux que la rétention sont supprimés du storage et
  de la table, y compris quand l’export est ensuite désactivé.
- `./mvnw verify` vert. Vitest et Playwright verts, y compris les
  parcours réglages admin, widgets et liste BI.
- `GUIDE-UTILISATEUR.md` décrit les nouveaux champs admin et la liste
  sur `/rapports`.

## Constraints & Assumptions

**What limitations do we need to work within?**

- Une instance = un tenant. Jobs `@Scheduled` dans le process, pas de
  file distribuée.
- `DocumentStoragePort` ne liste pas les objets : l’historique BI est
  une table applicative qui pointe vers un `storageRef` opaque.
- L’export dossiers manuel est **par type** (colonnes dynamiques). Le
  job suit cette forme.
- Réglages : extension de `GET`/`PUT /api/tenant/sla-settings`, rôle
  `TENANT_ADMIN`, écran admin des types (formulaire SLA existant).
- Pas de lookup Keycloak pour les e-mails.
- Hypothèses nommées : **A-POLL** (tick d’une minute, l’échéance se
  décide dans le use case), **A-ALERT-INTERVAL-MAX** (7 jours),
  **A-BI-INTERVAL** (minimum 1 h, maximum 720 h), **A-EMAIL-CAP** et
  **A-WEBHOOK-CAP** (20), **A-MISSED-WINDOW**, **A-BROWSER-NO-RETRY**,
  **A-SNAPSHOT-ALL-SCORED** (y compris `ON_TRACK`, pour ne pas
  rattraper), **A-SLA-CSV-PERIOD** (défaut 30 jours, tous types),
  **A-TWO-TOGGLES** (alerting et BI indépendants), **A-NO-DIGEST**,
  **A-ALERT-POPULATION** (tous les dossiers scorés, pas la fenêtre du
  rapport), **A-BI-MASK-ALWAYS** (le CSV stocké retire les clés
  sensibles pour tout téléchargeur ; le clair reste l’export manuel
  admin).

## Questions & Open Items

**What do we still need to clarify?**

Toutes tranchées le 2026-09-21 :

| ID | Décision |
|---|---|
| D-CYCLE-SCOPE | Les trois volets dans ce cycle |
| D-ALERT-CHANNEL | EMAIL + WEBHOOK + BROWSER via `SendNotificationUseCase` |
| D-ALERT-RECIPIENTS | E-mails et webhooks choisis dans les réglages ; BROWSER vers les participants du dossier |
| D-ALERT-THRESHOLD | Statuts existants uniquement (`slaTargetHours` par type, ratio 80 % inchangé) |
| D-ALERT-DEDUP | Une alerte par franchissement et par dossier |
| D-DASHBOARD-WIDGETS | Dead-letter Flowable + tâches ouvertes sans assigné |
| D-BI-MECHANISM | Job vers `DocumentStoragePort`, pas de JDBC |
| D-BI-SCOPE | CSV dossiers et CSV SLA, masquage des exports manuels |
| D-BI-DELIVERY | Fichiers horodatés, rétention configurable, liste sur `/rapports` |
| D-ALERT-ACTIVATION | Opt-in ; snapshot sans envoi |
| D-BI-ACTIVATION | Opt-in ; intervalle et rétention requis |
| D-CADENCE | Défauts : alerte 1 h (min 15 min), BI 24 h, rétention 30 jours (1–365) |
| D-BI-SCHEDULE | Intervalle depuis l’activation ou le dernier succès, pas une heure civile |
| D-BI-DOSSIER-SHAPE | Un CSV par type + un CSV SLA ; `VIEW` sur le CSV de type ; rôles rapport sur le CSV SLA ; liste filtrée |

Aucune question produit restante.
