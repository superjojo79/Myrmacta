// TODO §140 (#58) — screenshot S5 : le modèle de lettre du paquet « états » (accusé de réception de facture) à côté de son
// aperçu PDF. À part : l'aperçu PDF est une iframe, et seul Edge a un lecteur PDF en headless (même raison que la capsule
// C4). Une nouvelle version (brouillon) du modèle est ouverte en édition : l'aperçu montre son contenu réel.
import { expect, test } from '@playwright/test'
import { captureLang, outDir } from './settings'

const en = captureLang() === 'en'
const LETTER = 'accuse-facture'
const SAMPLE = { numeroFacture: '2026-117', dateReception: '11/11/2026', montant: en ? '1,250.00 EUR' : '1 250,00 EUR', delaiJours: 30 }

// Fenêtre haute : l'aperçu PDF (en haut) et le contenu en édition (en dessous) tiennent dans une capture normale ; une
// capture « page entière » ne fixe pas le rendu du lecteur PDF.
test.use({ channel: 'msedge', launchOptions: { args: [`--lang=${en ? 'en-GB' : 'fr-BE'}`] }, viewport: { width: 1440, height: 1800 } })

test('S5 modèle de document et son aperçu PDF', async ({ page, browser }) => {
  await page.goto('/admin/templates')
  const rows = page.locator('li').filter({ hasText: new RegExp(`${LETTER} — `) })
  await expect(rows.first()).toBeVisible()
  if (!(await rows.filter({ hasText: /DRAFT/ }).count())) {
    await rows.first().getByRole('button', { name: /^(Nouvelle version|New version)$/ }).click()
    await expect(rows.filter({ hasText: /DRAFT/ })).toBeVisible()
  }
  await rows.filter({ hasText: /DRAFT/ }).getByTestId('edit-content').click()
  await expect(page.getByTestId('template-edit')).toBeVisible()
  await page.locator('.preview-panel textarea').first().fill(JSON.stringify(SAMPLE, null, 2))
  await page.getByRole('button', { name: /^(Aperçu PDF|PDF preview)$/ }).click()
  await expect(page.locator('iframe.preview-panel__pdf')).toBeVisible()
  await page.waitForLoadState('networkidle')
  // Le lecteur PDF d'une iframe ne sort pas dans une capture d'écran (cadre gris) : le PDF rendu par le serveur est
  // photographié seul, dans son propre onglet, puis posé à la place de l'iframe (comme la vignette de S3, produit inchangé).
  const pdf = page.locator('iframe.preview-panel__pdf')
  // Le même rendu que l'aperçu, demandé au serveur avec le contenu du brouillon (lu dans « Source HTML ») et les mêmes
  // valeurs : l'URL blob de l'iframe n'est pas relisible depuis la page (CSP).
  const edit = page.getByTestId('template-edit')
  await edit.getByTestId('mode-source').click()
  const htmlContent = await edit.locator('textarea').inputValue()
  await edit.getByTestId('mode-visual').click()
  const xsrf = (await page.context().cookies()).find((c) => c.name === 'XSRF-TOKEN')?.value ?? ''
  const response = await page.request.post('/api/documents/preview/pdf', {
    headers: { 'X-XSRF-TOKEN': xsrf }, data: { htmlContent, variables: SAMPLE },
  })
  expect(response.ok(), `preview PDF → ${response.status()}`).toBeTruthy()
  const bytes = (await response.body()).toString('base64')
  // Contexte à part, à l'échelle 1 : à l'échelle 2, la capture ne prend que la moitié gauche du lecteur PDF.
  const viewer = await browser.newContext({ viewport: { width: 1000, height: 1300 }, deviceScaleFactor: 1 })
  const tab = await viewer.newPage()
  await tab.route('**/film-preview.pdf', (route) =>
    route.fulfill({ contentType: 'application/pdf', body: Buffer.from(bytes, 'base64') }))
  await tab.goto('http://film.localhost/film-preview.pdf#toolbar=0')
  await tab.waitForTimeout(2500)
  // « Ajuster à la page » (Ctrl+\) : le lecteur d'Edge ignore le paramètre view=Fit de l'URL.
  await tab.mouse.click(500, 650)
  await tab.keyboard.press('Control+Backslash')
  await tab.waitForTimeout(1500)
  const rendered = (await tab.screenshot()).toString('base64')
  await viewer.close()
  await pdf.evaluate((frame, png) => {
    const img = document.createElement('img')
    img.src = `data:image/png;base64,${png}`
    const box = frame.getBoundingClientRect()
    Object.assign(img.style, { width: `${box.width}px`, height: `${box.height}px`, objectFit: 'contain', background: '#525659' })
    frame.replaceWith(img)
  }, rendered)
  await page.waitForTimeout(500)
  await page.screenshot({ path: `${outDir()}/S05-modele-document.png` })
})
