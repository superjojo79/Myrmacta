// TODO §127 (D-DEMO-CAPTURE, lot 3 de #55) — les screenshots du site (#58), sur la démo complète (`scripts/demo.sh`).
// S13 (portail usager) n'en fait plus partie : l'exemple autorisation riverain est retiré de la démo (issue #101). S5
// (modèle de document et aperçu PDF) est dans screenshots-pdf.capture.ts : il se prend avec Edge (TODO §140).
import { expect, test, type Page } from '@playwright/test'
import { api, DEMO_JOURNEY, demoJourney } from './film/short'
import { outDir } from './settings'

type DossierSummary = { id: string; label: string; dossierTypeCode: string; createdAt: string }

/** Les dossiers d'un type, les plus récents d'abord (session de la démo, par le BFF). */
async function dossiers(page: Page, type: string): Promise<DossierSummary[]> {
  const response = await page.request.get('/api/dossiers', { params: { dossierTypeCode: type, size: 50 } })
  expect(response.ok()).toBeTruthy()
  const content = ((await response.json()) as { content: DossierSummary[] }).content
  return [...content].sort((a, b) => b.createdAt.localeCompare(a.createdAt))
}

async function settle(page: Page): Promise<void> {
  await page.waitForLoadState('networkidle')
  // Laisse finir les transitions (graphes, diagrammes BPMN) avant la prise de vue.
  await page.waitForTimeout(600)
}

async function shot(page: Page, name: string): Promise<void> {
  await settle(page)
  await page.screenshot({ path: `${outDir()}/${name}.png` })
}

test('S1 fiche d’une facture routée par la mail room', async ({ page }) => {
  await page.goto('/supervision')
  const invoice = (await dossiers(page, 'FACTURE'))[0]!
  await page.goto(`/dossiers/${invoice.id}`)
  await shot(page, 'S01-fiche-facture')
})

test('S2 instance de processus, chemin parcouru', async ({ page }) => {
  await page.goto('/supervision')
  // Une arrivée en cours (validation des accès par l'IT) : chemin parcouru et étape courante.
  const hire = (await dossiers(page, 'ONBOARDING_COLLABORATEUR')).find((d) => d.label.includes('Sarah Dubois'))!
  await page.goto(`/dossiers/${hire.id}`)
  const viewer = page.locator('.bpmn-viewer')
  await expect(viewer.locator('.djs-container')).toBeVisible()
  await settle(page)
  await viewer.evaluate((el) => el.scrollIntoView({ block: 'start' }))
  await shot(page, 'S02-instance-processus')
})

test('S3 concepteur de parcours, avec en vignette l’éditeur BPMN et sa palette', async ({ page }) => {
  // TODO §140 (#58) : aligné sur l'issue. L'éditeur BPMN (palette métier) est photographié d'abord, puis posé en vignette
  // sur le concepteur de parcours, dont le parcours d'exemple (capsule C2) montre son aperçu compilé en BPMN.
  await page.goto('/admin/bpmn-editor?processDefinitionKey=triCourrier')
  await expect(page.locator('.djs-palette')).toBeVisible()
  await settle(page)
  const editor = (await page.locator('main').screenshot()).toString('base64')
  if (!(await page.request.get(`/api/parcours/${DEMO_JOURNEY}`)).ok()) {
    await api(page, 'PUT', `/api/parcours/${DEMO_JOURNEY}`, { ...demoJourney(), expectedVersion: null })
  }
  await page.goto('/admin/parcours')
  await page.getByTestId(`parcours-open-${DEMO_JOURNEY}`).click()
  await page.getByTestId('parcours-preview').click()
  await expect(page.getByTestId('parcours-diagram').locator('.djs-container')).toBeVisible()
  await settle(page)
  await page.getByTestId('parcours-editor').evaluate((el) => el.scrollIntoView({ block: 'start' }))
  // Vignette posée à la prise de vue seulement (produit inchangé), comme celle des capsules (D-VIDEO-PIP).
  await page.evaluate((png) => {
    const img = document.createElement('img')
    img.src = `data:image/png;base64,${png}`
    Object.assign(img.style, {
      position: 'fixed', right: '24px', bottom: '24px', width: '46%', zIndex: '2147483647', borderRadius: '12px',
      border: '3px solid #4cc9f0', boxShadow: '0 12px 36px rgba(0,0,0,.35)', background: '#fff',
    })
    document.body.appendChild(img)
  }, editor)
  await page.waitForTimeout(300)
  await shot(page, 'S03-parcours-bpmn')
})

test('S4 table de décision de routage', async ({ page }) => {
  // Le brouillon MCP du paquet « états » : la table de routage avec sa règle « imprimerie » de plus.
  await page.goto('/admin/dmn-editor?decisionKey=routageCourrier')
  await expect(page.locator('.dmn-decision-table-container, .dmn-drd-container').first()).toBeVisible()
  await shot(page, 'S04-table-routage')
})

test('S6 registre du courrier', async ({ page }) => {
  await page.goto('/supervision')
  await page.getByTestId('type-filter').selectOption('COURRIER')
  await page.locator('form.supervision__filters').getByRole('button', { name: /Filtrer|Filter/ }).click()
  await shot(page, 'S06-registre-courrier')
})

test('S7 file de tri', async ({ page }) => {
  await page.goto('/tasks')
  await shot(page, 'S07-file-de-tri')
})

test('S8 déposer un courrier', async ({ page }) => {
  await page.goto('/mail-deposit')
  await shot(page, 'S08-deposer-courrier')
})

test('S9 onboarding provisionné', async ({ page }) => {
  await page.goto('/supervision')
  const hire = (await dossiers(page, 'ONBOARDING_COLLABORATEUR')).find((d) => d.label.includes('Van den Broeck'))!
  await page.goto(`/dossiers/${hire.id}`)
  // Cadre sur le poste et les champs « Provisioning » écrits par le processus.
  await page.getByRole('heading', { name: /^(Poste|Position)$/ }).evaluate((el) => el.scrollIntoView({ block: 'start' }))
  await shot(page, 'S09-onboarding-provisionne')
})

test('S10 supervision des SLA', async ({ page }) => {
  await page.goto('/rapports')
  await shot(page, 'S10-sla')
})

test('S11 incident rejouable', async ({ page }) => {
  await page.goto('/admin/incidents')
  await shot(page, 'S11-incident')
})

test('S12 connecteurs', async ({ page }) => {
  await page.goto('/admin/connectors')
  await shot(page, 'S12-connecteurs')
})

test('S14 brouillon MCP et sa provenance', async ({ page }) => {
  await page.goto('/admin/drafts')
  await shot(page, 'S14-brouillon-mcp')
})

test.describe('mobile', () => {
  test.use({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 3, isMobile: true, hasTouch: true })

  test('S15 tâches sur mobile', async ({ page }) => {
    await page.goto('/tasks')
    await shot(page, 'S15-taches-mobile')
  })
})
