// TODO §140 (D-VIDEO-SHORT, issue #58) — capsule courte C3 « La bonne question, au bon moment », sur la démo complète.
// Le schéma de question du manager est montré avec son aperçu ; puis l'arrivée de Thomas Peeters, qui attend cette
// validation, reçoit la réponse « à corriger » : la fiche revient aux RH (le processus reste en cours, diagramme visible).
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { expect, test, type Page } from '@playwright/test'
import { highlightPath, REPO, until } from './director'
import { api, en, opening, plan, scrollTo, shortCapsule, underTheHood } from './short'

const capsule = shortCapsule('c03-question')
const TYPE = 'ONBOARDING_COLLABORATEUR'
const SCHEMA = 'validation-manager-onboarding'

const questions = JSON.parse(readFileSync(resolve(REPO, 'exemples/onboarding-collaborateur', en ? 'en/config' : 'config', 'questions.json'), 'utf-8')) as {
  schemaKey: string
  fields: { name: string; label: string }[]
}[]
function questionLabel(name: string): RegExp {
  const found = questions.flatMap((s) => s.fields).find((f) => f.name === name)!
  return new RegExp(`^${found.label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}( \\*)?(\\s|$)`)
}

type Dossier = { id: string; label: string }
async function thomas(page: Page): Promise<Dossier> {
  return until(async () => {
    const list = await api<{ content: Dossier[] }>(page, 'GET', `/api/dossiers?dossierTypeCode=${TYPE}&size=50`)
    return list.content.find((d) => d.label.includes('Thomas Peeters'))
  })
}

test('ouverture', async ({ page }) => {
  await opening(capsule, page)
})

test('schema', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'schema')
  await d.goto('/admin/task-question-schemas')
  await d.beat(1.5)
  const row = page.locator('tr').filter({ hasText: SCHEMA })
  await row.getByRole('button', { name: /^(Modifier|Edit)$/ }).click()
  await scrollTo(page, page.getByRole('heading', { name: new RegExp(SCHEMA) }), 'start')
  await d.beat(2)
  // L'aperçu : la liste des décisions possibles.
  await scrollTo(page, page.getByText(/APPROUVER lance|APPROVE starts|APPROUVER starts/).last(), 'center')
  await d.holdForVoice(0.6)
  await d.wrap(order)
})

test('tache', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'tache')
  const dossier = await thomas(page)
  await d.goto(`/dossiers/${dossier.id}`)
  const tasks = page.locator('.task-list').first()
  await scrollTo(page, tasks, 'center')
  await d.beat(2.5)
  await tasks.getByLabel(questionLabel('decisionManager')).selectOption('CORRIGER')
  await d.beat(0.6)
  await tasks.getByLabel(questionLabel('commentaireManager'))
    .pressSequentially(en ? 'The start date is 7 December.' : "La date d'arrivée est le 7 décembre.", { delay: 30 })
  await d.beat(0.8)
  const complete = tasks.getByRole('button', { name: /^(Compléter|Complete)$/ }).first()
  await scrollTo(page, complete, 'center')
  await complete.click()
  // Le processus passe à la correction par les RH ; l'attente est coupée au montage.
  await d.offscreen(async () => {
    await page.waitForTimeout(2500)
    await d.reload()
  })
  await expect(page.locator('.bpmn-viewer .highlight-active').first()).toBeVisible()
  await highlightPath(page)
  await scrollTo(page, page.locator('.bpmn-viewer'), 'center')
  await d.beat(2)
  await d.holdForVoice(0.3)
  await d.wrap(order)
})

test('sous-le-capot', async ({ page }) => {
  await underTheHood(capsule, page)
})
