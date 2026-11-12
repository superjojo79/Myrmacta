// TODO §136 (D-DEMO-FAKE-FAULTS, D-VIDEO-OFFSCREEN-PREP, D-VIDEO-PIP ; issue #57) — tournage de la capsule L2
// « onboarding orchestré », un test par chapitre, contre la scène `scripts/video.sh start l2-onboarding` (profil
// demo-video-onboarding : exemple sans arrivée, faux SIRH, IAM et ITSM sur http://localhost:18090). Partie 1 filmée dans
// l'interface ; les arrivées de la partie 2 sont préparées par l'API hors champ, pannes commandées aux faux services.
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import { expect, test, type Page } from '@playwright/test'
import { Director, lang, REPO, until } from './director'

type Text = { fr: string; en: string }
type Chapter = { id: string; title: Text; part?: Text; underTheHood?: Text }
const capsule = JSON.parse(readFileSync(resolve(REPO, 'scripts/video/capsules/l2-onboarding.json'), 'utf-8')) as {
  title: Text
  chapters: Chapter[]
}
const chapter = (id: string): Chapter => capsule.chapters.find((c) => c.id === id)!
const order = (id: string): number => capsule.chapters.findIndex((c) => c.id === id)
const en = lang === 'en'
const TYPE = 'ONBOARDING_COLLABORATEUR'
const PROCESS = 'onboardingCollaborateur'
const FAKE_APIS = process.env.FILM_FAKE_APIS ?? 'http://localhost:18090'
const DIAGRAM = '.bpmn-viewer'

// Libellés des champs et des questionnaires dans la langue de la scène : ceux de l'exemple installé.
const example = resolve(REPO, 'exemples/onboarding-collaborateur', en ? 'en/config' : 'config')
type FieldDef = { name: string; label: string }
const typeFields = (JSON.parse(readFileSync(resolve(example, 'type-dossier.json'), 'utf-8')) as {
  fieldDefinitions: FieldDef[]
}).fieldDefinitions
const questionFields = (JSON.parse(readFileSync(resolve(example, 'questions.json'), 'utf-8')) as { fields: FieldDef[] }[])
  .flatMap((schema) => schema.fields)
function label(name: string): RegExp {
  const found = [...typeFields, ...questionFields].find((f) => f.name === name)
  if (!found) throw new Error(`unknown field ${name}`)
  // Le nom accessible d'une liste déroulante contient aussi ses options : pas d'ancre de fin.
  return new RegExp(`^${found.label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}( \\*)?(\\s|$)`)
}

const camille = {
  label: en ? 'New hire — Camille Lenoir' : 'Arrivée — Camille Lenoir',
  prenom: 'Camille', nom: 'Lenoir', emailPersonnel: 'camille.lenoir@example.org',
  fonction: en ? 'Java developer' : 'Développeuse Java', departement: 'IT', managerLogin: 'mdupont',
  profilPoste: 'DEVELOPPEUR', wrongDate: '2026-12-07', rightDate: '2026-12-14',
}

// ---------------------------------------------------------------------------------------------------------------- API

type Dossier = { id: string; label: string; version: number; status: string; fields: Record<string, unknown> }

/** Appel d'API de la session filmée ; le BFF exige l'en-tête CSRF recopié du cookie XSRF-TOKEN. */
async function api(page: Page, method: 'GET' | 'POST' | 'PATCH', url: string, data?: unknown, headers: Record<string, string> = {}) {
  const xsrf = (await page.context().cookies()).find((c) => c.name === 'XSRF-TOKEN')?.value ?? ''
  const response = await page.request.fetch(url, { method, data, headers: { 'X-XSRF-TOKEN': xsrf, ...headers } })
  expect(response.ok(), `${method} ${url} → ${response.status()} ${await response.text()}`).toBeTruthy()
  const text = await response.text()
  return text ? JSON.parse(text) : null
}

