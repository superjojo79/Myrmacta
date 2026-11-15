// TODO §140 (D-VIDEO-SHORT, issue #58) — capsule courte C8 « Une panne n'arrête pas le dossier », sur la démo complète.
// L'incident est celui du paquet « états » : l'arrivée de Nadia Claes, dont l'adresse en .invalid fait couper la connexion
// par le faux SIRH. On corrige l'adresse sur la fiche, puis on rejoue l'étape : le processus va jusqu'au provisioning.
import { expect, test, type Page } from '@playwright/test'
import { until } from './director'
import { api, onboardingLabel, opening, plan, scrollTo, shortCapsule, underTheHood } from './short'

const capsule = shortCapsule('c08-panne')
const TYPE = 'ONBOARDING_COLLABORATEUR'

type Dossier = { id: string; label: string; fields: Record<string, unknown> }

async function nadia(page: Page): Promise<Dossier> {
  const found = await until(async () => {
    const list = await api<{ content: Dossier[] }>(page, 'GET', `/api/dossiers?dossierTypeCode=${TYPE}&size=50`)
    return list.content.find((d) => d.label.includes('Nadia Claes'))
  })
  return api<Dossier>(page, 'GET', `/api/dossiers/${found.id}`)
}

test('ouverture', async ({ page }) => {
  await opening(capsule, page)
})

test('incident', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'incident')
  const dossier = await nadia(page)
  await d.goto('/admin/incidents')
  await expect(page.getByRole('button', { name: /^(Rejouer|Retry)/ }).first()).toBeVisible()
  await d.beat(2.5)
  await d.goto(`/dossiers/${dossier.id}`)
  await d.beat(1)
  await scrollTo(page, page.getByTestId('incident-banner'), 'center')
  await d.holdForVoice(0.3)
  await d.wrap(order)
})

test('rejeu', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'rejeu')
  const dossier = await nadia(page)
  await d.goto(`/dossiers/${dossier.id}`)
  const email = page.getByLabel(onboardingLabel('emailPersonnel'))
  await scrollTo(page, email, 'center')
  await d.beat(1)
  await email.fill('')
  await email.pressSequentially('nadia.claes@example.org', { delay: 35 })
  await d.beat(0.8)
  await page.getByRole('button', { name: /^(Enregistrer les champs|Save the fields)$/ }).click()
  await d.beat(1.2)
  const replay = page.getByTestId('incident-banner').getByRole('button', { name: /^(Rejouer|Retry)/ })
  await scrollTo(page, replay, 'center')
  await d.beat(0.8)
  await replay.click()
  // Le processus reprend : SIRH, IAM puis ITSM répondent ; l'attente est coupée au montage.
  await d.offscreen(() => until(async () => (await api<Dossier>(page, 'GET', `/api/dossiers/${dossier.id}`)).fields.ticketMateriel))
  await d.reload()
  await scrollTo(page, page.getByRole('heading', { name: /^Provisioning$/ }), 'start')
  await d.holdForVoice(0.3)
  await d.wrap(order)
})

test('sous-le-capot', async ({ page }) => {
  await underTheHood(capsule, page)
})
