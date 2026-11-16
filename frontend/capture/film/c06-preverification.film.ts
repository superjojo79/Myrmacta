// TODO §143 (D-VIDEO-SHORT, D-VIDEO-RIVERAIN-SCENE, issue #101) — capsule courte C6 « Les pièces incohérentes repérées
// avant l'agent », sur la scène riverain (`scripts/video.sh start c06-preverification`), avec le modèle local d'Ollama.
// La scène de C6 déploie la variante du processus avec pré-vérification, traduite (app.demo.riverain.variant). Hors champ,
// trois demandes du jeu d'évaluation (conforme, preuve au nom d'un autre, consigne injectée) reçoivent leurs pièces et
// démarrent. Chaque demande a sa plaque (une seule demande ouverte par plaque) ; les pièces la reprennent.
// Le tournage échoue si le modèle ne signale pas l'écart des deux derniers cas : les sous-titres l'affirment.
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { expect, test, type Page } from '@playwright/test'
import { pdf, REPO, until } from './director'
import { api, en, opening, plan, scrollTo, shortCapsule, underTheHood } from './short'

const capsule = shortCapsule('c06-preverification')

// Le modèle local traite les demandes une à une : un plan peut attendre plus que les 10 minutes de la configuration.
test.describe.configure({ timeout: 20 * 60_000 })
const TYPE = 'AUTORISATION_RIVERAIN'
const PROCESS = 'autorisationRiverain'
const example = resolve(REPO, 'exemples/autorisation-riverain')
const sample = JSON.parse(readFileSync(resolve(example, 'config/exemple-dossier.json'), 'utf-8')) as {
  fields: Record<string, string>
}
type Piece = { type: string; intitule: string; texte: string }
const cases = (JSON.parse(readFileSync(resolve(example, 'evaluation/cas.json'), 'utf-8')) as {
  cas: { id: string; pieces: Piece[] }[]
}).cas

/** Les trois demandes filmées : le cas d'évaluation, sa plaque, son libellé. */
const SCENES = [
  { id: 'conforme', plate: '1-RIV-101' },
  { id: 'preuve-autre-titulaire', plate: '1-RIV-102' },
  { id: 'injection', plate: '1-RIV-103' },
] as const
const label = (plate: string) => (en ? 'Resident parking permit — Dupont — ' : 'Autorisation riverain — Dupont — ') + plate

type Dossier = { id: string; label: string; version: number }
type Finding = { id: string; kind: string; origin: string; sourceRef: string; message: string }
type Run = { id: string; status: string; findings: Finding[] }

async function xsrf(page: Page): Promise<string> {
  return (await page.context().cookies()).find((c) => c.name === 'XSRF-TOKEN')?.value ?? ''
}

/** Hors champ : une demande du cas `id`, ses pièces jointes, puis le workflow (la pré-vérification part aussitôt). */
async function submit(page: Page, id: string, plate: string): Promise<string> {
  const pieces = cases.find((c) => c.id === id)!.pieces
  const created = await api<Dossier>(page, 'POST', '/api/dossiers', {
    dossierTypeCode: TYPE, label: label(plate), fields: { ...sample.fields, plaque: plate },
  })
  // La pièce d'identité est exigée mais sensible : jamais lue par le modèle.
  for (const piece of [...pieces, { type: 'pieceIdentite', intitule: 'Carte d\'identité', texte: 'Carte d\'identité de Camille Dupont' }]) {
    const response = await page.request.post(`/api/dossiers/${created.id}/elements/attachment`, {
      headers: { 'X-XSRF-TOKEN': await xsrf(page) },
      multipart: {
        file: { name: `${piece.type}.pdf`, mimeType: 'application/pdf', buffer: pdf(piece.texte.replaceAll('1-ABC-123', plate)) },
        label: piece.intitule, pieceKey: piece.type,
      },
    })
    expect(response.ok(), `upload ${piece.type} → ${response.status()}`).toBeTruthy()
  }
  const opened = await api<Dossier>(page, 'PATCH', `/api/dossiers/${created.id}/status`, { status: 'OUVERT', expectedVersion: created.version })
  await api(page, 'POST', `/api/dossiers/${opened.id}/workflows`, { processDefinitionKey: PROCESS, variables: {} },
    { 'Idempotency-Key': `film-${opened.id}` })
  return opened.id
}