async function byLabel(page: Page, wanted: string): Promise<Dossier | undefined> {
  const list = (await api(page, 'GET', `/api/dossiers?dossierTypeCode=${TYPE}&size=50`)) as { content: { id: string; label: string }[] }
  const found = list.content.find((d) => d.label === wanted)
  return found ? ((await api(page, 'GET', `/api/dossiers/${found.id}`)) as Dossier) : undefined
}

async function dossierNamed(page: Page, wanted: string): Promise<Dossier> {
  return until(() => byLabel(page, wanted))
}

async function link(page: Page, dossierId: string): Promise<string> {
  return until(async () => {
    const page1 = (await api(page, 'GET', `/api/dossiers/${dossierId}/workflows?size=5`)) as { content: { id: string; processDefinitionKey: string }[] }
    return page1.content.find((l) => l.processDefinitionKey === PROCESS)?.id
  })
}

type TaskView = { taskId: string; taskDefinitionKey: string | null }

async function task(page: Page, dossierId: string, key: string): Promise<TaskView> {
  const linkId = await link(page, dossierId)
  return until(async () => {
    // Juste après le démarrage, le lien existe avant son instance de processus (409) : on réessaie.
    const response = await page.request.get(`/api/workflow-links/${linkId}/tasks?size=10`)
    if (!response.ok()) return undefined
    const tasks = (await response.json()) as { content: TaskView[] }
    return tasks.content.find((t) => t.taskDefinitionKey === key)
  })
}

/** Commande une panne au faux service : les `times` prochains appels de l'étape répondent `status`. */
async function fault(page: Page, step: 'sirh' | 'iam' | 'groups' | 'itsm', status: number, times = 1): Promise<void> {
  const response = await page.request.post(`${FAKE_APIS}/_demo/faults`, { data: { step, status, times } })
  expect(response.status(), 'fake services: fault not armed').toBe(204)
}

/** Une arrivée encodée, ouverte, démarrée et approuvée par l'API : elle part aussitôt vers le SIRH, l'IAM et l'ITSM. */
async function approvedArrival(page: Page, who: { label: string; prenom: string; nom: string; email: string; fonction: string; departement: string }): Promise<Dossier> {
  const created = (await api(page, 'POST', '/api/dossiers', {
    dossierTypeCode: TYPE, label: who.label,
    fields: {
      prenom: who.prenom, nom: who.nom, emailPersonnel: who.email, dateArrivee: '2026-12-14', fonction: who.fonction,
      departement: who.departement, managerLogin: 'mdupont', profilPoste: 'STANDARD',
    },
  })) as Dossier
  const opened = (await api(page, 'PATCH', `/api/dossiers/${created.id}/status`, { status: 'OUVERT', expectedVersion: created.version })) as Dossier
  await api(page, 'POST', `/api/dossiers/${opened.id}/workflows`, { processDefinitionKey: PROCESS, variables: {} },
    { 'Idempotency-Key': `film-${opened.id}` })
  const manager = await task(page, opened.id, 'validationManager')
  await api(page, 'POST', `/api/workflow-tasks/${manager.taskId}/complete`, { variables: { decisionManager: 'APPROUVER' } })
  return opened
}

/** Attend que le dossier ait la valeur `field` (champ géré par le workflow), en relisant l'API. */
async function untilField(page: Page, dossierId: string, field: string): Promise<void> {
  await until(async () => {
    const dossier = (await api(page, 'GET', `/api/dossiers/${dossierId}`)) as Dossier
    return dossier.fields[field] !== undefined && dossier.fields[field] !== null && dossier.fields[field] !== ''
  })
}

// ------------------------------------------------------------------------------------------------------------ Écran

function card(kicker: string, title: string, subtitle = '', logo = false): string {
  const url = pathToFileURL(resolve(REPO, 'frontend/capture/panel/card.html'))
  url.search = new URLSearchParams({ kicker, title, subtitle, lang, logo: logo ? '1' : '0' }).toString()
  return url.toString()
}

