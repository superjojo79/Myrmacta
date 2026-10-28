// TODO §108 — tests de mutation du frontend (StrykerJS + Vitest), hors `npm run test:all`.
// `npm run test:mutation` : tout le périmètre, seuil `break` appliqué (run hebdomadaire de .github/workflows/mutation.yml).
// Sur une PR : `STRYKER_NO_BREAK=1 npx stryker run --mutate <fichiers>` (scripts/mutation-targets-frontend.sh), sans seuil.
// Rapport : reports/mutation/index.html.

/**
 * Périmètre : la logique sans rendu (stores, composables, client d'API, portail). Les composants `.vue` et la logique
 * BPMN de `src/components` coûtent trop cher à muter (plus de 9 h mesurées). scripts/mutation-targets-frontend.sh lit
 * cette ligne : la garder sur une seule ligne.
 */
const scope = ['src/stores', 'src/composables', 'src/api', 'portal']

/** @type {import('@stryker-mutator/api/core').PartialStrykerOptions} */
export default {
  testRunner: 'vitest',
  plugins: ['@stryker-mutator/vitest-runner'],
  vitest: { configFile: 'vite.config.ts' },
  coverageAnalysis: 'perTest',
  mutate: [
    ...scope.map((dir) => `${dir}/**/*.ts`),
    '!**/*.spec.ts',
    '!**/*.test.ts',
    '!**/*.d.ts',
    '!portal/main.ts',
  ],
  ignorePatterns: ['dist', 'coverage', 'playwright-report', 'test-results'],
  // Cliquet : score mesuré moins 2 points, arrondi par défaut ; à remonter quand le score progresse.
  thresholds: { high: 80, low: 60, break: process.env.STRYKER_NO_BREAK ? null : 0 },
  reporters: ['clear-text', 'progress', 'html', 'json'],
  htmlReporter: { fileName: 'reports/mutation/index.html' },
  jsonReporter: { fileName: 'reports/mutation/mutation.json' },
}
