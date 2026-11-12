// TODO §128 (D-VIDEO-PIPELINE, lot 4 de #56) — tournage des capsules vidéo contre une VRAIE scène
// (`scripts/video.sh start`), un fichier vidéo par chapitre (un test par chapitre). Hors `test:all` et hors CI.
//   CAPTURE_LANG=fr|en (défaut fr) · CAPTURE_BASE_URL (défaut http://localhost:8080) · CAPTURE_USER / CAPTURE_PASSWORD
//   FILM_OUT (défaut capture/out/film) : la voix (voice.json) y est lue, les vidéos et les coupes y sont écrites.
// Lancement complet : scripts/video.sh capsule (voix, tournage, montage).
import { defineConfig, devices } from '@playwright/test'
import { captureLang } from './capture/settings'

const lang = captureLang()

export const FILM_SIZE = { width: 1920, height: 1080 }
/** Écran filmé : 1280×720, agrandi en 1920×1080 à l'enregistrement — l'interface reste lisible en vidéo. */
export const SCREEN = { width: 1280, height: 720 }

export default defineConfig({
  testDir: './capture/film',
  testMatch: '**/*.film.ts',
  outputDir: './capture/out/.film-results',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  // Un chapitre attend de vraies relèves IMAP (jusqu'à 45 s) et l'agent IA (jusqu'à quelques minutes).
  timeout: 10 * 60_000,
  expect: { timeout: 60_000 },
  globalSetup: './capture/login.setup.ts',
  reporter: [['list']],
  use: {
    ...devices['Desktop Chrome'],
    baseURL: process.env.CAPTURE_BASE_URL ?? 'http://localhost:8080',
    locale: lang === 'en' ? 'en-GB' : 'fr-BE',
    timezoneId: 'Europe/Brussels',
    viewport: SCREEN,
    // Une action bloquée échoue en une minute, pas au bout des dix minutes du test.
    actionTimeout: 60_000,
    deviceScaleFactor: 1,
    video: { mode: 'on', size: FILM_SIZE },
    storageState: `capture/out/.auth-${lang}.json`,
    launchOptions: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE
      ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE }
      : {},
  },
})
