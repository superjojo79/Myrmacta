# Frontend — Vue 3 + PrimeVue

> SPA consommée depuis la même origine que l'API Spring Boot (pattern BFF).  
> Aucun token JWT manipulé côté browser.  
> Packaging : build Vite embarqué dans `src/main/resources/static/` de chaque instance
> (décision n° 27) — confirme `SameSite=Lax` sans CORS.

---

**Choix retenu : Vue 3 (Vite) + PrimeVue + Pinia**

| Composant | Rôle |
|---|---|
| Vue 3 | Framework SPA |
| Vite | Bundler / HMR (dev) ; build de production copié dans `static/` |
| PrimeVue | Librairie de composants UI métier (DataTable, formulaires, timeline…) |
| Pinia | State management (remplaçant officiel Vuex) |
| Axios | Client HTTP — appels relatifs vers l'API Spring Boot (même origine) |

### Packaging — frontend embarqué

Le frontend n'est **pas** déployé séparément. Le pipeline de build :

1. `npm run build` produit le bundle dans `frontend/dist/` ;
2. le contenu est copié (Maven `frontend-maven-plugin` ou étape CI) vers
   `src/main/resources/static/` ;
3. chaque instance Spring Boot dédiée à un tenant (décision n° 13) sert à la fois l'API/BFF et les
   assets statiques sous son `server.servlet.context-path` (`/{tenantId}/`).

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

```javascript
// stores/auth.js
export const useAuthStore = defineStore('auth', {
    state: () => ({ user: null, authenticated: false }),
    actions: {
        async init() {
            try {
                const { data } = await axios.get('/api/auth/me',
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
npm create vite@latest frontend -- --template vue
npm install primevue @primevue/themes primeicons
npm install pinia axios bpmn-js
```