/** Le carton d'un chapitre : la partie (s'il en ouvre une) ou « Chapitre N » en sur-titre, son titre en grand. */
function chapterCard(id: string): string {
  const c = chapter(id)
  return card(c.part ? c.part[lang] : `${en ? 'Chapter' : 'Chapitre'} ${order(id)}`, c.title[lang])
}

async function scrollTo(page: Page, target: string | ReturnType<Page['locator']>, block: 'start' | 'center' = 'start'): Promise<void> {
  const locator = typeof target === 'string' ? page.locator(target).first() : target
  await locator.evaluate((el, b) => el.scrollIntoView({ behavior: 'smooth', block: b }), block)
  await page.waitForTimeout(900)
}

const provisioning = (page: Page) => page.getByRole('heading', { name: 'Provisioning' }).first()
const taskList = (page: Page) => page.locator('.task-list').first()

/** Attend hors champ (coupé au montage) que la tâche `key` paraisse sur la fiche. */
async function awaitTask(d: Director, dossierId: string, key: string): Promise<void> {
  await d.offscreen(async () => {
    await task(d.page, dossierId, key)
    await d.reload()
  })
}

async function answer(page: Page, values: Record<string, string>): Promise<void> {
  const form = taskList(page)
  for (const [name, value] of Object.entries(values)) {
    const control = form.getByLabel(label(name))
    if ((await control.evaluate((el) => el.tagName)) === 'SELECT') await control.selectOption(value)
    else await control.pressSequentially(value, { delay: 30 })
    await page.waitForTimeout(500)
  }
}

/** Le bouton est d'abord centré à l'écran : en bas, le bandeau « Sous le capot » le masquerait. */
async function complete(page: Page): Promise<void> {
  const button = taskList(page).getByRole('button', { name: /^(Compléter|Complete)$/ }).first()
  await scrollTo(page, button, 'center')
  await page.waitForTimeout(600)
  await button.click()
  await page.waitForTimeout(1200)
}

// ---------------------------------------------------------------------------------------------------------- Chapitres

test.describe.configure({ mode: 'serial' })

test('intro', async ({ page }) => {
  const d = new Director(page, 'intro')
  await page.goto(card('Myrmacta', capsule.title[lang], en ? 'HRIS · IAM · ITSM orchestration' : 'Orchestration SIRH · IAM · ITSM', true))
  await d.holdForVoice()
  await d.wrap(order('intro'))
})

test('encodage', async ({ page }) => {
  const d = new Director(page, 'encodage')
  // Une scène fraîche : les faux services répondent tous en succès.
  await page.request.delete(`${FAKE_APIS}/_demo/faults`)
  await page.goto(chapterCard('encodage'))
  await d.beat(3)
  await d.banner(chapter('encodage').underTheHood![lang])
  await d.goto('/dossiers/new')
  const form = page.locator('form.create-dossier__form')
  await form.locator('select').first().selectOption(TYPE)
  await d.beat(1)
  // La saisie est réelle, accélérée deux fois au montage.
  await d.fast(2, async () => {
    await page.locator('#dossier-label').pressSequentially(camille.label, { delay: 25 })
    for (const name of ['prenom', 'nom', 'emailPersonnel', 'fonction', 'managerLogin'] as const) {
      await form.getByLabel(label(name)).pressSequentially(camille[name], { delay: 25 })
    }
    await form.getByLabel(label('dateArrivee')).fill(camille.wrongDate)
    await form.getByLabel(label('departement')).selectOption(camille.departement)
    await form.getByLabel(label('profilPoste')).selectOption(camille.profilPoste)
  })
  await d.beat(1.5)
  await form.getByRole('button', { name: /^(Créer|Create)$/ }).click()
  await page.waitForURL(/\/dossiers\/[0-9a-f-]{36}$/)
  await d.settle()
  await d.beat(1.5)
  const actions = page.locator('.dossier-detail__actions')
  await actions.locator('select').selectOption('OUVERT')
  await d.beat(1)
  await actions.getByRole('button', { name: /Enregistrer statut|Save status/ }).click()
  await d.beat(1.5)
  await actions.getByRole('button', { name: /Démarrer workflow|Start workflow/ }).click()
  await d.beat(2)
  await page.getByRole('button', { name: /^(Démarrer|Start)$/ }).click()
  const dossier = await dossierNamed(page, camille.label)
  await awaitTask(d, dossier.id, 'validationManager')
  await scrollTo(page, '.task-list')
  await d.holdForVoice()
  await d.wrap(order('encodage'))
})

