// TODO §140 (D-VIDEO-SHORT, issue #58) — capsule courte C2 « Concevoir un flux sans développer », sur la démo complète.
// Trois plans : un parcours compilé en BPMN, l'éditeur BPMN et sa palette de briques, la table de routage testée puis
// restaurée. Préparés hors champ par l'API : le parcours « demandeAcces » et une version 2 de la table au délai fautif.
// Le brouillon MCP de la table (paquet « états ») n'est pas touché : la capsule C10 le publie.
import { expect, test, type Page } from '@playwright/test'
import { api, DEMO_JOURNEY as PARCOURS, demoJourney, en, opening, plan, scrollTo, shortCapsule, underTheHood } from './short'

const capsule = shortCapsule('c02-flux')
const DECISION = 'delaiTraitement'
const CASE = { typeDossier: 'FACTURE', montant: 450 }

/**
 * La table de la capsule, à elle seule : déployer une table retire son brouillon, et celui de la table de routage
 * (brouillon MCP du paquet « états ») est publié par C10. Version 1 : 30 jours sous 1 000 €, 15 au-delà ; la version 2
 * passe par erreur le premier délai à 10 jours.
 */
function decisionTable(firstDeadline: number): string {
  const t = (fr: string, english: string) => (en ? english : fr)
  const rule = (id: string, amount: string, days: number, note: string) => `
      <rule id="${id}">
        <inputEntry id="${id}Type"><text>"FACTURE"</text></inputEntry>
        <inputEntry id="${id}Amount"><text>${amount}</text></inputEntry>
        <outputEntry id="${id}Days"><text>${days}</text></outputEntry>
        <annotationEntry><text>${note}</text></annotationEntry>
      </rule>`
  return `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" id="delaiTraitementDefinitions" name="${t('Délai de traitement', 'Processing deadline')}" namespace="https://myrmacta.be/demo">
  <decision id="${DECISION}" name="${t("Délai de traitement d'une facture", 'Invoice processing deadline')}">
    <decisionTable id="delaiTraitementTable" hitPolicy="FIRST">
      <input id="inputType" label="${t('Type de dossier', 'Case type')}">
        <inputExpression id="inputTypeExpression" typeRef="string"><text>typeDossier</text></inputExpression>
      </input>
      <input id="inputAmount" label="${t('Montant (€)', 'Amount (€)')}">
        <inputExpression id="inputAmountExpression" typeRef="number"><text>montant</text></inputExpression>
      </input>
      <output id="outputDays" label="${t('Délai (jours)', 'Deadline (days)')}" name="delaiJours" typeRef="number" />${
  rule('ruleSmall', '&lt; 1000', firstDeadline, t('Petite facture', 'Small invoice'))}${
  rule('ruleLarge', '&gt;= 1000', 15, t('Grosse facture : traitée plus vite', 'Large invoice: handled faster'))}
    </decisionTable>
  </decision>
</definitions>
`
}

async function deploy(page: Page, xml: string): Promise<void> {
  const xsrf = (await page.context().cookies()).find((c) => c.name === 'XSRF-TOKEN')?.value ?? ''
  const response = await page.request.post('/api/dmn-decision-deployments', {
    headers: { 'X-XSRF-TOKEN': xsrf },
    multipart: { file: { name: 'delai-traitement.dmn', mimeType: 'application/xml', buffer: Buffer.from(xml, 'utf-8') } },
  })
  expect(response.ok(), `DMN deploy → ${response.status()} ${await response.text()}`).toBeTruthy()
}

/** Hors champ : la version 1 (si la table n'existe pas encore), puis la version au délai fautif. */
async function deployFaultyVersion(page: Page): Promise<void> {
  const versions = await page.request.get(`/api/dmn-decision-deployments/${DECISION}/versions`)
  const known = versions.ok() ? ((await versions.json()) as unknown[]).length : 0
  if (known === 0) await deploy(page, decisionTable(30))
  await deploy(page, decisionTable(10))
}

async function evaluate(page: Page, deadline: number): Promise<void> {
  await page.locator('.dmn-editor__evaluate').click()
  await expect(page.locator('.dmn-editor__rows')).toContainText(`"delaiJours":${deadline}`)
}

