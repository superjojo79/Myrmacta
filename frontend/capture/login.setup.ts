// TODO §127 (D-DEMO-CAPTURE) — connexion unique au Keycloak local de la démo (BFF : la session vit dans un cookie),
// mémorisée pour toutes les captures d'une langue. Compte du realm de développement (docs/guides/GETTING-STARTED.md §4).
import { chromium, type FullConfig } from '@playwright/test'
import { captureLang } from './settings'

export default async function login(config: FullConfig): Promise<void> {
  const use = config.projects[0]!.use
  const baseURL = use.baseURL ?? 'http://localhost:8080'
  const browser = await chromium.launch(use.launchOptions)
  const page = await browser.newPage({ locale: use.locale })
  try {
    await page.goto(baseURL)
    await page.locator('#username').fill(process.env.CAPTURE_USER ?? 'demo')
    await page.locator('#password').fill(process.env.CAPTURE_PASSWORD ?? 'demo')
    await page.locator('#kc-login').click()
    await page.waitForURL((url) => url.toString().startsWith(baseURL), { timeout: 30_000 })
    await page.context().storageState({ path: `capture/out/.auth-${captureLang()}.json` })
  } finally {
    await browser.close()
  }
}
