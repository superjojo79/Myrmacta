// TODO §143 (D-VIDEO-SHORT, D-VIDEO-RIVERAIN-SCENE, issue #101) — capsule courte C5 « L'usager complète sa demande sans
// compte », sur la scène riverain (`scripts/video.sh start c05-portail`). Une demande incomplète est encodée hors champ ;
// l'agent demande une pièce ; le lien personnel arrive dans Mailpit ; le demandeur répond sur un téléphone, sans session ;
// la réponse revient sur la fiche. Le plan « portail » produit aussi le screenshot S13.
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { expect, test, type Browser, type Page } from '@playwright/test'
import { filmDir, pdf, REPO, until } from './director'
import { api, en, opening, plan, scrollTo, shortCapsule, underTheHood } from './short'
import { outDir } from '../settings'

const capsule = shortCapsule('c05-portail')
const TYPE = 'AUTORISATION_RIVERAIN'
const PROCESS = 'autorisationRiverain'
const MAILPIT = process.env.FILM_MAILPIT ?? 'http://localhost:8025'
const PHONE = { width: 390, height: 844 }
const example = resolve(REPO, 'exemples/autorisation-riverain')
const sample = JSON.parse(readFileSync(resolve(example, 'config/exemple-dossier.json'), 'utf-8')) as {
  label: string
  fields: Record<string, string>
}
const questions = JSON.parse(readFileSync(resolve(example, en ? 'en/config' : 'config', 'questions.json'), 'utf-8')) as {
  fields: { name: string; label: string }[]
}[]
function questionLabel(name: string): RegExp {
  const found = questions.flatMap((s) => s.fields).find((f) => f.name === name)!
  return new RegExp(`^${found.label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}( \\*)?(\\s|$)`)
}
// La demande de l'exemple ; en anglais, son libellé et sa zone sont traduits (le reste est une adresse bruxelloise).
const LABEL = en ? 'Resident parking permit — Dupont — 1-ABC-123' : sample.label
const FIELDS = { ...sample.fields, ...(en ? { zoneRiverain: 'Tilleuls resident zone' } : {}) }
const REQUEST = en ? 'Please attach a recent proof of address.' : 'Merci de joindre une preuve de domicile récente.'
const ANSWER = en ? 'Here is my latest electricity bill.' : 'Voici ma dernière facture d\'électricité.'
const BILL = 'SIBELGA - Facture du 03/10/2026 - Camille Dupont, Rue des Tilleuls 12, 1000 Bruxelles'

type Dossier = { id: string; label: string; version: number }

async function dossier(page: Page): Promise<Dossier> {
  return until(async () => {
    const list = await api<{ content: Dossier[] }>(page, 'GET', `/api/dossiers?dossierTypeCode=${TYPE}&size=20`)
    return list.content.find((d) => d.label === LABEL)
  })
}

/** Le dernier lien du portail reçu par le demandeur (Mailpit), après `since` messages. */
async function portalLink(since: number): Promise<{ id: string; link: string }> {
  return until(async () => {
    const list = await (await fetch(`${MAILPIT}/api/v1/messages?limit=5`)).json() as { total: number; messages: { ID: string }[] }
    if (list.total <= since) return undefined
    const message = await (await fetch(`${MAILPIT}/api/v1/message/${list.messages[0]!.ID}`)).json() as { ID: string; Text: string }
    // En anglais, le lien porte la langue avant le jeton : …/portal/?lang=en#<jeton>.
    const link = message.Text.match(/https?:\/\/\S+\/portal\/\S*#\S+/)?.[0]
    return link ? { id: message.ID, link } : undefined
  }, 90_000)
}

async function mailCount(): Promise<number> {
  return ((await (await fetch(`${MAILPIT}/api/v1/messages?limit=1`)).json()) as { total: number }).total
}

let link = ''

test('ouverture', async ({ page }) => {
  await opening(capsule, page)
})

test('instruction', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'instruction')
  await d.goto('/supervision')
  // Hors champ : la boîte de Mailpit (locale, de la scène) est vidée, pour que le plan « lien » ne montre que ce message.
  await fetch(`${MAILPIT}/api/v1/messages`, { method: 'DELETE' })
  // Hors champ : la demande de l'exemple, encodée, ouverte, son workflow démarré.
  const created = await api<Dossier>(page, 'POST', '/api/dossiers', { dossierTypeCode: TYPE, label: LABEL, fields: FIELDS })
  const opened = await api<Dossier>(page, 'PATCH', `/api/dossiers/${created.id}/status`, { status: 'OUVERT', expectedVersion: created.version })
  await api(page, 'POST', `/api/dossiers/${opened.id}/workflows`, { processDefinitionKey: PROCESS, variables: {} },
    { 'Idempotency-Key': `film-${opened.id}` })
  const since = await mailCount()
  await d.offscreen(async () => {
    await until(async () => (await page.request.get(`/api/dossiers/${opened.id}/workflows?size=5`)).ok())
    await page.waitForTimeout(2000)
  })
  await d.goto(`/dossiers/${opened.id}`)
  await d.beat(1.5)
  const tasks = page.locator('.task-list').first()
  await scrollTo(page, tasks, 'center')
  await d.beat(1)
  await tasks.getByLabel(questionLabel('decisionAgent')).selectOption('DEMANDER_INFOS')
  await d.beat(0.6)
  await tasks.getByLabel(questionLabel('motivation')).pressSequentially(REQUEST, { delay: 30 })
  await d.beat(0.8)
  const complete = tasks.getByRole('button', { name: /^(Compléter|Complete)$/ }).first()
  await scrollTo(page, complete, 'center')
  await complete.click()
  await d.beat(1.2)
  await d.holdForVoice(0.3)
  await d.wrap(order)
  link = (await portalLink(since)).link
})

