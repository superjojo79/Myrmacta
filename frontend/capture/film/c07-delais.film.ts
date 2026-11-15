// TODO §140 (D-VIDEO-SHORT, issue #58) — capsule courte C7 « Savoir où en sont les délais », sur la démo complète : le
// rapport SLA (factures antidatées du paquet « états »), puis le réglage des alertes de franchissement et de l'export BI.
// Alertes et export partent à intervalle (au plus tôt une heure pour l'export) : on montre leur réglage, pas un envoi.
import { expect, test } from '@playwright/test'
import { en, opening, plan, scrollTo, shortCapsule, underTheHood } from './short'

const capsule = shortCapsule('c07-delais')

test('ouverture', async ({ page }) => {
  await opening(capsule, page)
})

test('rapports', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'rapports')
  await d.goto('/rapports')
  // Style posé au tournage seulement : une marge sous la page, pour remonter le dernier tableau au-dessus des sous-titres.
  await page.addStyleTag({ content: 'main { padding-bottom: 360px !important; }' })
  // « 73.3 % » en français, « 73.3% » en anglais.
  const compliance = page.getByText(/73[.,]3\s?%/)
  await expect(compliance).toBeVisible()
  await d.beat(2)
  await scrollTo(page, compliance, 'center')
  await d.beat(3)
  await scrollTo(page, page.getByRole('heading', { level: 2 }).nth(2), 'start')
  await d.beat(2.5)
  // Le tableau du détail en haut de l'écran : en bas, les sous-titres le masqueraient.
  await scrollTo(page, page.locator('table').last(), 'start')
  await d.holdForVoice(0.3)
  await d.wrap(order)
})

test('alertes', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'alertes')
  await d.goto('/admin/dossier-types')
  const form = page.getByTestId('sla-settings-form')
  await scrollTo(page, page.locator('#sla-heading'), 'start')
  await d.beat(1.5)
  await form.getByTestId('sla-target-percent').fill('95')
  await form.getByTestId('sla-alerting-enabled').check()
  await d.beat(0.6)
  await form.getByTestId('sla-alert-interval').fill('60')
  await form.getByTestId('sla-alert-emails').pressSequentially(
    en ? 'operations@demo.example\nfinance@demo.example' : 'direction@demo.example\ncomptabilite@demo.example', { delay: 25 })
  await d.beat(0.8)
  await form.getByTestId('sla-bi-enabled').check()
  await form.getByTestId('sla-bi-interval').fill('24')
  await form.getByTestId('sla-bi-retention').fill('90')
  await d.beat(1.2)
  await form.getByTestId('sla-save').click()
  await expect.poll(async () => (await page.request.get('/api/tenant/sla-settings')).status()).toBe(200)
  await d.beat(1)
  await d.holdForVoice(0.3)
  await d.wrap(order)
})

test('sous-le-capot', async ({ page }) => {
  await underTheHood(capsule, page)
})
