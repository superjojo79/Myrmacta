// TODO §128 (D-VIDEO-PIPELINE, D-VIDEO-MAIL, D-VIDEO-AGENT, D-VIDEO-DEADLINE ; lot 4 de #56) — tournage de la capsule L1
// « Digital Mail Room », un test par chapitre, contre la scène `scripts/video.sh start` (profil demo-video-mailroom,
// GreenMail et Mailpit démarrés). Les chapitres se suivent : le premier envoie la facture que les suivants retrouvent.
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import { expect, test, type Page } from '@playwright/test'
import { Director, lang, pdf, REPO, runAgent, sendMail, until } from './director'

type Text = { fr: string; en: string }
type Chapter = { id: string; title: Text; underTheHood?: Text; card?: Text }
const capsule = JSON.parse(readFileSync(resolve(REPO, 'scripts/video/capsules/l1-mailroom.json'), 'utf-8')) as {
  title: Text
  chapters: Chapter[]
}
const chapter = (id: string): Chapter => capsule.chapters.find((c) => c.id === id)!
const order = (id: string): number => capsule.chapters.findIndex((c) => c.id === id)
const en = lang === 'en'
const MAILPIT = 'http://localhost:8025'
const invoiceLabel = en ? 'Invoice 2026-117' : 'Facture 2026-117'

type DossierSummary = { id: string; label: string; reference: string; createdAt: string }

async function dossiers(page: Page, type: string): Promise<DossierSummary[]> {
  const response = await page.request.get('/api/dossiers', { params: { dossierTypeCode: type, size: 50 } })
  expect(response.ok()).toBeTruthy()
  const content = ((await response.json()) as { content: DossierSummary[] }).content
  return [...content].sort((a, b) => b.createdAt.localeCompare(a.createdAt))
}

type MyTask = { name: string; dossierId: string }

/** Les tâches de tri ouvertes (« Mes tâches » du compte de démo, membre de COURRIER). */
async function sortingTasks(page: Page): Promise<MyTask[]> {
  const response = await page.request.get('/api/workflow-tasks/mine', { params: { size: 100 } })
  expect(response.ok()).toBeTruthy()
  return ((await response.json()) as { content: MyTask[] }).content.filter((t) => /Trier|Sort/.test(t.name))
}

async function invoice(page: Page, label = invoiceLabel): Promise<DossierSummary> {
  return until(async () => (await dossiers(page, 'FACTURE')).find((d) => d.label === label))
}

function card(kicker: string, title: string, subtitle = '', logo = false): string {
  const url = pathToFileURL(resolve(REPO, 'frontend/capture/panel/card.html'))
  url.search = new URLSearchParams({ kicker, title, subtitle, lang, logo: logo ? '1' : '0' }).toString()
  return url.toString()
}

/** Le carton d'un chapitre : « Chapitre N » en sur-titre, son titre en grand. */
function chapterCard(id: string, subtitle = ''): string {
  return card(`${en ? 'Chapter' : 'Chapitre'} ${order(id)}`, chapter(id).title[lang], subtitle)
}

async function scrollTo(page: Page, selector: string): Promise<void> {
  await page.locator(selector).first().evaluate((el) => el.scrollIntoView({ behavior: 'smooth', block: 'start' }))
  await page.waitForTimeout(900)
}

async function mailRegister(d: Director): Promise<void> {
  await d.goto('/supervision')
  await d.page.getByTestId('type-filter').selectOption('COURRIER')
  await d.page.locator('form.supervision__filters').getByRole('button', { name: /Filtrer|Filter/ }).click()
  await d.settle()
}

test.describe.configure({ mode: 'serial' })

test('intro', async ({ page }) => {
  const d = new Director(page, 'intro')
  await page.goto(card('Myrmacta', capsule.title[lang], 'Digital Mail Room', true))
  await d.holdForVoice()
  await d.wrap(order('intro'))
})

test('facture', async ({ page }) => {
  const d = new Director(page, 'facture')
  await page.request.delete(`${MAILPIT}/api/v1/messages`)
  await page.goto(chapterCard('facture'))
  await d.beat(2.5)
  await d.banner(chapter('facture').underTheHood![lang])
  await d.goto(MAILPIT)
  await sendMail('facture')
  // La relève IMAP (jusqu'à 45 s) est coupée au montage : l'accusé de réception apparaît aussitôt.
  await d.offscreen(() => until(async () => {
    const list = await (await page.request.get(`${MAILPIT}/api/v1/messages`)).json() as { messages: { Subject: string }[] }
    return list.messages.some((m) => m.Subject.includes('2026-117'))
  }))
  await d.goto(MAILPIT)
  await page.getByText(/2026-117/).first().click()
  await d.settle()
  await d.beat(5)
  await mailRegister(d)
  await d.beat(4)
  await d.goto('/admin/dmn-editor?decisionKey=routageCourrier')
  await expect(page.locator('.dmn-decision-table-container, .dmn-drd-container').first()).toBeVisible()
  await d.beat(5)
  const facture = await invoice(page)
  await d.goto(`/dossiers/${facture.id}`)
  await d.beat(3)
  await scrollTo(page, '.dossier-fields-editor')
  await d.holdForVoice()
  await d.wrap(order('facture'))
})

