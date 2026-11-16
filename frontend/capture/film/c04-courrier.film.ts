// TODO §140 (D-VIDEO-SHORT, issue #58) — capsule courte C4 « Des courriers PDF prêts à envoyer », sur la démo complète.
// Le modèle « confirmation-arrivee » est créé à l'écran (contenu collé en source HTML, puis montré dans l'éditeur visuel),
// rendu en PDF par le serveur, puis activé et décliné en une nouvelle version.
import { expect, test } from '@playwright/test'
import { en, opening, plan, scrollTo, shortCapsule, underTheHood } from './short'

const capsule = shortCapsule('c04-courrier')

// L'aperçu PDF est une iframe : le Chromium headless de Playwright n'a pas de lecteur PDF (cadre blanc), Edge en a un.
// Edge est installé sur tout poste Windows ; son interface (barre du lecteur) suit la langue de la capsule.
test.use({ channel: 'msedge', launchOptions: { args: [`--lang=${en ? 'en-GB' : 'fr-BE'}`] } })

// Style posé au tournage seulement (produit inchangé) : la liste des versions, en bas de page, lisible en vidéo.
const LIST_STYLE = 'li:has(> .actions) { font-size: 20px; padding: 10px 14px; margin: 6px 0; background: #fff; '
  + 'border-left: 5px solid #4cc9f0; border-radius: 8px; } '
  // Marge sous la page : la liste remonte au-dessus des sous-titres incrustés.
  + 'main { padding-bottom: 360px !important; }'
const KEY = 'confirmation-arrivee'
const LABEL = en ? 'Start date confirmation' : "Confirmation d'arrivée"

// Variables Thymeleaf (${…}), comme les modèles des exemples.
const HTML = en
  ? `<h1>Welcome to the team</h1>
<p>Dear <span th:text="\${prenom}">First name</span> <span th:text="\${nom}">Last name</span>,</p>
<p>We are pleased to confirm that you start on <strong><span th:text="\${dateArrivee}">date</span></strong> as <span th:text="\${fonction}">role</span>.</p>
<p>Your manager will welcome you at 9 a.m. and give you your equipment.</p>
<p>Human Resources</p>`
  : `<h1>Bienvenue dans l'équipe</h1>
<p>Bonjour <span th:text="\${prenom}">Prénom</span> <span th:text="\${nom}">Nom</span>,</p>
<p>Nous avons le plaisir de vous confirmer votre arrivée le <strong><span th:text="\${dateArrivee}">date</span></strong> au poste de <span th:text="\${fonction}">fonction</span>.</p>
<p>Votre manager vous accueillera à 9 h et vous remettra votre matériel.</p>
<p>Le service des ressources humaines</p>`

const SAMPLE = {
  prenom: 'Camille', nom: 'Lenoir', dateArrivee: en ? '14 December 2026' : '14 décembre 2026',
  fonction: en ? 'Java developer' : 'développeuse Java',
}

test('ouverture', async ({ page }) => {
  await opening(capsule, page)
})

test('modele', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'modele')
  await d.goto('/admin/templates')
  const form = page.locator('form.admin-form')
  await d.fast(2, async () => {
    await form.locator('input').nth(0).pressSequentially(KEY, { delay: 25 })
    await form.locator('input').nth(1).pressSequentially(LABEL, { delay: 25 })
  })
  // Les valeurs d'exemple d'abord : le rendu est strict, une variable sans valeur ferait échouer l'aperçu.
  await page.locator('.preview-panel textarea').first().fill(JSON.stringify(SAMPLE, null, 2))
  // Le contenu est collé en source HTML, puis l'éditeur visuel le montre.
  await page.getByTestId('mode-source').click()
  await form.locator('.tpl-editor textarea').fill(HTML)
  await d.beat(0.8)
  await page.getByTestId('mode-visual').click()
  await d.beat(3)
  await page.getByRole('button', { name: /^(Aperçu PDF|PDF preview)$/ }).click()
  const pdf = page.locator('iframe.preview-panel__pdf')
  await expect(pdf).toBeVisible()
  await scrollTo(page, pdf, 'center')
  await d.beat(3)
  await form.getByRole('button', { name: /^(Créer|Create)$/ }).click()
  await expect(page.getByText(new RegExp(`${KEY} — `))).toBeVisible()
  await d.holdForVoice(0.3)
  await d.wrap(order)
})

test('version', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'version')
  await d.goto('/admin/templates')
  await page.addStyleTag({ content: LIST_STYLE })
  const row = () => page.locator('li').filter({ hasText: new RegExp(`${KEY} — `) })
  await scrollTo(page, row().first(), 'center')
  await d.beat(1.5)
  await row().first().getByRole('button', { name: /^(Activer|Activate)$/ }).click()
  await expect(row().filter({ hasText: /ACTIVE/ })).toBeVisible()
  await d.beat(2)
  await row().first().getByRole('button', { name: /^(Nouvelle version|New version)$/ }).click()
  await expect(row().filter({ hasText: /v2/ })).toBeVisible()
  await scrollTo(page, row().first(), 'center')
  await d.holdForVoice(0.3)
  await d.wrap(order)
})

test('sous-le-capot', async ({ page }) => {
  await underTheHood(capsule, page)
})
