// E2E « pilotage » — Tâches : boîte de réception, assignation, incidents (TODO §95 : découpage de pilotage.spec.ts par écran).
import { expect, test } from '@playwright/test'
import {
  DOSSIER_ID,
  LINK_ID,
  envelope,
  mockSession,
  openApp,
  createAndOpenDossier,
} from './support/pilotage'

test('E2E-5 complete task from inbox with {}', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'Tâches' }).click()
  await expect(page.getByText('Valider')).toBeVisible()
  await page.getByRole('button', { name: 'Compléter' }).click()
  await expect(page.getByText('Aucune tâche assignée')).toBeVisible()
})

test('E2E-INCIDENT-1 an administrator sees the incident with its message and retries it (TODO §94)', async ({ page }) => {
  await mockSession(page, { withActiveLink: true, withIncident: true, roles: ['TENANT_ADMIN', 'WORKFLOW_ADMIN'] })
  await openApp(page)
  await createAndOpenDossier(page)
  const banner = page.getByTestId('incident-banner')
  await expect(banner).toContainText('E-mail : refus')
  await expect(banner).toContainText('single e-mail address')
  const retry = page.waitForRequest(
    (r) => r.method() === 'POST' && /\/incidents\/job-1\/retry$/.test(new URL(r.url()).pathname),
  )
  await banner.getByRole('button', { name: 'Rejouer' }).click()
  await retry
  await expect(page.getByTestId('incident-banner')).toHaveCount(0)
})

test('E2E-INCIDENT-2 a manager sees the incident without message nor retry (TODO §94)', async ({ page }) => {
  await mockSession(page, { withActiveLink: true, withIncident: true, roles: ['DOSSIER_MANAGER'] })
  await openApp(page)
  await createAndOpenDossier(page)
  const banner = page.getByTestId('incident-banner')
  await expect(banner).toContainText('E-mail : refus')
  await expect(banner).not.toContainText('single e-mail address')
  await expect(banner.getByRole('button', { name: 'Rejouer' })).toHaveCount(0)
})

test('E2E-INCIDENT-3 the admin incidents screen lists failures with a link to the dossier (TODO §94)', async ({ page }) => {
  await mockSession(page, { withActiveLink: true, withIncident: true, roles: ['TENANT_ADMIN', 'WORKFLOW_ADMIN'] })
  await openApp(page)
  await page.getByRole('link', { name: 'Incidents' }).click()
  await expect(page.getByRole('heading', { name: 'Étapes en échec' })).toBeVisible()
  const row = page.getByTestId('incident-admin-row')
  await expect(row).toContainText('DOS-TEST-1')
  await expect(row).toContainText('E-mail : refus')
  await row.getByRole('button', { name: 'Rejouer' }).click()
  await expect(page.getByText('Aucune étape en échec.')).toBeVisible()
})

test('E2E-6 reassign visible for admin only', async ({ page }) => {
  await mockSession(page, { withActiveLink: true, roles: ['TENANT_ADMIN', 'WORKFLOW_ADMIN'] })
  await openApp(page)
  await createAndOpenDossier(page)
  await expect(page.getByRole('button', { name: 'Réassigner' })).toBeVisible()
})

test('E2E-6b manager cannot reassign', async ({ page }) => {
  await mockSession(page, { withActiveLink: true, roles: ['DOSSIER_MANAGER'] })
  await openApp(page)
  await createAndOpenDossier(page)
  await expect(page.getByRole('button', { name: 'Réassigner' })).toHaveCount(0)
})

test('E2E-ASSIGN-1 a dossier editor takes a free task with M’assigner (D-ASSIGN-CLAIM)', async ({ page }) => {
  await mockSession(page, { withActiveLink: true, roles: ['DOSSIER_MANAGER'], freeTask: true })
  await openApp(page)
  await createAndOpenDossier(page)
  await expect(page.getByText('Assignee : —')).toBeVisible()
  await page.getByRole('button', { name: 'M’assigner' }).click()
  await expect(page.getByText('Assignee : vous')).toBeVisible()
  await expect(page.getByRole('button', { name: 'M’assigner' })).toHaveCount(0)
})

test('E2E-ASSIGN-2 admin reassigns by picking a known user, shown by name (D-ASSIGN-DIR)', async ({ page }) => {
  await mockSession(page, { withActiveLink: true, roles: ['TENANT_ADMIN', 'WORKFLOW_ADMIN'] })
  await openApp(page)
  await createAndOpenDossier(page)
  await expect(page.getByText('Assignee : vous')).toBeVisible()
  await page.getByRole('button', { name: 'Réassigner' }).click()
  await page.getByTestId('reassign-user').selectOption({ label: 'Grace Hopper (grace@example.com)' })
  await page.getByRole('button', { name: 'Confirmer' }).click()
  await expect(page.getByText('Assignee : Grace Hopper')).toBeVisible()
})

test('E2E-7 inbox lists mine tasks', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'Tâches' }).click()
  await expect(page.getByText('Valider')).toBeVisible()
  await expect(page.getByText('DOS-1')).toBeVisible()
})

test('E2E-PAGE-2 tasks inbox pager writes page query', async ({ page }) => {
  await mockSession(page)
  await page.route('**/api/workflow-tasks/mine*', async (route) => {
    const url = new URL(route.request().url())
    const pageNo = Number(url.searchParams.get('page') || '0')
    const task = {
      taskId: pageNo === 0 ? 'task-1' : 'task-2',
      name: pageNo === 0 ? 'Valider' : 'Vérifier',
      assigneeId: 'user-1',
      createdAt: '2026-08-12T00:00:00Z',
      processInstanceId: 'pi-1',
      dossierId: DOSSIER_ID,
      dossierReference: 'DOS-1',
      linkId: LINK_ID,
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope([task], { page: pageNo, size: 20, totalElements: 21, totalPages: 2 })),
    })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Tâches' }).click()
  await expect(page.getByText('Valider')).toBeVisible()
  await page.getByTestId('admin-pager-next').click()
  await expect(page).toHaveURL(/[?&]page=1/)
  await expect(page.getByText('Vérifier')).toBeVisible()
})