test('amende', async ({ page }) => {
  const d = new Director(page, 'amende')
  await page.goto(chapterCard('amende'))
  await d.beat(2.5)
  await d.banner(chapter('amende').underTheHood![lang])
  const before = (await dossiers(page, 'COURRIER')).length
  await d.goto('/mail-deposit')
  const subject = en ? 'Parking fine 2026-0042' : 'Amende de stationnement 2026-0042'
  await page.getByTestId('mail-deposit-files').setInputFiles({
    name: en ? 'fine-2026-0042.pdf' : 'amende-2026-0042.pdf', mimeType: 'application/pdf', buffer: pdf(subject),
  })
  await page.getByTestId('mail-deposit-sender').pressSequentially('pv@police.example', { delay: 40 })
  await page.getByTestId('mail-deposit-subject').pressSequentially(subject, { delay: 40 })
  await d.beat(1)
  await page.getByTestId('mail-deposit-send').click()
  await expect(page.getByTestId('mail-deposit-result').first()).toBeVisible()
  await d.beat(3)
  const fine = await d.offscreen(() => until(async () => {
    const all = await dossiers(page, 'COURRIER')
    return all.length > before ? all[0] : undefined
  }))
  await d.goto('/tasks')
  await d.beat(4)
  // Le tri est asynchrone : la tâche « Trier le courrier » apparaît quelques secondes après le dépôt (attente coupée).
  await d.offscreen(() => until(async () => {
    await d.goto(`/dossiers/${fine.id}`)
    return page.getByTestId('reassign-group-select').first().isVisible()
  }))
  await scrollTo(page, '[data-testid="reassign-group-select"]')
  await page.getByTestId('reassign-group-select').first().selectOption('JURIDIQUE')
  await d.beat(1.5)
  await page.getByTestId('reassign-group').first().click()
  await d.settle()
  await d.holdForVoice()
  await d.wrap(order('amende'))
})

test('agent', async ({ page }) => {
  const d = new Director(page, 'agent')
  await page.goto(chapterCard('agent'))
  await d.beat(2.5)
  await d.banner(chapter('agent').underTheHood![lang])
  const before = (await sortingTasks(page)).length
  await sendMail('inconnu')
  await d.goto('/tasks')
  // Relève IMAP puis tri asynchrone : l'agent ne démarre qu'une fois la tâche de tri créée (attente coupée).
  await d.offscreen(() => until(async () => (await sortingTasks(page)).length > before))
  await page.reload()
  await d.settle()
  await d.beat(3)
  const panel = pathToFileURL(resolve(REPO, 'frontend/capture/panel/agent-panel.html'))
  panel.search = new URLSearchParams({ lang, model: 'gemma4:12b' }).toString()
  await page.goto(panel.toString())
  await d.banner(chapter('agent').underTheHood![lang])
  // Le modèle local réfléchit entre deux outils : passage accéléré quatre fois au montage.
  await d.fast(4, () => runAgent(page))
  await d.beat(3)
  const facture = await invoice(page)
  await d.goto(`/dossiers/${facture.id}`)
  await scrollTo(page, '.relations-list')
  await d.holdForVoice()
  await d.wrap(order('agent'))
})

test('reponse', async ({ page }) => {
  const d = new Director(page, 'reponse')
  await page.goto(chapterCard('reponse'))
  await d.beat(2.5)
  await d.banner(chapter('reponse').underTheHood![lang])
  await d.goto(MAILPIT)
  await sendMail('reponse')
  const reply = await d.offscreen(() => until(async () => (await dossiers(page, 'REPONSE_FACTURE'))[0]))
  const facture = await invoice(page)
  await d.goto(`/dossiers/${facture.id}`)
  await scrollTo(page, '.elements-list')
  await d.beat(4)
  await scrollTo(page, '.relations-list')
  await d.beat(3)
  await d.goto(`/dossiers/${reply.id}`)
  await d.holdForVoice()
  await d.wrap(order('reponse'))
})

test('echeance', async ({ page }) => {
  const d = new Director(page, 'echeance')
  await page.goto(chapterCard('echeance', chapter('echeance').card![lang]))
  await d.beat(3.5)
  await d.banner(chapter('echeance').underTheHood![lang])
  // Les factures semées par la scène : l'une à cinq jours de son terme (rappel), l'autre échue (escalade).
  const due = await invoice(page, en ? 'Invoice 2026-098' : 'Facture 2026-098')
  await d.goto(`/dossiers/${due.id}`)
  await scrollTo(page, '.dossier-fields-editor')
  await d.beat(5)
  const overdue = await invoice(page, en ? 'Invoice 2026-091' : 'Facture 2026-091')
  await d.goto(`/dossiers/${overdue.id}`)
  await scrollTo(page, '.elements-list')
  await d.holdForVoice()
  await d.wrap(order('echeance'))
})

test('conclusion', async ({ page }) => {
  const d = new Director(page, 'conclusion')
  await page.goto(chapterCard('conclusion'))
  await d.beat(2)
  await d.banner(chapter('conclusion').underTheHood![lang])
  await mailRegister(d)
  await d.beat(6)
  await d.goto('/admin/drafts')
  await d.beat(4)
  await d.goto('/admin/dmn-editor?decisionKey=routageCourrier')
  await expect(page.locator('.dmn-decision-table-container, .dmn-drd-container').first()).toBeVisible()
  await d.beat(5)
  await d.banner(null)
  await page.goto(card('Myrmacta', en ? 'Incoming mail, from the first e-mail to the due date' : 'Le courrier entrant, du premier e-mail à l’échéance', 'myrmacta.be', true))
  await d.holdForVoice(2.5)
  await d.wrap(order('conclusion'))
})
