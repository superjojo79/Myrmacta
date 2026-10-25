# Frontend Myrmacta

SPA Vue 3 + TypeScript embarquée dans `src/main/resources/static/` (décision n° 27).

## Prérequis

- Node **22+** (DD-NODE)

## Scripts

| Script | Rôle |
|---|---|
| `npm run dev` | Vite HMR |
| `npm run build` | `vue-tsc` + bundle `dist/` |
| `npm run typecheck` | Vérification TypeScript |
| `npm run lint` | ESLint |
| `npm run test` | Vitest (unitaires / composants) |
| `npm run test:coverage` | Vitest + couverture |
| `npm run test:e2e` | Playwright (preview server) — **obligatoire** avant claim Phase 8/9 FE |
| `npm run test:all` | Vitest **puis** Playwright (parité locale avec le job CI `frontend`) |

> Les mocks Vitest ne remplacent pas `frontend/e2e/**`. Tout changement de
> contrat API consommé par le SPA doit mettre à jour les `page.route` Playwright.

## Structure

```
src/
  app/ api/ stores/ views/ components/ styles/
```

Maquette UX : `docs/specs/FlowForge - Maquettes UI.html`.

## Tester contre un vrai backend en HMR

`npm run dev` sert la SPA sur son propre port Vite (ex. 5173) ; il n'y a **pas** de
`server.proxy` configuré vers l'API — toutes les requêtes `/api/...` (relatives, voir
`resolveApiBaseUrl()` dans `src/api/http.ts`) partent donc vers le serveur Vite lui-même, pas
vers Spring Boot, et échouent en 404. Deux options pour tester contre l'API réelle :

- **Build + backend seul (recommandé pour valider un parcours complet)** : `npm run build`, puis
  démarrer le backend Spring Boot normalement (`./mvnw spring-boot:run`) — il sert la SPA buildée
  depuis `static/` en même origine que l'API, sans proxy nécessaire. Pas de HMR.
- **HMR contre le backend local** : ajouter temporairement un bloc `server.proxy` dans
  `vite.config.ts` pointant `/api` vers `http://localhost:8080` (port par défaut du backend local),
  ou lancer un reverse-proxy externe devant les deux. Ne pas committer cette config — en prod, le
  SPA est servi en même origine (décision n° 27), aucun proxy applicatif n'est prévu.
