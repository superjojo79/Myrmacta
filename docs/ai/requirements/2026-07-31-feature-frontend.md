---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: draft
created: 2026-08-12
updated: 2026-08-12
feature: frontend
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
  (`WORKFLOW_ADMIN` / `TENANT_ADMIN`), opérateurs mobiles, feature aval
  (évolution UX).
- **Situation actuelle :** backends Complete (dont `visualisation-workflow`) ;
  analyse [11-frontend.md](../../../analysis%20doc/11-frontend.md) ;
  maquette HTML de référence
  [FlowForge - Maquettes UI.html](../../../analysis%20doc/FlowForge%20-%20Maquettes%20UI.html)
  (écrans Supervision, Dossier détail, Rapports & SLA, Mobile, Marque) ;
  décision n° 27 (frontend embarqué `static/`, TypeScript, Vitest,
  Playwright, ESLint/Prettier).
- **Écart API :** la maquette Supervision / Rapports suppose liste,
  agrégats SLA et console connecteurs riches — **TODO §24** (liste /
  pagination dossiers) et métriques SLA **non livrés**. La V1 FE doit
  s’adosser aux endpoints existants et marquer le reste en non-goals.

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

- Livrer le **socle SPA** (**D-SCOPE = A**) : Vue 3 + Vite + TypeScript
  (`strict`) + PrimeVue + Pinia + Axios + `bpmn-js` Viewer, projet sous
  `frontend/`, build copié dans `src/main/resources/static/` (décision
  n° 27).
- **Session BFF** (**D-AUTH = A**) : store Pinia `auth` ; `GET /api/auth/me`
  au boot ; redirection `/oauth2/authorization/keycloak` si 401 ; Axios
  `withCredentials` + CSRF double-submit (`XSRF-TOKEN` /
  `X-XSRF-TOKEN`) ; logout via `/api/auth/logout` (handler OIDC existant).
- **Shell UX aligné maquette** (**D-UX = A**) : layout Pilotage
  (nav + contenu) ; tokens / typo proches du mock (Barlow family,
  palette acier / accent `#5980a6`) ; **maquette prime** pour rendu UX
  vs widgets PrimeVue bruts.
- **Écrans V1 branchés API** (**D-SCREENS = A**) :
  1. **Dossier — détail** : chargement par `id` ou `ref` ; champs ;
     éléments (métadonnées + lien contenu) ; workflows liés ; panneau
     visualisation (`diagram` + `history` + `tasks`) via `bpmn-js` ;
     réassign (TA/WA) ; complétion de tâche.
  2. **Mes tâches / Mobile** : liste tâches issues des links connus /
     parcours dossier (pas d’API « inbox globale » dédiée — **A-TASKS**) ;
     actions ancrées (valider / demander pièce = complete task).
  3. **Notifications** : panneau alimenté par SSE
     `GET /api/notifications/stream` (canal `BROWSER`).
  4. **Supervision (MVP)** : entrée « ouvrir dossier » par référence /
     id + placeholders stats **non branchés** tant que liste/pagination
     absentes (TODO §24).
- **Qualité non négociable** (**D-QA = A**) : Vitest + Vue Test Utils ;
  Playwright (session + parcours dossier/tâche) ; ESLint
  `@vue/eslint-config-typescript` + Prettier ; `vue-tsc --noEmit` en CI ;
  gate npm alignée sur `./mvnw verify`.
- **Packaging** (**D-PACK = A**) : `npm run build` → copie Maven
  (`frontend-maven-plugin` ou resource copy) vers `static/` ; SPA
  history fallback (forward non-API → `index.html`) côté Spring si
  nécessaire.

### Secondary goals

- Design tokens CSS dérivés de la section **Marque & logo** du mock
  (variables, pas une page produit).
- Préparer un client HTTP typé manuel (interfaces TS) en attendant
  OpenAPI (TODO §24) — **pas** de génération Orval dans cette tranche.
- Proxy Vite → Boot en dev (`server.proxy`).

### Non-goals (explicitement hors scope V1)

- Écran **Rapports & SLA** (pas d’API métriques) — différé.
- File Supervision complète (liste/filtre/pagination dossiers) — bloquée
  TODO §24.
- Admin connecteurs / templates / types / ACL / webhooks notification —
  différé (APIs existent, hors tranche UX V1).
- Designer BPMN éditable (Flowable Modeler) — hors produit ; Viewer
  lecture seule seulement.
- Hébergement CDN / CORS / JWT browser — écarté (décision n° 27 / 15).
- Client OpenAPI généré — après point 24.
- i18n multi-langue UI — hors V1 (fr UI).
- PWA / offline — hors V1.
- Modification champs dossier (PATCH fields) — API absente.

## Alternatives considérées

| Approche | Trade-off | Verdict |
|---|---|---|
| **A — Socle + dossier/viz/tâches/SSE** (retenue) | Couvre le parcours métier central avec APIs existantes ; Supervision/Rapports en stub | **Recommandée** |
| B — Maquette pixel-perfect tous écrans | Bloque sur APIs manquantes (liste, SLA) ou invente des mocks non contractuels | Écartée |
| C — Admin-first (types, templates, connecteurs) | Utile ops, faible valeur instructeur quotidien | Différée |

## User Stories & Use Cases
**How will users interact with the solution?**

1. **En tant que** gestionnaire authentifié, **je veux** ouvrir un
   dossier par référence, **afin de** voir champs, éléments et état
   workflow.