async function dossierId(page: Page, plate: string): Promise<string> {
  return until(async () => {
    const list = await api<{ content: Dossier[] }>(page, 'GET', `/api/dossiers?dossierTypeCode=${TYPE}&size=20`)
    return list.content.find((d) => d.label === label(plate))?.id
  })
}

/** La pré-vérification terminée du dossier (un modèle local traite les trois demandes une à une : jusqu’à quelques minutes). */
async function run(page: Page, id: string): Promise<Run> {
  return until(async () => {
    const runs = await api<Run[]>(page, 'GET', `/api/dossiers/${id}/prechecks`)
    return runs.find((r) => r.status === 'SUCCEEDED')
  }, 900_000)
}

/** Les sous-titres affirment que l'écart est signalé : sinon, le tournage échoue plutôt que de mentir. */
function expectFlagged(found: Run): void {
  // La question attendue par le jeu d'évaluation : la preuve de domicile au nom du demandeur.
  expect(found.findings.some((f) => f.origin === 'MODEL' && f.kind === 'INCONSISTENT' && f.sourceRef.includes('domicileAuNom')),
    `model did not flag the gap: ${JSON.stringify(found.findings.map((f) => [f.origin, f.kind, f.sourceRef, f.message]))}`).toBe(true)
}

async function showPanel(page: Page): Promise<void> {
  await page.addStyleTag({ content: 'main { padding-bottom: 360px !important; }' })
  await scrollTo(page, page.getByTestId('precheck-panel'), 'start')
}

test('ouverture', async ({ page }) => {
  await opening(capsule, page)
})

test('conforme', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'conforme')
  await d.goto('/supervision')
  // Hors champ (coupé au montage) : les trois demandes, que le modèle local pré-vérifie une à une.
  const id = await d.offscreen(async () => {
    for (const scene of SCENES) await submit(page, scene.id, scene.plate)
    const first = await dossierId(page, SCENES[0].plate)
    await run(page, first)
    return first
  })
  await d.goto(`/dossiers/${id}`)
  await showPanel(page)
  await d.holdForVoice(0.6)
  await d.wrap(order)
})

test('titulaire', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'titulaire')
  await d.goto('/supervision')
  const id = await dossierId(page, SCENES[1].plate)
  expectFlagged(await d.offscreen(() => run(page, id)))
  await d.goto(`/dossiers/${id}`)
  await showPanel(page)
  await d.beat(1.5)
  await scrollTo(page, page.locator('[data-testid="precheck-finding"][data-kind="INCONSISTENT"]').first(), 'center')
  await d.holdForVoice(0.6)
  await d.wrap(order)
})

test('injection', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'injection')
  await d.goto('/supervision')
  const id = await dossierId(page, SCENES[2].plate)
  expectFlagged(await d.offscreen(() => run(page, id)))
  await d.goto(`/dossiers/${id}`)
  await showPanel(page)
  await d.beat(2)
  const flagged = page.locator('[data-testid="precheck-finding"][data-kind="INCONSISTENT"][data-origin="MODEL"]').first()
  await scrollTo(page, flagged, 'center')
  await d.beat(2.5)
  // Son nom accessible reprend le message du constat ; on le vise par son texte.
  await flagged.locator('button', { hasText: /^\s*(Accepter|Accept)\s*$/ }).click()
  await expect(flagged.getByTestId('precheck-decision')).toBeVisible()
  await d.beat(1)
  await d.holdForVoice(0.6)
  await d.wrap(order)
})

test('sous-le-capot', async ({ page }) => {
  await underTheHood(capsule, page)
})
