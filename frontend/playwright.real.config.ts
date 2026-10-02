import { defineConfig, devices } from '@playwright/test'

// TODO §72 — test de bout en bout RÉEL (aucune API simulée) : lancé par `SignatureBrowserE2eTest`
// (Java) contre un Spring Boot démarré en H2 ; ce dernier passe le lien d'invitation dans l'environnement.
export default defineConfig({
  testDir: './e2e-real',
  workers: 1,
  reporter: 'list',
  use: {
    // L'interface suit la langue du navigateur (D7) : le test attend les libellés français, quelle que soit la machine (CI en en-US).
    locale: 'fr-FR',
    launchOptions: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE
      ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE }
      : {},
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
