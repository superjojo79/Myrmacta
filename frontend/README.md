# Frontend FlowForge

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
| `npm run test` | Vitest |
| `npm run test:coverage` | Vitest + couverture |
| `npm run test:e2e` | Playwright (preview server) |

## Structure

```
src/
  app/ api/ stores/ views/ components/ styles/
```

Maquette UX : `analysis doc/FlowForge - Maquettes UI.html`.
