---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: draft
created: 2026-08-12
updated: 2026-08-12
feature: frontend
stakeholder_decisions: 2026-08-12
---

# Requirements & Problem Understanding

## Problem Statement
**What problem are we solving?**

FlowForge expose déjà les API métier (auth BFF, dossiers, workflows,
visualisation, documents, connecteurs, notifications SSE), mais **aucune
SPA** n’est livrée : `src/main/resources/static/` est vide (`.gitkeep`),
il n’y a pas de projet Vite, et les gestionnaires n’ont que des clients
HTTP bruts pour piloter les dossiers et tâches.

- **Affectés :** `DOSSIER_MANAGER` / `DOSSIER_VIEWER`, superviseurs
  (`WORKFLOW_ADMIN` / `TENANT_ADMIN`), opérateurs mobiles, admins
  de configuration tenant.
- **Situation actuelle :** backends Complete (dont `visualisation-workflow`) ;
  analyse [11-frontend.md](../../../analysis%20doc/11-frontend.md) ;
  maquette HTML de référence
  [FlowForge - Maquettes UI.html](../../../analysis%20doc/FlowForge%20-%20Maquettes%20UI.html) ;
  décision n° 27 (frontend embarqué `static/`, TypeScript, Vitest,
  Playwright, ESLint/Prettier).
- **Écart API :** liste/pagination dossiers et métriques SLA **non
  livrés** (TODO §24) — pas de faux KPI ni écran Rapports en V1
  (**D-MOCKGAP = A**).

## Stakeholder decisions (2026-08-12)

| Id | Décision |
|---|---|
| **D-SCOPE = A** | Socle SPA + parcours instructeur (dossier/viz/SSE/Supervision MVP) ; pas de maquette pixel-complete sur APIs absentes |
| **D-ME = A** | Étendre `AuthUserResponse` avec `id` = OIDC `sub` |
| **D-ACTIONS = C** | Créer dossier + démarrer workflow + complete + reassign |
| **D-ELEMENTS = B** | Liste + download ; bouton « Ajouter » visible **désactivé** (« bientôt ») |
| **D-ADMIN = C** + **D-ADMIN-RES = A** | Vague 2 : CRUD `dossier-types`(+ACL), `document-templates`, `connectors`(+definitions lecture), `workflow-type-configs` ; **pas** notification-webhooks UI |
| **D-SEQ = A** | Vague 1 instructeur → vague 2 admin |
| **D-PACK = A** | `frontend-maven-plugin` (Node pendant Maven) → `static/` ; bundles **non commités** |
| **D-E2E = A** | Playwright session mockée ; Keycloak réel hors CI |
| **D-SENSITIVE = A** | Masquer valeurs `sensitive` (`••••`) sauf `TENANT_ADMIN` |
| **D-MOCKGAP = A** | Masquer Rapports & SLA ; Supervision sans faux KPI |

## Goals & Objectives
**What do we want to achieve?**

### Primary goals — Vague 1 (instructeur)

- **Socle SPA** (**D-SCOPE**) : Vue 3 + Vite + TypeScript `strict` +
  PrimeVue + Pinia + Axios + `bpmn-js` Viewer sous `frontend/` ;
  packaging **D-PACK**.
- **Session BFF** : store `auth` ; `GET /api/auth/me` →
  `{ id, fullName, email, roles }` (**D-ME**) ; redirect OAuth2 si 401 ;
  CSRF Axios ; logout OIDC.
- **Shell UX** : layout Pilotage aligné maquette (tokens Barlow /
  accent acier) ; nav **sans** Rapports & SLA (**D-MOCKGAP**).
- **Création dossier** (**D-ACTIONS**) : formulaire type
  (`GET /api/dossier-types`) + champs dynamiques ; `POST /api/dossiers` ;
  masquage `sensitive` à l’affichage (**D-SENSITIVE**).
- **Dossier détail** : id/ref ; champs ; éléments (liste + content,
  Ajouter disabled — **D-ELEMENTS**) ; workflows ; viz diagram/history/
  tasks ; **start workflow** ; complete ; reassign (TA/WA).
- **Mes tâches / Mobile** (**A-MOBILE**) : filtre assigné = `auth.id`
  (sub) sur tâches chargées / parcours ; pas d’API inbox globale.
- **Notifications SSE** : panneau `GET /api/notifications/stream` ;
  mark-read **local** (**A-NOTIF-READ**).
- **Supervision MVP** : ouvrir par ref/id + lien création ; **pas** de
  stats mockées.
- **Qualité** : Vitest, Playwright (**D-E2E**), ESLint, Prettier,
  `vue-tsc` ; gate avec `./mvnw verify`.

### Primary goals — Vague 2 (admin)

- UI CRUD (**D-ADMIN-RES**) :
  - `/api/dossier-types` + `/api/dossier-types/{code}/acl`
  - `/api/document-templates` (lifecycle activate/archive/new-version)
  - `/api/connectors` + lecture `/api/connector-definitions`
  - `/api/workflow-type-configs` (dont `observerUserIds`)
- Authz UI alignée rôles backend (`TENANT_ADMIN` / `WORKFLOW_ADMIN`
  selon endpoints) ; secrets connecteurs jamais echo en clair si l’API
  les redacte.

### Secondary goals

- Design tokens depuis section Marque du mock (pas de page produit).
- Interfaces TS manuelles (**A-OPENAPI**) ; proxy Vite → Boot en dev.