2. **En tant que** participant workflow, **je veux** visualiser le
   diagramme BPMN coloré (actif / terminé / erreur), **afin de**
   comprendre où en est l’instance.
3. **En tant que** assigné, **je veux** compléter une tâche utilisateur
   depuis le détail ou la vue mobile, **afin de** faire avancer le
   processus.
4. **En tant que** `WORKFLOW_ADMIN` / `TENANT_ADMIN`, **je veux**
   réassigner une tâche, **afin de** débloquer la file.
5. **En tant que** utilisateur connecté, **je veux** recevoir les
   notifications navigateur via SSE, **afin de** réagir aux changements
   d’état / échecs.
6. **En tant que** développeur, **je veux** un scaffold Vite + gates CI
   (tsc / eslint / vitest / playwright), **afin de** livrer sans dette
   qualité.

### Parcours clés

- Boot → `/api/auth/me` → shell → ouvrir dossier → viz + complete task.
- 401 session → redirect OAuth2 Keycloak → retour SPA.
- Mutation POST (complete / reassign) → cookie CSRF présent sinon 403.

### Edge cases

- Dossier / link inconnu → affichage erreur `ProblemDetail` (404).
- Non-participant viz → 403.
- Link `RESERVED` sans `processInstanceId` → panneau dossier OK ; viz
  diagram/history/tasks en état « non démarré » (409).
- SSE coupé → reconnexion soft ; UI non bloquante.
- Rôles sans droit réassign → bouton masqué / disabled.

## Success Criteria
**How will we know when we're done?**

- [ ] Projet `frontend/` (vue-ts) buildable ; artefacts dans `static/`
      via pipeline documenté.
- [ ] Session : `auth` store + CSRF Axios + logout OIDC.
- [ ] Écran dossier détail branché (`GET /api/dossiers/{id|?ref=}`,
      workflows, viz, complete, reassign selon rôle).
- [ ] Viewer `bpmn-js` affiche XML + overlays active/completed/failed.
- [ ] Panneau notifications SSE opérationnel (mockable en test).
- [ ] Supervision MVP : accès dossier par ref/id (pas de fausse liste
      paginée).
- [ ] Vitest verts ; `vue-tsc --noEmit` ; ESLint ; au moins 1 parcours
      Playwright (auth mockée ou bypass test) documenté.
- [ ] Aucun JWT access token stocké / manipulé côté browser.
- [ ] Lint AI docs feature `frontend` OK ; requirements status → review
      Phase 2.

## Constraints & Assumptions
**What limitations do we need to work within?**

### Constraints

- Stack figée : Vue 3, TS, Vite, PrimeVue, Pinia, Axios, bpmn-js
  (analysis 11 + conventions FlowForge).
- Même origine ; instance = tenant (`app.tenant.id`) ; context-path
  éventuel `/{tenantId}/` — chemins relatifs.
- Authz réelle côté backend ; le FE masque UX mais ne fait pas confiance
  aux rôles seuls.
- Pas de couplage MinIO ; téléchargement éléments via
  `/api/dossier-elements/{id}/content` si présent.

### Assumptions (nommées)

| Id | Hypothèse |
|---|---|
| **A-TASKS** | Pas d’API inbox globale V1 : « Mes tâches » agrège via parcours dossier / links chargés ; une API inbox pourra arriver plus tard. |
| **A-LIST** | Liste dossiers Supervision complète dépend TODO §24 ; V1 = accès direct par id/ref. |
| **A-ME** | `AuthUserResponse` = `{ fullName, email, roles }` (pas de `sub` exposé) — identité affichage = email / fullName. |
| **A-THEME** | Tokens mock (Barlow, accent acier) adaptés via CSS variables + thème PrimeVue custom, sans recopier le HTML bundlé tel quel. |
| **A-HISTORY** | Spring sert la SPA avec fallback `index.html` pour routes client (à confirmer / ajouter en implémentation). |

## Questions & Open Items
**What do we still need to clarify?**

| Id | Question | Statut Phase 1 |
|---|---|---|
| Q1 | Faut-il livrer Supervision liste avant TODO §24 (mock données) ? | **Tranché D-SCREENS = A** — pas de mock contractuel ; accès id/ref seulement |
| Q2 | Inclure admin templates/connecteurs dans V1 ? | **Non** — non-goals |
| Q3 | Client OpenAPI généré dès maintenant ? | **Non** — après point 24 |
| Q4 | Plugin Maven exact (`frontend-maven-plugin` vs CI copy) ? | **Ouvert Phase 3/5** — critère = `static/` peuplé en verify |
| Q5 | Playwright contre Keycloak réel en CI ? | **Différé** — CI = mock/bypass ; smoke Keycloak hors CI |
| Q6 | Exposer `sub` dans `/api/auth/me` pour matching participants ? | **Ouvert** — si besoin réassign UX / « moi » ; sinon email/rôles suffisent V1 |

## References

- [11-frontend.md](../../../analysis%20doc/11-frontend.md)
- [03-authentification.md](../../../analysis%20doc/03-authentification.md) (CSRF / session)
- [10-visualisation-workflow.md](../../../analysis%20doc/10-visualisation-workflow.md)
- Maquette UI HTML (même dossier analysis)
- [DEFERRED-FEATURES.md](../../../DEFERRED-FEATURES.md)
- Décision n° 27 (`TODO.md`)
