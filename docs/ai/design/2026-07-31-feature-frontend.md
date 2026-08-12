---
phase: design
title: System Design & Architecture
description: Define the technical architecture, components, and data models
status: draft
created: 2026-08-12
updated: 2026-08-12
feature: frontend
requirements_aligned: pending-review
---

# System Design & Architecture

## Architecture Overview
**What is the high-level system structure?**

Tranche **frontend** (requirements `draft`) : SPA Vue 3 TypeScript
embarquée dans chaque instance Boot (décision n° 27), même origine BFF,
consommation des API déjà livrées. **Pas de nouveau bounded context
Java** — uniquement adaptateurs web Spring minimaux si fallback SPA /
resource handlers.

```mermaid
flowchart TB
  subgraph browser["Navigateur — même origine"]
    SPA[Vue 3 SPA]
    PINIA[Pinia stores]
    AX[Axios + CSRF]
    BPMN[bpmn-js Viewer]
    SSE[EventSource notifications]
    SPA --> PINIA
    SPA --> AX
    SPA --> BPMN
    SPA --> SSE
  end

  subgraph boot["Instance FlowForge / tenant"]
    STATIC[static/ index.html + assets]
    SEC[SecurityFilterChain BFF]
    AUTH[/api/auth/me · logout]
    DOS[/api/dossiers…]
    WF[/api/workflow-links… · tasks]
    NOTIF[/api/notifications/stream]
    DOC[/api/dossier-elements…/content]
    STATIC --> SPA
    AX --> SEC
    SSE --> SEC
    SEC --> AUTH
    SEC --> DOS
    SEC --> WF
    SEC --> NOTIF
    SEC --> DOC
  end

  IDP[Keycloak OIDC]
  SEC -.->|oauth2 login/logout| IDP
```

### Stack

| Couche | Choix |
|---|---|
| App | Vue 3 `<script setup lang="ts">`, Vue Router |
| Build | Vite ; `frontend/` → `dist/` → `src/main/resources/static/` |
| UI | PrimeVue + thème custom (tokens maquette) + PrimeIcons |
| State | Pinia (`auth`, `dossier`, `notifications`) |
| HTTP | Axios relatif, `withCredentials`, XSRF cookie/header |
| BPMN | `bpmn-js` Viewer (lecture seule) |
| Qualité | Vitest, Vue Test Utils, Playwright, ESLint, Prettier, `vue-tsc` |

### Responsabilités

| Zone | Rôle |
|---|---|
| `frontend/src/app` | Bootstrap, router, layout shell |
| `frontend/src/stores` | Pinia auth / dossier / notifications |
| `frontend/src/api` | Clients Axios typés (interfaces manuelles) |
| `frontend/src/views` | SupervisionMVP, DossierDetail, MobileTasks |
| `frontend/src/components/workflow` | BpmnViewer, TaskList, HistoryTimeline |
| `frontend/src/components/notifications` | NotificationDrawer |
| `frontend/src/styles` | Tokens CSS (maquette) |
| Spring `static/` + WebMvc | Sert artefacts ; fallback SPA si ajouté |

---

## Data Models
**What data do we need to manage?**

### Côté client (TypeScript)

```typescript
interface AuthUser {
  fullName: string | null;
  email: string | null;
  roles: string[]; // ex. DOSSIER_MANAGER, WORKFLOW_ADMIN
}

interface DossierSummary {
  id: string;
  reference: string;
  typeCode: string;
  status: string;
  fields: Record<string, unknown>;
  version: number;
}

interface WorkflowLinkView {
  linkId: string;
  workflowKey: string;
  status: string;
  processInstanceId: string | null;
  startedBy: string | null;
}

interface WorkflowDiagramView {
  bpmnXml: string;
  activeIds: string[];
  completedIds: string[];
  failedIds: string[];
}

interface WorkflowTaskView {
  taskId: string;
  name: string;
  assigneeId: string | null;
  createdAt: string | null;
}

interface NotificationEvent {
  // payload SSE existant — type discriminant éventuel dans payload
  id?: string;
  channel?: string;
  payload: Record<string, unknown>;
}
```

Alignement strict sur DTOs JSON backend ; pas de duplication métier
Java dans le FE.

### Persistance navigateur

- Cookie session httpOnly (serveur) + cookie `XSRF-TOKEN` lisible JS.
- Pas de localStorage de secrets ; Pinia mémoire seule.

---

## API Design
**How do components communicate?**

### Consommées (existantes)

| Méthode | Path | Usage FE |
|---|---|---|
| GET | `/api/auth/me` | Boot session |
| POST | `/api/auth/logout` | Déconnexion (form/handler Boot) |
| GET | `/api/dossiers/{id}` | Détail |
| GET | `/api/dossiers?ref=` | Ouverture par référence |
| GET | `/api/dossiers/{id}/workflows` | Links |
| POST | `/api/dossiers/{id}/workflows` | Démarrer workflow (si exposé UX) |
| GET | `/api/workflow-links/{linkId}/diagram` | bpmn-js |
| GET | `/api/workflow-links/{linkId}/history` | Timeline |
| GET | `/api/workflow-links/{linkId}/tasks` | Tâches actives |
| GET | `/api/workflow-links/{linkId}/dossier` | Panneau lié |
| POST | `/api/workflow-links/{linkId}/tasks/{taskId}/reassign` | Admin |
| POST | `/api/workflow-tasks/{taskId}/complete` | Complétion |
| GET | `/api/dossier-elements/{id}/content` | Download / preview |
| GET | `/api/notifications/stream` | SSE |