test('manager', async ({ page }) => {
  const d = new Director(page, 'manager')
  const dossier = await dossierNamed(page, camille.label)
  await page.goto(chapterCard('manager'))
  await d.beat(2.5)
  await d.banner(chapter('manager').underTheHood![lang])
  await d.goto(`/dossiers/${dossier.id}`)
  await scrollTo(page, '.task-list')
  await answer(page, {
    decisionManager: 'CORRIGER',
    commentaireManager: en ? 'The start date is 14 December.' : "La date d'arrivée est le 14 décembre.",
  })
  await d.beat(1)
  await complete(page)
  await awaitTask(d, dossier.id, 'correction')
  await scrollTo(page, '.dossier-fields-task-form')
  const date = page.locator('.dossier-fields-task-form').getByLabel(label('dateArrivee'))
  await scrollTo(page, date, 'center')
  await d.beat(1.5)
  await date.fill(camille.rightDate)
  await d.beat(1.5)
  await complete(page)
  await awaitTask(d, dossier.id, 'validationManager')
  await scrollTo(page, '.task-list')
  await answer(page, { decisionManager: 'APPROUVER' })
  await d.beat(1)
  await complete(page)
  await d.holdForVoice()
  await d.wrap(order('manager'))
})

test('provisioning', async ({ page }) => {
  const d = new Director(page, 'provisioning')
  const dossier = await dossierNamed(page, camille.label)
  await page.goto(chapterCard('provisioning'))
  await d.beat(2.5)
  await d.banner(chapter('provisioning').underTheHood![lang])
  // Les appels SIRH puis IAM partent dès l'approbation ; leur aboutissement (quelques secondes) est coupé.
  await d.offscreen(() => untilField(page, dossier.id, 'loginIam'))
  await d.goto(`/dossiers/${dossier.id}`)
  await d.pip(DIAGRAM)
  await scrollTo(page, provisioning(page))
  await d.beat(4)
  await d.holdForVoice()
  await d.pip(null)
  await d.wrap(order('provisioning'))
})

test('acces', async ({ page }) => {
  const d = new Director(page, 'acces')
  const dossier = await dossierNamed(page, camille.label)
  await page.goto(chapterCard('acces'))
  await d.beat(2.5)
  await d.banner(chapter('acces').underTheHood![lang])
  await d.goto(`/dossiers/${dossier.id}`)
  await task(page, dossier.id, 'validationAcces')
  await scrollTo(page, '.task-list')
  await answer(page, {
    niveauAcces: 'DEVELOPPEURS',
    justificationAcces: en ? 'Java team, repository access' : 'Équipe Java, accès aux dépôts',
  })
  await d.beat(1)
  await complete(page)
  await d.offscreen(async () => {
    await untilField(page, dossier.id, 'ticketMateriel')
    await d.reload()
  })
  await d.pip(DIAGRAM)
  await scrollTo(page, provisioning(page))
  await d.beat(5)
  await d.pip(null)
  await scrollTo(page, page.getByRole('heading', { name: 'Workflows' }).first(), 'center')
  await d.holdForVoice()
  await d.wrap(order('acces'))
})

