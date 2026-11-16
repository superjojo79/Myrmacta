// TODO §140 (D-VIDEO-SHORT, issue #58) — capsule courte C10 « Un assistant IA qui travaille sous contrôle », sur la démo
// complète : le brouillon MCP de la table de routage (paquet « états », agent « demo-etats ») est relu, publié par une
// personne, puis la règle de l'imprimerie apparaît dans la table en ligne.
import { expect, test } from '@playwright/test'
import { opening, plan, scrollTo, shortCapsule, underTheHood } from './short'

const capsule = shortCapsule('c10-brouillons')
const KEY = 'routageCourrier'

// Style posé au tournage seulement (produit inchangé) : la ligne du brouillon, lisible en vidéo.
const ROW_STYLE = `[data-testid="draft-row-${KEY}"] td { background: #fff8db; font-size: 17px; }`

test('ouverture', async ({ page }) => {
  await opening(capsule, page)
})

test('brouillon', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'brouillon')
  await d.goto('/admin/drafts')
  await page.addStyleTag({ content: ROW_STYLE })
  await expect(page.getByTestId(`draft-row-${KEY}`)).toBeVisible()
  await scrollTo(page, page.getByTestId(`draft-row-${KEY}`), 'center')
  await d.holdForVoice(0.3)
  await d.wrap(order)
})

test('publication', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'publication')
  await d.goto('/admin/drafts')
  await page.addStyleTag({ content: ROW_STYLE })
  await d.beat(1.2)
  // La confirmation (fenêtre native, absente de la vidéo) est acceptée.
  page.once('dialog', (dialog) => dialog.accept())
  await page.getByTestId(`draft-row-${KEY}`).getByTestId('draft-publish').click()
  await expect(page.getByTestId('drafts-notice')).toBeVisible()
  const log = page.getByTestId('publications-log')
  // TODO §145 / #112 — le journal montre le nom (annuaire), pas le sujet OIDC.
  await expect(log).not.toContainText(/publié par [0-9a-f]{8}-[0-9a-f]{4}-/i)
  await scrollTo(page, log, 'center')
  await d.beat(2.5)
  await d.goto(`/admin/dmn-editor?decisionKey=${KEY}`)
  await expect(page.locator('.dmn-decision-table-container').first()).toContainText('imprimerie.example')
  await d.beat(1)
  await d.holdForVoice(0.3)
  await d.wrap(order)
})

test('sous-le-capot', async ({ page }) => {
  await underTheHood(capsule, page)
})