### Auth / CSRF

```typescript
axios.defaults.withCredentials = true;
axios.defaults.xsrfCookieName = 'XSRF-TOKEN';
axios.defaults.xsrfHeaderName = 'X-XSRF-TOKEN';
```

Erreurs : lire `ProblemDetail` (`type`, `title`, `status`, `detail`).

### Non consommées V1

Admin connecteurs, templates, types, ACL, webhooks notification,
génération PDF UI, métriques SLA.

---

## Component Breakdown
**What are the major building blocks?**

### Routes (Vue Router)

| Path | Vue | Notes |
|---|---|---|
| `/` | redirect → `/supervision` | |
| `/supervision` | `SupervisionView` | MVP ouverture ref/id |
| `/dossiers/:id` | `DossierDetailView` | Cœur V1 |
| `/tasks` | `MobileTasksView` | Layout responsive |
| `/login` callback | N/A | géré par Boot OAuth2 |

### Stores

- **auth** : `init()`, `logout()`, getters `isAdmin`, `roles`.
- **dossier** : charge détail + links + viz sélectionnée ; actions
  complete / reassign.
- **notifications** : connect SSE, buffer événements, mark-read local
  (UI only si pas d’API mark-read).

### bpmn-js

- Instancier Viewer dans `onMounted` ; `importXML(bpmnXml)` ;
  coloriser `activeIds` / `completedIds` / `failedIds` (markers CSS) ;
  destroy `onBeforeUnmount`.

### Build / packaging

1. `frontend/package.json` scripts : `dev`, `build`, `test`, `lint`,
   `typecheck`, `test:e2e`.
2. Vite `base: './'` ou base relative au context-path.
3. Maven : exécuter build Node pendant `generate-resources` **ou**
   étape CI qui copie `frontend/dist/**` → `static/` avant package.
4. Conserver `.gitkeep` remplacé par artefacts générés (gitignore
   `static/assets` si build CI only — **décision d’implémentation** :
   préférer commit ignore des bundles, build en verify).

---

## Design Decisions
**Why did we choose this approach?**

| Id | Décision | Alternatives | Raison |
|---|---|---|---|
| **DD-SCOPE** | Socle + dossier/viz/tâches/SSE ; Supervision MVP | Full mock screens | APIs manquantes (liste/SLA) |
| **DD-PACK** | Embarqué `static/` | CDN séparé | Décision 27 / CSRF SameSite=Lax |
| **DD-UI** | PrimeVue + tokens maquette | CSS only / autre kit | Stack figée + UX mock prioritaire |
| **DD-STATE** | Pinia | Fournir props only | Session + SSE + dossier partagés |
| **DD-HTTP** | Axios | fetch natif | CSRF helpers + analysis 11 |
| **DD-BPMN** | bpmn-js Viewer | SVG custom / image | Standard Flowable XML |
| **DD-TYPES** | Interfaces manuelles | openapi-typescript | Point 24 ouvert |
| **DD-TEST** | Vitest + Playwright gates | Tests FE plus tard | Analysis 11 non négociable |
| **DD-AUTHZ-UI** | Masquage rôles + confiance backend | FE-only guards | Sécurité réelle serveur |

### Alignement maquette

Écrans mock → mapping V1 :

| Mock | V1 |
|---|---|
| Supervision | MVP (recherche ref/id + placeholders) |
| Dossier — détail | **Complet** (API) |
| Rapports & SLA | **Hors scope** |
| Mobile | **Adaptatif** Mes tâches |
| Marque & logo | Tokens / guidelines CSS only |

---

## Non-Functional Requirements
**How should the system perform?**

- **Sécurité :** jamais de JWT access en JS ; CSRF sur mutations ;
  pas de HTML non échappé depuis champs dossier (XSS).
- **Perf :** lazy routes ; Viewer BPMN chargé sur écran dossier ;
  SSE fail-soft.
- **A11y :** focus visible, labels formulaires, contrastes tokens mock.
- **Compat :** navigateurs evergreen ; mobile pour vue tâches.
- **CI :** `npm run typecheck && lint && test` ; Playwright smoke ;
  `./mvnw verify` inclut ou suppose static à jour.
- **i18n :** français V1.
- **Observabilité FE :** erreurs API log console structurées ; pas de
  PII dans logs client.

## Risks

| Risque | Mitigation |
|---|---|
| Context-path tenant casse assets/API | `base` Vite + chemins relatifs Axios |
| Absence `sub` dans `/me` limite « mes tâches » | Q6 ; filtrer par assignee si API renvoie ids ; sinon liste link tasks |
| Bundle static oublié en CI | Gate verify échoue si `index.html` manquant |
| PrimeVue ≠ maquette | Tokens + overrides CSS ; maquette prime UX |
| Playwright + OAuth CI flaky | Mock session / route Bypass test profile |

## Next

Phase 2 — Review Requirements (`dev-requirements`), puis Phase 3 Design
review formalisée.
