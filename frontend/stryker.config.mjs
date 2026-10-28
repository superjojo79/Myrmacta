// TODO §108 — tests de mutation du frontend (StrykerJS + Vitest), hors `npm run test:all`.
// `npm run test:mutation` : tout le périmètre, seuil `break` appliqué (run hebdomadaire de .github/workflows/mutation.yml).
// Sur une PR : `STRYKER_NO_BREAK=1 npx stryker run --ignoreStatic --mutate <fichiers>` (scripts/mutation-targets-frontend.sh),
// sans seuil. `--ignoreStatic` : sur un run ciblé, un mutant statique (constante exécutée au chargement du module)
// recharge tout le module, dépasse le délai et compterait comme tué ; ignoré, ou « survivant » s'il est aussi couvert
// test par test (faux survivant visible plutôt que faux tué). Le run complet les garde : 78 sur 1 686 le 2026-10-28.
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
  // Cliquet : score mesuré (55,16 % le 2026-10-28, 1 686 mutants, 28 min sur 4 cœurs) moins 2 points, arrondi par
  // défaut ; à remonter quand le score progresse.
  thresholds: { high: 80, low: 60, break: process.env.STRYKER_NO_BREAK ? null : 53 },
  reporters: ['clear-text', 'progress', 'html', 'json'],
  htmlReporter: { fileName: 'reports/mutation/index.html' },
  jsonReporter: { fileName: 'reports/mutation/mutation.json' },
}
