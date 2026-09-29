import { defineConfig, devices } from '@playwright/test'

// TODO §72 — test de bout en bout RÉEL (aucune API simulée) : lancé par `SignatureBrowserE2eTest`
// (Java) contre un Spring Boot démarré en H2 ; ce dernier passe le lien d'invitation dans l'environnement.
export default defineConfig({
  testDir: './e2e-real',
  workers: 1,
  reporter: 'list',
  use: {
    launchOptions: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE
      ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE }
      : {},
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
