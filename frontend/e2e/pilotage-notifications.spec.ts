// E2E « pilotage » — Notifications : tiroir, boîte persistée, webhooks (TODO §95 : découpage de pilotage.spec.ts par écran).
import { expect, test } from '@playwright/test'
import {
  envelope,
  mockSession,
  openApp,
} from './support/pilotage'

test('E2E-8 notifications drawer', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('button', { name: /Notifications/ }).click()
  await expect(page.getByText('Ping')).toBeVisible()
})

test('E2E-NOTIF-1 a notification missed while disconnected is visible via REST backfill', async ({ page }) => {
  // TODO §66 — the whole point of persisting user_notification: unlike E2E-8 (which only proves
  // the live SSE path), this notification never arrives over the stream at all, only via the
  // GET backfill issued on mount — proving the drawer no longer depends on being connected at
  // the moment an event fires.
  await mockSession(page)
  await page.route(/\/api\/notifications(\?|$)/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(
        envelope([
          {
            id: 'missed-1',
            payload: { type: 'TASK_REASSIGNED', title: 'Tâche réassignée', body: 'Pendant votre absence' },
            createdAt: '2026-09-22T08:00:00Z',
            readAt: null,
            read: false,
          },
        ]),
      ),
    })
  })
  await openApp(page)
  await page.getByRole('button', { name: /Notifications/ }).click()
  await expect(page.getByText('Tâche réassignée')).toBeVisible()
})

test('E2E-NOTIF-2 marking a notification read calls the server and persists the state', async ({ page }) => {
  await mockSession(page)
  let readRequested = false
  await page.route(/\/api\/notifications(\?|$)/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(
        envelope([
          {
            id: 'to-read-1',
            payload: { type: 'DOCUMENT_GENERATED', title: 'Document généré', body: 'sur DOS-1' },
            createdAt: '2026-09-22T08:00:00Z',
            readAt: null,
            read: false,
          },
        ]),
      ),
    })
  })
  await page.route('**/api/notifications/to-read-1/read', async (route) => {
    readRequested = true
    await route.fulfill({ status: 204, body: '' })
  })
  await openApp(page)
  await page.getByRole('button', { name: /Notifications/ }).click()
  await expect(page.getByText('Document généré')).toBeVisible()
  await page.getByRole('button', { name: /Document généré/ }).click()
  // la requête part après le clic, de façon asynchrone : on l'attend au lieu de la supposer déjà partie
  await expect.poll(() => readRequested).toBe(true)
})

test('E2E-NOTIF-3 the 3 new producer payload types render correctly in the drawer', async ({ page }) => {
  // Covers what UT-NOTIF-11/12/14 already prove at the backend (the right recipients get sent
  // TASK_REASSIGNED/TASK_COMPLETED_BY_OTHER/DOCUMENT_GENERATED) from the other end: once such a
  // row exists, the drawer renders its title/body correctly in a real browser — same rendering
  // path regardless of which producer wrote the row, so one test covering all 3 payload shapes
  // is equivalent to testing each producer's rendering separately.
  await mockSession(page)
  await page.route(/\/api\/notifications(\?|$)/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(
        envelope([
          {
            id: 'reassigned-1',
            payload: { type: 'TASK_REASSIGNED', title: 'Tâche réassignée', body: 'vous a été réassignée' },
            createdAt: '2026-09-22T08:02:00Z',
            readAt: null,
            read: false,
          },
          {
            id: 'completed-1',
            payload: { type: 'TASK_COMPLETED_BY_OTHER', title: 'Tâche terminée', body: 'par un autre participant' },
            createdAt: '2026-09-22T08:01:00Z',
            readAt: null,
            read: false,
          },
          {
            id: 'generated-1',
            payload: { type: 'DOCUMENT_GENERATED', title: 'Document généré', body: 'sur le dossier suivi' },
            createdAt: '2026-09-22T08:00:00Z',
            readAt: null,
            read: false,
          },
        ]),
      ),
    })
  })
  await openApp(page)
  await page.getByRole('button', { name: /Notifications/ }).click()
  await expect(page.getByText('Tâche réassignée')).toBeVisible()
  await expect(page.getByText('Tâche terminée')).toBeVisible()
  await expect(page.getByText('Document généré')).toBeVisible()
})

test('E2E-WEBHOOK-1 tenant admin lists and creates a notification webhook', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'Webhooks' }).click()
  await expect(page.getByRole('heading', { name: 'Webhooks de notification' })).toBeVisible()
  await expect(page.getByTestId('webhook-list')).toContainText('Hook A')
  await expect(page.getByTestId('webhook-list')).toContainText('secret configuré')
  await page.getByTestId('webhook-name').fill('Ops')
  await page.getByTestId('webhook-url').fill('https://example.com/ops')
  await page.getByTestId('webhook-secret').fill('plain-secret')
  await page.getByTestId('webhook-create-form').getByRole('button', { name: 'Créer' }).click()
  await expect(page.getByTestId('webhook-list')).toContainText('Ops')
  await expect(page.getByTestId('webhook-secret')).toHaveValue('')
})

test('E2E-WEBHOOK-2 non-admin does not see webhooks nav', async ({ page }) => {
  await mockSession(page, { roles: ['DOSSIER_VIEWER'] })
  await openApp(page)
  await expect(page.getByRole('link', { name: 'Webhooks' })).toHaveCount(0)
})
