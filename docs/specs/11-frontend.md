# Frontend — Vue 3 + TypeScript + OpenVue

> SPA consommée depuis la même origine que l'API Spring Boot (pattern BFF).  
> Aucun token JWT manipulé côté browser.  
> Packaging : build Vite embarqué dans `src/main/resources/static/` de chaque instance
> (décision n° 27) — confirme `SameSite=Lax` sans CORS.

## Marque affichée

Le nom commercial est **Myrmacta** (décision n° 105). La barre latérale, sur fond encre `#1E2A3A`,
affiche le logo sombre (`myrmacta-logo-dark.svg`, encre `#F3F4F6`, corail `#FF7A52`). Le portail,
sur fond clair, affiche le logo clair (encre `#1E2A3A`, corail `#E4572E`). Fichiers :
`frontend/src/assets/brand/`, favicon dans `frontend/public/`, copies documentaires dans
`docs/brand/`. Le favicon est la tête corail à deux antennes ; la fourmi entière reste le logo.
Signature : « L'intelligence collective au service de vos dossiers et décisions ».

---

## Maquette UI de référence

La construction de l'interface doit se baser sur le mock HTML présent dans ce dossier :

- **[Myrmacta - Maquettes UI.html](Myrmacta%20-%20Maquettes%20UI.html)** — maquettes UI de référence (écrans, navigation, composition visuelle). Le nom affiché dans la maquette est Myrmacta.

