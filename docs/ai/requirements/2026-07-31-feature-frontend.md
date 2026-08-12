---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
created: 2026-08-12
updated: 2026-08-12
feature: frontend
stakeholder_decisions: 2026-08-12
phase2: 2026-08-12
phase2_stakeholder_confirm: 2026-08-12
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
- **Écarts API comblés par cette feature (Phase 2) :**
  - `AuthUserResponse` sans `id` (sub) — **D-ME**
  - pas d’inbox tâches — **D-INBOX**
  - pas de liste éléments par dossier en HTTP — **D-ELEMENTS-API**
- **Écarts API volontairement hors V1 FE :** liste/pagination dossiers et
  métriques SLA (TODO §24) — **D-MOCKGAP**.

## Stakeholder decisions

### Phase 1 (2026-08-12)

| Id | Décision |
|---|---|
| **D-SCOPE = A** | Socle SPA + parcours instructeur ; pas de maquette pixel-complete sur APIs absentes |
| **D-ME = A** | Étendre `AuthUserResponse` avec `id` = OIDC `sub` |
| **D-ACTIONS = C** | Créer dossier + démarrer workflow + complete + reassign |
| **D-ELEMENTS = B** | Liste + download ; bouton « Ajouter » visible **désactivé** (« bientôt ») |
| **D-ADMIN = C** + **D-ADMIN-RES = A** | Vague 2 : CRUD `dossier-types`(+ACL), `document-templates`, `connectors`(+definitions lecture), `workflow-type-configs` ; **pas** notification-webhooks UI |
| **D-SEQ = A** | Vague 1 instructeur → vague 2 admin |
| **D-PACK = A** | `frontend-maven-plugin` → `static/` ; bundles **non commités** |
| **D-E2E = A** | Playwright session mockée ; Keycloak réel hors CI |
| **D-SENSITIVE = A** | Masquer valeurs `sensitive` (`••••`) sauf `TENANT_ADMIN` |
| **D-MOCKGAP = A** | Masquer Rapports & SLA ; Supervision sans faux KPI |

### Phase 2 review (2026-08-12)

| Id | Décision |
|---|---|
| **D-INBOX = B** | Ajouter `GET /api/workflow-tasks/mine` (inbox assignée à `sub`) |
| **D-STATUS = A** | UI changement statut via `PATCH /api/dossiers/{id}/status` (API déjà là) |
| **D-ELEMENTS-API = A** | Ajouter `GET /api/dossiers/{id}/elements` |
| **D-COMPLETE = A** | Complete avec `variables: {}` uniquement (pas de form outcomes) |

### Corrections factuelles Phase 2 (code)

| Item | Réalité code | Requirements |
|---|---|---|
| `/api/auth/me` | record actuel `AuthUserResponse(name, email, roles)` | Cible V1 : **`{ id, name, email, roles }`** (`name` ≠ `fullName`) |
| Création dossier | `CreateDossierRequest(dossierTypeCode, label, fields)` | Formulaire FE aligné (label obligatoire) |
| Complete task | body optionnel `CompleteTaskRequest(variables)` | FE envoie `{}` / omit (**D-COMPLETE**) |

## Goals & Objectives
**What do we want to achieve?**

### Primary goals — Vague 1 (instructeur)

- **Socle SPA** (**D-SCOPE**) : Vue 3 + Vite + TypeScript `strict` +
  PrimeVue + Pinia + Axios + `bpmn-js` Viewer sous `frontend/` ;
  packaging **D-PACK**.
- **Session BFF** : store `auth` ; `GET /api/auth/me` →
  `{ id, name, email, roles }` (**D-ME**) ; redirect OAuth2 si 401 ;
  CSRF Axios ; logout OIDC.
- **Shell UX** : layout Pilotage aligné maquette (tokens Barlow /
  accent acier) ; nav **sans** Rapports & SLA (**D-MOCKGAP**).
- **Création dossier** (**D-ACTIONS**) : `dossierTypeCode` + `label` +
  champs dynamiques depuis `GET /api/dossier-types` ;
  `POST /api/dossiers` ; masquage `sensitive` à l’**affichage**
  (**D-SENSITIVE**).
- **Dossier détail** : id/ref ; champs ; **statut** (**D-STATUS**) ;
  éléments via **`GET /api/dossiers/{id}/elements`** + download content
  (**D-ELEMENTS** / **D-ELEMENTS-API**) ; Ajouter disabled ; workflows ;
  viz ; **start workflow** ; complete (**D-COMPLETE**) ; reassign (TA/WA).
- **Mes tâches / Mobile** (**A-MOBILE**, **D-INBOX**) : consomme
  `GET /api/workflow-tasks/mine` filtré serveur pour `assigneeId === sub`
  (`auth.id`) ; actions complete depuis la liste.