// Style posé au tournage seulement (produit inchangé, comme la vignette de L2) : le résultat du test, lisible en vidéo.
const RESULT_STYLE = '.dmn-editor__rows { white-space: pre-wrap; font-size: 22px; font-weight: 600; padding: 8px 12px; '
  + 'background: #e7f5ff; border-left: 5px solid #4cc9f0; }'

test('ouverture', async ({ page }) => {
  await opening(capsule, page)
})

test('parcours', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'parcours')
  await d.goto('/admin/parcours')
  const existing = await page.request.get(`/api/parcours/${PARCOURS}`)
  if (!existing.ok()) {
    await api(page, 'PUT', `/api/parcours/${PARCOURS}`, { ...demoJourney(), expectedVersion: null })
    await d.reload()
  }
  await d.beat(1.2)
  await page.getByTestId(`parcours-open-${PARCOURS}`).click()
  await expect(page.getByTestId('parcours-editor')).toBeVisible()
  await d.beat(2.5)
  await scrollTo(page, page.getByTestId('parcours-step').nth(1), 'start')
  await d.beat(2.5)
  await scrollTo(page, page.getByTestId('parcours-step').nth(3), 'start')
  await d.beat(2)
  await page.getByTestId('parcours-preview').click()
  const diagram = page.getByTestId('parcours-diagram')
  await expect(diagram.locator('.djs-container')).toBeVisible()
  await page.waitForTimeout(800)
  await scrollTo(page, diagram, 'center')
  await d.holdForVoice(0.3)
  await d.wrap(order)
})

test('bpmn', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'bpmn')
  await d.goto('/admin/bpmn-editor?processDefinitionKey=triCourrier')
  await expect(page.locator('.djs-palette')).toBeVisible()
  await d.beat(3.5)
  // Une brique « notifier » : son panneau de propriétés montre ses réglages.
  await page.locator('.djs-element[data-element-id="mailAccuse"]').first().click()
  await d.beat(1)
  await d.holdForVoice(0.3)
  await d.wrap(order)
})

test('dmn', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'dmn')
  await d.goto('/admin/dmn-editor')
  await deployFaultyVersion(page)
  await d.goto(`/admin/dmn-editor?decisionKey=${DECISION}`)
  await page.addStyleTag({ content: RESULT_STYLE })
  await expect(page.locator('.dmn-decision-table-container').first()).toBeVisible()
  await d.beat(2.5)
  await page.getByRole('button', { name: /^(Tester|Test)$/ }).click()
  await page.locator('.dmn-editor__inputs').fill(JSON.stringify(CASE, null, 2))
  await scrollTo(page, page.locator('.dmn-editor__tester-panel'), 'center')
  await d.beat(1)
  await evaluate(page, 10)
  await scrollTo(page, page.locator('.dmn-editor__rows'), 'center')
  await d.beat(2.5)
  await page.getByRole('button', { name: /^(Historique|History)$/ }).click()
  const panel = page.locator('.dmn-editor__history-panel')
  await scrollTo(page, panel, 'center')
  await d.beat(1)
  await panel.getByRole('button', { name: /^(Redéployer la version|Redeploy version) 1$/ }).click()
  await d.beat(0.8)
  await panel.locator('.dmn-editor__confirm-redeploy').click()
  await expect(panel.locator('.dmn-editor__success')).toBeVisible()
  await d.beat(1.5)
  await scrollTo(page, page.locator('.dmn-editor__tester-panel'), 'center')
  await evaluate(page, 30)
  await scrollTo(page, page.locator('.dmn-editor__rows'), 'center')
  await d.beat(2)
  // La table rechargée : la version restaurée, au délai de 30 jours.
  await d.reload()
  await expect(page.locator('.dmn-decision-table-container').first()).toContainText('30')
  await d.beat(1.5)
  await d.holdForVoice(0.3)
  await d.wrap(order)
})

test('sous-le-capot', async ({ page }) => {
  await underTheHood(capsule, page)
})