### Non-goals

- Écran **Rapports & SLA** ; faux KPI Supervision.
- Liste/pagination dossiers (TODO §24).
- UI **notification-webhooks**.
- Upload éléments / stockage objet (MinIO interdit en dur).
- Designer BPMN éditable ; CDN / CORS / JWT browser.
- Client OpenAPI généré ; i18n multi-langue ; PWA.
- PATCH fields dossier (API absente).

## Alternatives considérées

| Décision | Options | Retenu |
|---|---|---|
| Périmètre V1 | A socle+instructeur / B mock full / C admin-first | **A** + admin en vague 2 |
| `/me` + sub | A étendre / B sans / C inbox API | **A** |
| Actions dossier | A lecture+tasks / B +start / C +create | **C** |
| Éléments | A download / B +Ajouter disabled / C upload | **B** |
| Admin | A hors V1 / B lecture / C CRUD | **C** (ressources A) |
| Séquençage | A 2 vagues / B parallèle / C admin first | **A** |
| Packaging | A Maven+Node / B CI copy / C commit bundles | **A** |
| E2E auth | A mock / B Keycloak CI / C pas E2E | **A** |
| Sensitive | A mask sauf TA / B tout montrer / C reveal bouton | **A** |
| Trous maquette | A masquer / B « bientôt » / C mock data | **A** |

## User Stories & Use Cases
**How will users interact with the solution?**

### Vague 1
1. Gestionnaire : créer un dossier (type + champs) puis l’ouvrir.
2. Gestionnaire : démarrer un workflow sur un dossier.
3. Participant : voir diagramme BPMN coloré + historique + tâches.
4. Assigné : compléter une tâche (détail ou mobile).
5. WA/TA : réassigner une tâche.
6. Utilisateur : recevoir notifications SSE ; mark-read local.
7. Superviseur : ouvrir un dossier par référence (sans file paginée).

### Vague 2
8. Admin : CRUD types de dossier + ACL.
9. Admin : gérer templates documents (versions / activate / archive).
10. Admin : gérer instances connecteurs (+ catalogue definitions).
11. WA/TA : configurer workflow types + observateurs.

### Parcours clés
- Boot → `/me` (avec `id`) → shell → créer/ouvrir dossier → start →
  viz → complete.
- 401 → OAuth2 Keycloak → retour SPA.
- Mutations → CSRF obligatoire.

### Edge cases
- 404/403/`ProblemDetail` ; link sans PI → 409 viz ; SSE fail-soft ;
  réassign masqué hors TA/WA ; Ajouter élément disabled ; champ
  sensitive masqué hors TA.

## Success Criteria
**How will we know when we're done?**

### Vague 1
- [ ] `frontend/` buildable ; `static/` peuplé via Maven plugin.
- [ ] `/api/auth/me` expose `id` ; auth store + CSRF + logout.
- [ ] Création dossier + start workflow + détail + viz + complete +
      reassign.
- [ ] Éléments : liste + download ; Ajouter disabled.
- [ ] SSE notifications ; Supervision sans faux KPI ; pas de nav
      Rapports.
- [ ] Masquage `sensitive` (sauf TA).
- [ ] Mes tâches filtrables via `auth.id`.
- [ ] Vitest + `vue-tsc` + ESLint ; Playwright mock session.
- [ ] Aucun JWT access en browser.

### Vague 2
- [ ] CRUD UI types(+ACL), templates, connectors, workflow-type-configs.
- [ ] Tests Vitest/E2E smoke admin (au moins create/list type).
- [ ] Docs + lint feature OK → prêt Phase 2/9 selon avancement.

## Constraints & Assumptions

### Constraints
- Stack figée analysis 11 + conventions FlowForge.
- Même origine ; chemins relatifs (context-path tenant).
- Authz serveur ; FE = masquage UX seulement.
- Pas de MinIO couplé ; pas de secrets en logs/UI.

### Assumptions

| Id | Hypothèse |
|---|---|
| **A-OPENAPI** | Interfaces TS manuelles ; pas Orval V1 |
| **A-NOTIF-READ** | Mark-read notifications = état local |
| **A-I18N** | UI française uniquement |
| **A-HISTORY** | Fallback Spring `index.html` pour Vue Router history |
| **A-MOBILE** | Vue Mes tâches incluse vague 1 |
| **A-LIST** | Pas de liste dossiers tant que TODO §24 |
| **A-THEME** | Tokens maquette via CSS + thème PrimeVue custom |
| **A-TASKS** | Pas d’inbox API globale ; filtre par `sub` sur tâches connues |

## Questions & Open Items

| Id | Statut |
|---|---|
| Q1–Q11 décisions stakeholder | **Clos** (tableau ci-dessus) |
| Implémentation exacte plugin Maven (version Node embalisée) | **Phase 5** — critère = verify peuplé |
| Surface exacte DTOs admin (secrets redact) | **Phase 3 design** — lire controllers existants |

## References

- [11-frontend.md](../../../analysis%20doc/11-frontend.md)
- [03-authentification.md](../../../analysis%20doc/03-authentification.md)
- [10-visualisation-workflow.md](../../../analysis%20doc/10-visualisation-workflow.md)
- Maquette UI HTML ; [DEFERRED-FEATURES.md](../../../DEFERRED-FEATURES.md)
- Décision n° 27 (`TODO.md`)