Toute implémentation Vue / OpenVue (layouts, pages, composants métier, hiérarchie visuelle) doit s'aligner sur ce mock. En cas d'écart entre la maquette et les choix techniques de cette analyse (OpenVue, BFF, etc.), la maquette prime pour le rendu et l'UX ; la stack technique documentée ici reste celle à utiliser pour l'implémenter. PrimeVue (devenu propriétaire) a été remplacé par **OpenVue**, fork communautaire MIT (PR #165) : mêmes composants et thème Aura, seuls les imports changent.

Pour faciliter la création de l'interface, s'appuyer sur les **skills spécialisés** du dépôt (à charger avant d'écrire le code UI) :

- [`myrmacta-conventions`](../../.claude/skills/myrmacta-conventions/SKILL.md) — conventions projet (stack FE, packaging, priorités)
- [`vue-best-practices`](../../.claude/skills/vue-best-practices/SKILL.md) — Composition API, SFC, réactivité, macros `<script setup>`, structure des composants
- [`pinia`](../../.claude/skills/pinia/SKILL.md) — stores et état applicatif
- [`vite`](../../.claude/skills/vite/SKILL.md) — bundler / config de build
- [`vue-testing-best-practices`](../../.claude/skills/vue-testing-best-practices/SKILL.md), [`vitest`](../../.claude/skills/vitest/SKILL.md), [`playwright-best-practices`](../../.claude/skills/playwright-best-practices/SKILL.md) — tests composants et e2e

---

**Choix retenu : Vue 3 (Vite) + TypeScript + OpenVue + Pinia**

| Composant | Rôle |
|---|---|
| Vue 3 | Framework SPA |
| TypeScript | Typage statique — `<script setup lang="ts">`, `strict: true` |
| Vite | Bundler / HMR (dev) ; build de production copié dans `static/` |
| OpenVue | Librairie de composants UI métier (fork MIT de PrimeVue : DataTable, formulaires, timeline…) |
| Pinia | State management (remplaçant officiel Vuex) |
| Axios | Client HTTP — appels relatifs vers l'API Spring Boot (même origine) |
| Vitest + Vue Test Utils | Tests unitaires / composants |
| Playwright | Tests end-to-end (parcours session, formulaires, workflow) |
| ESLint + Prettier | Lint et formatage — `@vue/eslint-config-typescript` |

### Pourquoi TypeScript

Écarté à tort dans une première itération de cette analyse (exemples de code en `.js` pur) alors que
l'application manipule des ACL, des variables de workflow et des documents multi-tenant — un terrain
propice aux régressions silencieuses au refactor sans typage statique. Le typage statique est d'autant
plus pertinent que le point ouvert n° 24 du [TODO](../decisions/README.md) (statut de contrat de l'API vis-à-vis du
frontend) reste à trancher : si une génération OpenAPI est retenue côté backend, un client TypeScript
généré (`openapi-typescript` ou `orval`) donne une garantie de synchronisation front/back qu'un client
JavaScript ne peut pas offrir — à réévaluer dès que le point n° 24 sera acté.

### Qualité et tests — non négociable dès le premier commit

Absent de la première itération de cette analyse. Le projet backend est couvert par ArchUnit et une
suite de tests exhaustive (`./mvnw verify`) ; le frontend doit suivre la même discipline dès sa mise en
place plutôt que rattraper la dette après coup :

- **Vitest** (unitaire/composants) — colocalisé avec le code (`*.spec.ts`), exécuté en CI au même titre
  que `./mvnw verify` côté backend.
- **Playwright** (e2e) — au minimum le parcours de session (redirection Keycloak, `/api/auth/me`,
  cookie CSRF) et un parcours métier représentatif (création de dossier, complétion de tâche).
- **ESLint (`@vue/eslint-config-typescript`) + Prettier** — gate CI, cohérent avec le formatage imposé
  côté Java par le build Maven.
- **`vue-tsc --noEmit`** en CI — le compilateur TypeScript comme vérification statique indépendante du
  bundler Vite (esbuild ne type-check pas).

### Packaging — frontend embarqué

Le frontend n'est **pas** déployé séparément. Le pipeline de build :

1. `npm run build` produit le bundle dans `frontend/dist/` ;
2. le contenu est copié (Maven `frontend-maven-plugin` ou étape CI) vers
   `src/main/resources/static/` ;
3. chaque instance Spring Boot dédiée à un tenant (décision n° 13) sert à la fois l'API/BFF et les
   assets statiques. Le `server.servlet.context-path` (`/{tenantId}/`) est activé par défaut
   (décision n° 14, lot A1, PR #119) ; le profil `local` le neutralise (`context-path: ""`) pour
   le poste de développement.

Conséquences actées : pas de configuration CORS ; cookie de session et CSRF en `SameSite=Lax`
(décision n° 15) ; N instances embarquent le même artefact front (acceptable — simplicité
opérationnelle prioritaire).

### Alternatives écartées

- **React** — viable, mais Vue 3 plus adapté aux apps métier formulaires/tableaux avec moins de verbosité
- **Thymeleaf SSR / htmx** — insuffisant pour une application riche avec designer BPMN et preview documentaire
- **Hébergement front séparé (CDN)** — imposerait `SameSite=None; Secure` et CORS avec `credentials` ;
  écarté au profit de l'embarquement (décision n° 27)

---

## Initialisation de la session (Pinia)

```typescript
// stores/auth.ts
import { defineStore } from 'pinia';
import axios from 'axios';

interface AuthenticatedUser {
    id: string;
    email: string;
    roles: string[];
}

interface AuthState {
    user: AuthenticatedUser | null;
    authenticated: boolean;
}

export const useAuthStore = defineStore('auth', {
    state: (): AuthState => ({ user: null, authenticated: false }),
    actions: {
        async init(): Promise<void> {
            try {
                const { data } = await axios.get<AuthenticatedUser>('/api/auth/me',
                    { withCredentials: true });
                this.user = data;
                this.authenticated = true;
            } catch {
                window.location.href = '/oauth2/authorization/keycloak';
            }
        }
    }
});
```

La configuration Axios pour le jeton CSRF (`xsrfCookieName`/`xsrfHeaderName`) est documentée dans
[03-authentification.md](03-authentification.md#protection-csrf).

## Dépendances npm

```bash
npm create vite@latest frontend -- --template vue-ts
npm install openvue @openvue/themes primeicons
npm install pinia axios bpmn-js

# Qualité et tests
npm install -D vitest @vue/test-utils jsdom
npm install -D @playwright/test
npm install -D eslint @vue/eslint-config-typescript eslint-plugin-vue prettier
npm install -D vue-tsc
```

## Implémentation — contrat OpenAPI et pagination des listes admin (PR #58, TODO §24)

*Synthèse ajoutée lors de la remédiation (TODO §95), d'après les documents de travail
([requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-08-19-feature-openapi-pagination-admin.md),
[implementation](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-08-19-feature-openapi-pagination-admin.md)).*

- **Contrat :** springdoc publie l'OpenAPI 3 au runtime seulement si `app.openapi.enabled=true` (défaut `false`,
  relayé vers `springdoc.*` par `OpenApiEnvironmentPostProcessor`). Le fichier versionné
  [`openapi/openapi.yaml`](../../openapi/openapi.yaml) est la référence : `OpenApiContractIT` échoue si le contrat
  généré en diffère. SSE, upload multipart et `POST /api/webhooks/**` sont masqués (`@Hidden`).
- **Pagination :** les listes admin (types, templates, connecteurs, configurations de workflow, webhooks de
  notification, file de purge) renvoient la même enveloppe de page que `GET /api/dossiers` (`PageQuery` /
  `PageResult` dans le domaine, `Pageable` confiné aux adaptateurs JPA). La file de purge ajoute ses `counts`
  globaux. Les listes imbriquées restent des tableaux.
- **SPA :** `AdminPager` et `useAdminListQuery` ; les listes déroulantes hors admin lisent toutes les pages
  (`listAllPages`, taille 100). Écran `/admin/notification-webhooks` ajouté.

## Implémentation — tests de mutation StrykerJS (issue #245, TODO §108)

- **Outil :** StrykerJS et son lanceur Vitest, dépendances de développement (hors SPA livré). Configuration
  [`frontend/stryker.config.mjs`](../../frontend/stryker.config.mjs) ; `npm run test:mutation`, hors
  `npm run test:all`. Rapport : `frontend/reports/mutation/` (HTML et JSON, ignoré par git).
- **Périmètre :** la logique sans rendu, `src/stores`, `src/composables`, `src/api`, `portal`. Les composants `.vue`
  et la logique BPMN de `src/components` restent hors mutation (coût, voir §108).
- **CI :** [`mutation.yml`](../../.github/workflows/mutation.yml), run complet chaque semaine avec seuil bloquant
  (cliquet, `thresholds.break`), run ciblé sur les fichiers modifiés de chaque PR, sans seuil.
- **Écrire un test compatible :** les specs tournent aussi dans le bac à sable de Stryker
  (`frontend/.stryker-tmp/sandbox-*`). Une fixture du backend se lit par `repoFile('src/test/resources/…')`
  (`src/test/repoRoot.ts`), jamais par un chemin relatif au répertoire courant.