- **Notifications SSE** : `GET /api/notifications/stream` ; mark-read
  **local** (**A-NOTIF-READ**).
- **Supervision MVP** : ouvrir par ref/id + lien création ; **pas** de
  stats mockées.
- **Qualité** : Vitest, Playwright (**D-E2E**), ESLint, Prettier,
  `vue-tsc` ; gate avec `./mvnw verify`.

### Primary goals — Vague 2 (admin)

- UI CRUD (**D-ADMIN-RES**) :
  - `/api/dossier-types` + `/api/dossier-types/{code}/acl` (`TENANT_ADMIN`)
  - `/api/document-templates` (activate/archive/new-version)
  - `/api/connectors` + lecture `/api/connector-definitions`
  - `/api/workflow-type-configs` (dont `observerUserIds`)
- Authz UI alignée rôles backend ; secrets connecteurs non réaffichés
  si l’API les redacte.

### Secondary goals

- Design tokens depuis section Marque du mock (pas de page produit).
- Interfaces TS manuelles (**A-OPENAPI**) ; proxy Vite → Boot en dev.

### Non-goals

- Écran **Rapports & SLA** ; faux KPI Supervision.
- Liste/pagination dossiers (TODO §24).
- UI **notification-webhooks** ; execute connecteur depuis l’UI admin.
- Upload éléments / stockage objet (MinIO interdit en dur).
- Formulaire variables / outcomes de complete (**D-COMPLETE** = `{}` only).
- PATCH **fields** dossier (endpoint absent — distinct du PATCH status).
- Designer BPMN éditable ; CDN / CORS / JWT browser.
- Client OpenAPI généré ; i18n multi-langue ; PWA.

## Alternatives considérées

| Décision | Options | Retenu |
|---|---|---|
| Périmètre V1 | A socle+instructeur / B mock full / C admin-first | **A** + admin vague 2 |
| `/me` + sub | A étendre / B sans / C inbox API only | **A** |
| Actions dossier | A lecture+tasks / B +start / C +create | **C** |
| Éléments UX | A download / B +Ajouter disabled / C upload | **B** |
| Admin | A hors V1 / B lecture / C CRUD | **C** (ressources A) |
| Séquençage | A 2 vagues / B parallèle / C admin first | **A** |
| Packaging | A Maven+Node / B CI copy / C commit bundles | **A** |
| E2E auth | A mock / B Keycloak CI / C pas E2E | **A** |
| Sensitive | A mask sauf TA / B tout montrer / C reveal bouton | **A** |
| Trous maquette | A masquer / B « bientôt » / C mock data | **A** |
| Inbox tâches | A cache session / B endpoint mine / C retirer page | **B** |
| Statut dossier | A UI PATCH status / B hors V1 / C + PATCH fields | **A** |
| Liste éléments | A GET dossiers/{id}/elements / B via link only / C différer | **A** |
| Complete vars | A `{}` / B form libre / C outcomes prédéfinis | **A** |

## User Stories & Use Cases
**How will users interact with the solution?**

### Vague 1
1. **En tant que** gestionnaire, **je veux** créer un dossier (type,
   label, champs), **afin de** démarrer une instruction.
2. **En tant que** gestionnaire, **je veux** ouvrir un dossier par id/ref
   et changer son statut, **afin de** refléter l’avancement métier.
3. **En tant que** gestionnaire, **je veux** démarrer un workflow lié,
   **afin d’** enclencher le processus Flowable.
4. **En tant que** participant, **je veux** voir diagramme / historique /
   tâches, **afin de** comprendre l’exécution.
5. **En tant qu’** assigné, **je veux** voir **mes** tâches via l’inbox
   et les compléter (`variables: {}`), **afin de** faire avancer le process.
6. **En tant que** WA/TA, **je veux** réassigner une tâche, **afin de**
   débloquer la file.
7. **En tant qu’** utilisateur, **je veux** des notifications SSE,
   **afin de** réagir aux changements d’état.
8. **En tant que** superviseur, **je veux** ouvrir un dossier par
   référence sans file paginée, **afin d’** accéder vite au détail.

### Vague 2
9. **En tant que** `TENANT_ADMIN`, **je veux** CRUD types + ACL,
   **afin de** configurer le modèle dossier.
10. **En tant qu’** admin templates, **je veux** versionner/activer/
    archiver des templates, **afin de** piloter la génération doc.
11. **En tant qu’** admin, **je veux** gérer les connecteurs,
    **afin de** brancher les sources/cibles.
12. **En tant que** WA/TA, **je veux** configurer les workflow types +
    observateurs, **afin de** contrôler démarrage et visibilité.

