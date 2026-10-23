# Frontend — Vue 3 + TypeScript + OpenVue

> SPA consommée depuis la même origine que l'API Spring Boot (pattern BFF).  
> Aucun token JWT manipulé côté browser.  
> Packaging : build Vite embarqué dans `src/main/resources/static/` de chaque instance
> (décision n° 27) — confirme `SameSite=Lax` sans CORS.

---

## Maquette UI de référence

La construction de l'interface doit se baser sur le mock HTML présent dans ce dossier :

- **[FlowForge - Maquettes UI.html](FlowForge%20-%20Maquettes%20UI.html)** — maquettes UI de référence (écrans, navigation, composition visuelle).

Toute implémentation Vue / OpenVue (layouts, pages, composants métier, hiérarchie visuelle) doit s'aligner sur ce mock. En cas d'écart entre la maquette et les choix techniques de cette analyse (OpenVue, BFF, etc.), la maquette prime pour le rendu et l'UX ; la stack technique documentée ici reste celle à utiliser pour l'implémenter. PrimeVue (devenu propriétaire) a été remplacé par **OpenVue**, fork communautaire MIT (PR #165) : mêmes composants et thème Aura, seuls les imports changent.

Pour faciliter la création de l'interface, s'appuyer sur les **skills spécialisés** du dépôt (à charger avant d'écrire le code UI) :

- [`flowforge-conventions`](../../.claude/skills/flowforge-conventions/SKILL.md) — conventions projet (stack FE, packaging, priorités)
- [`vue-best-practices`](../../.claude/skills/vue-best-practices/SKILL.md) — Composition API, structure des composants, patterns Vue
- [`vue`](../../.claude/skills/vue/SKILL.md) — SFC, réactivité, macros `<script setup>`
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
plus pertinent que le point ouvert n° 24 du [TODO](../../TODO.md) (statut de contrat de l'API vis-à-vis du
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