test('lien', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'lien')
  const message = await portalLink(0)
  link = link || message.link
  await d.goto(`${MAILPIT}/view/${message.id}`)
  await d.beat(2)
  await d.holdForVoice(0.6)
  await d.wrap(order)
})

/** Le portail, sans session, à la taille d'un téléphone ; `record` filme le plan. */
async function phone(browser: Browser, locale: string | undefined, record: boolean, scale = 1) {
  return browser.newContext({
    locale, viewport: PHONE, isMobile: true, hasTouch: true, deviceScaleFactor: scale,
    ...(record ? { recordVideo: { dir: resolve(filmDir(), 'video', '.mobile'), size: PHONE } } : {}),
  })
}

test('portail', async ({ browser, locale }) => {
  // S13 d'abord : le même lien, en haute densité, avant l'envoi de la réponse (le lien sert jusqu'à l'envoi).
  const still = await phone(browser, locale, false, 3)
  const shot = await still.newPage()
  await shot.goto(link)
  await expect(shot.getByRole('button', { name: /^(Envoyer ma réponse|Send my reply)$/ })).toBeVisible()
  await shot.waitForTimeout(800)
  await shot.screenshot({ path: resolve(REPO, 'frontend', outDir(), 'S13-portail-mobile.png') })
  await still.close()

  const context = await phone(browser, locale, true)
  const page = await context.newPage()
  const { director: d, order } = plan(capsule, page, 'portail')
  await d.goto(link)
  await d.beat(2)
  await page.locator('textarea, input[type="text"]').first().pressSequentially(ANSWER, { delay: 35 })
  await d.beat(0.8)
  await page.locator('#portal-file').setInputFiles({
    name: en ? 'proof-of-address.pdf' : 'preuve-domicile.pdf', mimeType: 'application/pdf', buffer: pdf(BILL),
  })
  await page.locator('#portal-file-label').fill(en ? 'Proof of address' : 'Preuve de domicile')
  await page.getByRole('button', { name: /^(Déposer le fichier|Upload the file)$/ }).click()
  await expect(page.getByTestId('portal-attachments')).toContainText(en ? 'Proof of address' : 'Preuve de domicile')
  await d.beat(1.2)
  const send = page.getByRole('button', { name: /^(Envoyer ma réponse|Send my reply)$/ })
  await scrollTo(page, send, 'center')
  await send.click()
  await expect(page.getByTestId('portal-done')).toBeVisible()
  await d.beat(1.5)
  await d.holdForVoice(0.6)
  await d.wrap(order)
  await context.close()
})

test('retour', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'retour')
  const found = await dossier(page)
  await d.goto(`/dossiers/${found.id}`)
  await d.beat(1.5)
  // La pièce reçue est un élément du dossier (les champs « Pièces justificatives » du formulaire restent vides).
  await scrollTo(page, page.getByRole('heading', { name: /^(Éléments|Elements)$/ }), 'start')
  await d.beat(2.5)
  await scrollTo(page, page.locator('.task-list').first(), 'center')
  await d.holdForVoice(0.6)
  await d.wrap(order)
})

test('sous-le-capot', async ({ page }) => {
  await underTheHood(capsule, page)
})