### Parcours clés
- Boot → `/me` (`id`) → shell → créer/ouvrir → status → start → viz →
  complete / inbox mine.
- 401 → OAuth2 Keycloak → retour SPA ; mutations → CSRF.

### Edge cases
- 404/403/`ProblemDetail` ; link sans PI → 409 viz ; SSE fail-soft ;
  réassign masqué hors TA/WA ; Ajouter élément disabled ; sensitive
  masqué hors TA ; inbox vide → empty state ; dossier sans éléments →
  liste vide.

## Success Criteria
**How will we know when we're done?**

### Vague 1
- [ ] `frontend/` buildable ; `static/` peuplé via Maven plugin.
- [ ] `/api/auth/me` expose `id` (+ `name`, `email`, `roles`) ; auth
      store + CSRF + logout ; **aucun JWT** access en browser.
- [ ] Création dossier (`dossierTypeCode`, `label`, `fields`) + start
      workflow + détail + viz + complete `{}` + reassign + **PATCH status**.
- [ ] `GET /api/dossiers/{id}/elements` + download content ; Ajouter
      disabled.
- [ ] `GET /api/workflow-tasks/mine` + vue Mes tâches.
- [ ] SSE notifications ; Supervision sans faux KPI ; pas de nav
      Rapports.
- [ ] Masquage `sensitive` (sauf TA).
- [ ] Vitest + `vue-tsc` + ESLint ; Playwright mock session (**D-E2E**).
- [ ] `./mvnw verify` vert (FE packaging inclus).

### Vague 2
- [ ] CRUD UI types(+ACL), templates, connectors, workflow-type-configs.
- [ ] Tests Vitest + E2E smoke admin (au moins create/list type).
- [ ] Lint feature OK ; docs prêtes pour Phase 3+.

## Constraints & Assumptions

### Constraints
- Stack figée analysis 11 + conventions FlowForge.
- Même origine ; chemins relatifs (context-path tenant).
- Authz serveur (ACL type, guards dossier, rôles admin) ; FE = masquage UX.
- Pas de MinIO couplé ; pas de secrets en logs/UI.
- Traçage `task` CLI indisponible (`unknown command 'task'`).

### Assumptions

| Id | Hypothèse |
|---|---|
| **A-OPENAPI** | Interfaces TS manuelles ; pas Orval V1 |
| **A-NOTIF-READ** | Mark-read notifications = état local |
| **A-I18N** | UI française uniquement |
| **A-HISTORY** | Fallback Spring `index.html` pour Vue Router history |
| **A-MOBILE** | Vue Mes tâches incluse vague 1 (alimentée par D-INBOX) |
| **A-LIST** | Pas de liste dossiers tant que TODO §24 |
| **A-THEME** | Tokens maquette via CSS + thème PrimeVue custom |
| **A-INBOX-SHAPE** | `/mine` renvoie tâches actives assignées au `sub` courant (tenant instance) ; shape détaillée en design (taskId, name, dossierId/ref, createdAt, …) |
| **A-ELEMENTS-AUTHZ** | Liste éléments = même authz lecture que le dossier |
| **A-STATUS-ENUM** | UI propose les valeurs `DossierStatus` acceptées par l’API (optimistic `expectedVersion`) |

## Questions & Open Items

| Id | Statut |
|---|---|
| Décisions Phase 1 + Phase 2 | **Clos** |
| Shape exacte DTO `/mine` + pagination éventuelle | **Phase 3 design** |
| Version Node embalisée dans frontend-maven-plugin | **Phase 5** |
| Redaction secrets connecteurs (champ API) | **Phase 3** — lire DTOs existants |

## Phase 2 review notes

- Template README : sections obligatoires présentes + décisions /
  alternatives (aligné features antérieures).
- Contradiction « PATCH status API absente » **corrigée** (D-STATUS).
- Contradiction Mes tâches sans source de données **corrigée** (D-INBOX).
- Gap liste éléments HTTP **corrigé** (D-ELEMENTS-API).
- Naming `/me` aligné code (`name`).
- Pas de retour Phase 1 : gaps résolus avec stakeholder.

## References

- [11-frontend.md](../../../analysis%20doc/11-frontend.md)
- [03-authentification.md](../../../analysis%20doc/03-authentification.md)
- [10-visualisation-workflow.md](../../../analysis%20doc/10-visualisation-workflow.md)
- Maquette UI HTML ; [DEFERRED-FEATURES.md](../../../DEFERRED-FEATURES.md)
- Décision n° 27 (`TODO.md`)
- Controllers : `AuthController`, `DossierController`,
  `WorkflowTaskController`, `WorkflowLinkVisualizationController`
