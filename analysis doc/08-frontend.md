# Frontend — Vue 3 + PrimeVue

> SPA consommant l'API Spring Boot via cookie de session httpOnly.  
> Aucun token JWT manipulé côté browser (pattern BFF).

---

**Choix retenu : Vue 3 (Vite) + PrimeVue + Pinia**

| Composant | Rôle |
|---|---|
| Vue 3 | Framework SPA |
| Vite | Bundler / HMR |
| PrimeVue | Librairie de composants UI métier (DataTable, formulaires, timeline…) |
| Pinia | State management (remplaçant officiel Vuex) |
| Axios | Client HTTP — appels vers l'API Spring Boot |

### Alternatives écartées

- **React** — viable, mais Vue 3 plus adapté aux apps métier formulaires/tableaux avec moins de verbosité
- **Thymeleaf SSR / htmx** — insuffisant pour une application riche avec designer BPMN et preview documentaire

---

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

## Dépendances npm

```bash
npm create vite@latest mon-app -- --template vue
npm install primevue @primevue/themes primeicons
npm install pinia axios
```
