// TODO §127 (D-DEMO-CAPTURE, lot 3 de #55) — captures du site (screenshots, puis vidéos des capsules) contre une VRAIE
// instance de démo (`scripts/demo.sh start`), pas le backend simulé des tests e2e. Hors `test:all` et hors CI.
//   CAPTURE_LANG=fr|en (défaut fr) · CAPTURE_BASE_URL (défaut http://localhost:8080)
//   CAPTURE_USER / CAPTURE_PASSWORD : compte Keycloak local de la démo (défaut demo / demo, realm de développement)
// Sortie : capture/out/<langue>/ (non versionnée).
import { defineConfig, devices } from '@playwright/test'
import { captureLang } from './capture/settings'

const lang = captureLang()

export default defineConfig({
  testDir: './capture',
  testMatch: '**/*.capture.ts',
  outputDir: './capture/out/.results',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60_000,
  // Les éditeurs BPMN et DMN se chargent à la demande : à froid, leur affichage dépasse les 5 s par défaut.
  expect: { timeout: 15_000 },
  globalSetup: './capture/login.setup.ts',
  reporter: [['list']],
  use: {
    ...devices['Desktop Chrome'],
    baseURL: process.env.CAPTURE_BASE_URL ?? 'http://localhost:8080',
    locale: lang === 'en' ? 'en-GB' : 'fr-BE',
    timezoneId: 'Europe/Brussels',
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 2,
    storageState: `capture/out/.auth-${lang}.json`,
    launchOptions: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE
      ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE }
      : {},
  },
})