const lucas = {
  label: en ? 'New hire — Lucas Martin' : 'Arrivée — Lucas Martin', prenom: 'Lucas', nom: 'Martin',
  email: 'lucas.martin@example.org', fonction: en ? 'Accountant' : 'Comptable', departement: 'FINANCE',
}
const ines = {
  label: en ? 'New hire — Inès Lambert' : 'Arrivée — Inès Lambert', prenom: 'Inès', nom: 'Lambert',
  email: 'ines.lambert@example.org', fonction: en ? 'Sales representative' : 'Commerciale', departement: 'VENTES',
}

test('reessayer', async ({ page }) => {
  const d = new Director(page, 'reessayer')
  await page.goto(chapterCard('reessayer'))
  await d.beat(3)
  await d.banner(chapter('reessayer').underTheHood![lang])
  // Hors champ : l'IAM répondra 503 une fois ; l'arrivée est encodée et approuvée par l'API, jusqu'à l'incident.
  const dossier = await d.offscreen(async () => {
    await fault(page, 'iam', 503)
    const created = await approvedArrival(page, lucas)
    await task(page, created.id, 'traiterIncident')
    return created
  })
  await d.goto(`/dossiers/${dossier.id}`)
  await d.pip(DIAGRAM)
  await scrollTo(page, provisioning(page))
  await d.beat(5)
  await d.pip(null)
  await scrollTo(page, '.task-list')
  await answer(page, {
    actionIncident: 'REESSAYER',
    noteIncident: en ? 'IAM back online' : "L'IAM est de nouveau disponible",
  })
  await d.beat(1)
  await complete(page)
  await d.offscreen(async () => {
    await untilField(page, dossier.id, 'ticketMateriel')
    await d.reload()
  })
  await scrollTo(page, provisioning(page))
  await d.holdForVoice()
  await d.wrap(order('reessayer'))
})

test('abandon', async ({ page }) => {
  const d = new Director(page, 'abandon')
  await page.goto(chapterCard('abandon'))
  await d.beat(2.5)
  await d.banner(chapter('abandon').underTheHood![lang])
  const dossier = await d.offscreen(async () => {
    await fault(page, 'itsm', 500)
    const created = await approvedArrival(page, ines)
    await task(page, created.id, 'traiterIncident')
    return created
  })
  await d.goto(`/dossiers/${dossier.id}`)
  await scrollTo(page, provisioning(page))
  await d.beat(5)
  await scrollTo(page, '.task-list')
  await answer(page, {
    actionIncident: 'ABANDONNER',
    noteIncident: en ? 'Start postponed, roll back' : 'Arrivée reportée, on annule',
  })
  await d.beat(1)
  await complete(page)
  await d.offscreen(async () => {
    await untilField(page, dossier.id, 'journalCompensation')
    await d.reload()
  })
  await scrollTo(page, provisioning(page))
  await d.holdForVoice()
  await d.wrap(order('abandon'))
})

test('conclusion', async ({ page }) => {
  const d = new Director(page, 'conclusion')
  await page.goto(chapterCard('conclusion'))
  await d.beat(2)
  await d.banner(chapter('conclusion').underTheHood![lang])
  await d.goto('/supervision')
  await page.getByTestId('type-filter').selectOption(TYPE)
  await page.locator('form.supervision__filters').getByRole('button', { name: /Filtrer|Filter/ }).click()
  await d.settle()
  await d.beat(6)
  await d.banner(null)
  await page.goto(card('Myrmacta', en ? 'Orchestrating your applications, driven by the business' : 'L’orchestration de vos applications, pilotée par le métier', 'myrmacta.be', true))
  // Le carton de fin reste au moins 3 s, même si la voix s'est tue avant qu'il paraisse.
  await d.beat(3)
  await d.holdForVoice(2.5)
  await d.wrap(order('conclusion'))
})
