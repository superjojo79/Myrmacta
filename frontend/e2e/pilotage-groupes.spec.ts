// E2E « pilotage » — groupes d'utilisateurs et tâches de groupe (TODO §114, issue #190).
import { expect, test } from '@playwright/test'
import { createAndOpenDossier, mockSession, openApp } from './support/pilotage'

test('E2E-GROUP-1 a member takes the free task of his group from the inbox, then can complete it', async ({ page }) => {
  await mockSession(page, { roles: ['DOSSIER_MANAGER'], groupTask: true })
  await openApp(page)
  await page.getByRole('link', { name: 'Tâches' }).click()
  await expect(page.getByText('Trier le courrier')).toBeVisible()
  await expect(page.getByTestId('task-group')).toContainText('À prendre')
  await expect(page.getByRole('button', { name: 'Compléter' })).toHaveCount(0)
  const claim = page.waitForRequest(
    (r) => r.method() === 'POST' && /\/api\/workflow-links\/[^/]+\/tasks\/task-group\/claim$/.test(new URL(r.url()).pathname),
  )
  await page.getByRole('button', { name: 'Prendre' }).click()
  await claim
  await expect(page.getByTestId('task-group')).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Compléter' })).toBeVisible()
})

test('E2E-GROUP-2 an administrator creates a group and adds a known user to it', async ({ page }) => {
  await mockSession(page, { roles: ['TENANT_ADMIN'] })
  await openApp(page)
  await page.getByRole('link', { name: 'Groupes' }).click()
  await expect(page.getByRole('heading', { name: "Groupes d'utilisateurs" })).toBeVisible()
  await expect(page.getByTestId('group-COMPTABILITE')).toContainText('/services/compta')

  await page.getByTestId('group-code').fill('JURIDIQUE')
  await page.getByTestId('group-label').fill('Service juridique')
  await page.getByRole('button', { name: 'Créer' }).click()
  const juridique = page.getByTestId('group-JURIDIQUE')
  await expect(juridique).toContainText('Aucun membre listé.')

  await juridique.getByTestId('group-member-select-JURIDIQUE').selectOption('user-2')
  await juridique.getByRole('button', { name: 'Ajouter' }).click()
  await expect(juridique).toContainText('Grace Hopper (grace@example.com)')
  await expect(juridique).not.toContainText('Aucun membre listé.')
})

test('E2E-GROUP-3 a manager has no access to the groups screen', async ({ page }) => {
  await mockSession(page, { roles: ['DOSSIER_MANAGER'] })
  await openApp(page)
  await expect(page.getByRole('link', { name: 'Groupes' })).toHaveCount(0)
})

test('E2E-GROUP-4 a dossier editor reassigns a free task to another group from the dossier page', async ({ page }) => {
  await mockSession(page, { withActiveLink: true, roles: ['DOSSIER_MANAGER'], freeTask: true })
  await openApp(page)
  await createAndOpenDossier(page)
  await expect(page.getByTestId('task-groups')).toContainText('Mail room')
  await page.getByTestId('reassign-group-select').selectOption('JURIDIQUE')
  const reassign = page.waitForRequest(
    (r) => r.method() === 'POST' && /\/tasks\/task-1\/reassign-group$/.test(new URL(r.url()).pathname),
  )
  await page.getByTestId('reassign-group').click()
  expect((await reassign).postDataJSON()).toEqual({ groupCode: 'JURIDIQUE' })
  await expect(page.getByTestId('task-groups')).toContainText('Service juridique')
})
