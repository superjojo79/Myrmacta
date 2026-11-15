// TODO §140 (D-VIDEO-SHORT, issue #58) — capsule courte C1 « Un dossier complet, suivi de bout en bout », sur la démo
// complète. Un plan crée une arrivée (le formulaire change avec le type), l'autre suit une arrivée en cours.
import { test } from '@playwright/test'
import { until } from './director'
import { api, en, onboardingLabel, opening, plan, scrollTo, shortCapsule, underTheHood } from './short'

const capsule = shortCapsule('c01-dossier')
const TYPE = 'ONBOARDING_COLLABORATEUR'

const label = onboardingLabel

const lucas = {
  label: en ? 'New hire — Lucas Martin' : 'Arrivée — Lucas Martin',
  prenom: 'Lucas', nom: 'Martin', emailPersonnel: 'lucas.martin@example.org', dateArrivee: '2026-12-07',
  fonction: en ? 'Sales representative' : 'Commercial', departement: 'VENTES', managerLogin: 'mdupont',
  profilPoste: 'STANDARD',
}

test('ouverture', async ({ page }) => {
  await opening(capsule, page)
})

test('creation', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'creation')
  await d.goto('/dossiers/new')
  const form = page.locator('form.create-dossier__form')
  const type = form.locator('select').first()
  await d.beat(1.2)
  // Le formulaire prend les champs du type choisi.
  await type.selectOption('FACTURE')
  await d.beat(1.5)
  await type.selectOption(TYPE)
  await d.beat(1.2)
  await d.fast(2.5, async () => {
    await page.locator('#dossier-label').pressSequentially(lucas.label, { delay: 20 })
    for (const name of ['prenom', 'nom', 'emailPersonnel', 'fonction', 'managerLogin'] as const) {
      await form.getByLabel(label(name)).pressSequentially(lucas[name], { delay: 20 })
    }
    await form.getByLabel(label('dateArrivee')).fill(lucas.dateArrivee)
    await form.getByLabel(label('departement')).selectOption(lucas.departement)
    await form.getByLabel(label('profilPoste')).selectOption(lucas.profilPoste)
  })
  await d.beat(2.5)
  await form.getByRole('button', { name: /^(Créer|Create)$/ }).click()
  await page.waitForURL(/\/dossiers\/[0-9a-f-]{36}$/)
  await d.settle()
  await d.beat(1.5)
  await d.holdForVoice(0.3)
  await d.wrap(order)
})

test('suivi', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'suivi')
  // Une arrivée en cours de la démo : approuvée, fiche SIRH et compte IAM créés, accès à valider par l'IT.
  const hire = await until(async () => {
    const list = await api<{ content: { id: string; label: string }[] }>(page, 'GET', `/api/dossiers?dossierTypeCode=${TYPE}&size=50`)
    return list.content.find((x) => x.label.includes('Sarah Dubois'))
  })
  await d.goto(`/dossiers/${hire.id}`)
  await d.beat(2)
  await scrollTo(page, page.getByRole('heading', { name: /^Provisioning$/ }), 'start')
  await d.beat(2)
  await scrollTo(page, page.locator('.bpmn-viewer'), 'start')
  await d.beat(2.5)
  await scrollTo(page, page.getByRole('heading', { name: /^(Historique|History)$/ }), 'start')
  await d.beat(2.5)
  // La tâche en cours : la validation des accès par l'IT.
  await scrollTo(page, page.locator('.task-list'), 'center')
  await d.holdForVoice(0.3)
  await d.wrap(order)
})

test('sous-le-capot', async ({ page }) => {
  await underTheHood(capsule, page)
})
