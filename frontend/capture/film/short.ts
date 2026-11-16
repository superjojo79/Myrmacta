// TODO §140 (D-VIDEO-SHORT, issue #58) — capsules courtes du site : sans son, sous-titres incrustés au montage, en
// boucle. Chaque capsule s'ouvre sur un carton (bénéfice métier, logo) et se ferme sur la carte « Sous le capot » (détail
// technique, logo, tenue au moins 3 s). Les plans entre les deux n'ont pas de bandeau. Scène : la démo complète
// (`scripts/video.sh start c01-dossier`).
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import { expect, type Page } from '@playwright/test'
import { Director, lang, REPO } from './director'

type Text = { fr: string; en: string }
type Lines = { fr: string[]; en: string[] }
export type ShortCapsule = { id: string; title: Text; underTheHood: Lines; chapters: { id: string; title: Text }[] }

export const en = lang === 'en'

/** La capsule courte `id` (scripts/video/capsules/<id>.json). */
export function shortCapsule(id: string): ShortCapsule {
  return JSON.parse(readFileSync(resolve(REPO, `scripts/video/capsules/${id}.json`), 'utf-8')) as ShortCapsule
}

function card(title: string, subtitle: string): string {
  const url = pathToFileURL(resolve(REPO, 'frontend/capture/panel/card.html'))
  url.search = new URLSearchParams({ kicker: '', title, subtitle, lang, logo: '1' }).toString()
  return url.toString()
}

/** Le plan `chapter` de la capsule : son metteur en scène et son rang (nom du fichier vidéo). */
export function plan(capsule: ShortCapsule, page: Page, chapter: string): { director: Director; order: number } {
  const order = capsule.chapters.findIndex((c) => c.id === chapter)
  if (order < 0) throw new Error(`${capsule.id} has no chapter ${chapter}`)
  return { director: new Director(page, chapter), order }
}

/** Carton d'ouverture : le bénéfice métier, sous le logo. */
export async function opening(capsule: ShortCapsule, page: Page): Promise<void> {
  const { director, order } = plan(capsule, page, 'ouverture')
  await director.goto(card(capsule.title[lang], ''))
  await director.beat(3)
  await director.holdForVoice(0)
  await director.wrap(order)
}

/** Carte de fin « Sous le capot » : le détail technique, sous le logo, tenue au moins 3 s (ici 4,5 s). */
export async function underTheHood(capsule: ShortCapsule, page: Page): Promise<void> {
  const { director, order } = plan(capsule, page, 'sous-le-capot')
  await director.goto(card(en ? 'Under the hood' : 'Sous le capot', capsule.underTheHood[lang].join('\n')))
  await director.beat(4.5)
  await director.holdForVoice(0)
  await director.wrap(order)
}

/** Clé du parcours d'exemple (capsule C2, screenshot S3). */
export const DEMO_JOURNEY = 'demandeAcces'

/**
 * Un parcours d'exemple : validation du manager, décision, validation des accès. Sans type de dossier : celui des
 * arrivées lance déjà son propre processus, et le concepteur le signalerait par un avertissement.
 */
export function demoJourney() {
  const t = (fr: string, english: string) => (en ? english : fr)
  return {
    label: t("Demande d'accès", 'Access request'),
    definition: {
      statuses: [
        { code: 'VALIDATION', label: t('En validation', 'Under review'), initial: true, outcome: 'NONE' },
        { code: 'ACCORDE', label: t('Accordé', 'Granted'), initial: false, outcome: 'COMPLETED' },
        { code: 'REFUSE', label: t('Refusé', 'Refused'), initial: false, outcome: 'CANCELLED' },
      ],
      allowedDossierStatuses: ['OUVERT', 'EN_COURS'],
      onStartDossierStatus: 'EN_COURS',
      steps: [
        { id: 'validation', label: t('Le manager valide', 'The manager approves'), kind: 'QUESTION', params: { schemaKey: 'validation-manager-onboarding' }, next: 'decision' },
        { id: 'decision', label: t('Approuvée ?', 'Approved?'), kind: 'DECISION', params: {},
          branches: [{ variable: 'decisionManager', operator: 'EQ', value: 'APPROUVER', next: 'acces' }], defaultNext: 'refus' },
        { id: 'acces', label: t("L'IT valide les accès", 'IT approves the access'), kind: 'QUESTION', params: { schemaKey: 'validation-acces-onboarding' }, next: 'accorde' },
        { id: 'accorde', label: t('Accès accordés', 'Access granted'), kind: 'SET_WORKFLOW_STATUS', params: { targetWorkflowStatus: 'ACCORDE' }, next: 'cloture' },
        { id: 'cloture', label: t('Clôturer le dossier', 'Close the case'), kind: 'SET_DOSSIER_STATUS', params: { targetDossierStatus: 'CLOTURE' } },
        { id: 'refus', label: t('Demande refusée', 'Request refused'), kind: 'SET_WORKFLOW_STATUS', params: { targetWorkflowStatus: 'REFUSE' } },
      ],
    },
  }
}

/** Appel d'API de la session filmée ; le BFF exige l'en-tête CSRF recopié du cookie XSRF-TOKEN. */
export async function api<T = unknown>(page: Page, method: 'GET' | 'POST' | 'PATCH' | 'PUT', url: string, data?: unknown,
  headers: Record<string, string> = {}): Promise<T> {
  const xsrf = (await page.context().cookies()).find((c) => c.name === 'XSRF-TOKEN')?.value ?? ''
  const response = await page.request.fetch(url, { method, data, headers: { 'X-XSRF-TOKEN': xsrf, ...headers } })
  expect(response.ok(), `${method} ${url} → ${response.status()} ${await response.text()}`).toBeTruthy()
  const text = await response.text()
  return (text ? JSON.parse(text) : null) as T
}

/**
 * Le libellé du champ `name` de l'exemple onboarding, dans la langue de la scène (celui de l'exemple installé). Le nom
 * accessible d'une liste déroulante contient aussi ses options : pas d'ancre de fin.
 */
export function onboardingLabel(name: string): RegExp {
  const config = resolve(REPO, 'exemples/onboarding-collaborateur', en ? 'en/config' : 'config', 'type-dossier.json')
  const fields = (JSON.parse(readFileSync(config, 'utf-8')) as { fieldDefinitions: { name: string; label: string }[] }).fieldDefinitions
  const found = fields.find((f) => f.name === name)
  if (!found) throw new Error(`unknown field ${name}`)
  return new RegExp(`^${found.label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}( \\*)?(\\s|$)`)
}

/** Fait défiler jusqu'à `target`, en douceur, puis laisse l'image se poser. */
export async function scrollTo(page: Page, target: ReturnType<Page['locator']>, block: 'start' | 'center' = 'center'): Promise<void> {
  await target.first().evaluate((el, b) => el.scrollIntoView({ behavior: 'smooth', block: b }), block)
  await page.waitForTimeout(900)
}
