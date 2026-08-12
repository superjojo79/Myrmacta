import { expect, test, type Page } from '@playwright/test'

async function mockSession(page: Page) {
  await page.route('**/api/auth/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'user-1',
        name: 'Ada',
        email: 'ada@example.com',
        roles: ['TENANT_ADMIN', 'WORKFLOW_ADMIN', 'DOSSIER_MANAGER'],
      }),
    })
  })
  await page.route('**/api/notifications/stream', async (route) => {
    await route.fulfill({ status: 200, contentType: 'text/event-stream', body: '' })
  })
  await page.route('**/api/dossier-types', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: 't1',
            tenantId: 'tenant_dev',
            code: 'CLAIM',
            label: 'Sinistre',
            workflowKey: 'claimWorkflow',
            active: true,
            allowUnknownFields: false,
            fieldDefinitions: [
              {
                name: 'priority',
                label: 'Priorité',
                type: 'STRING',
                required: true,
                defaultValue: 'NORMAL',
                options: [],
                group: 'details',
                sensitive: false,
              },
            ],
          },
        ]),
      })
      return
    }
    await route.fulfill({ status: 201, contentType: 'application/json', body: '{}' })
  })
  await page.route('**/api/dossiers**', async (route) => {
    const url = route.request().url()
    const method = route.request().method()
    if (method === 'POST' && url.endsWith('/api/dossiers')) {
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          id: '11111111-1111-1111-1111-111111111111',
          tenantId: 'tenant_dev',
          reference: 'DOS-1',
          dossierTypeCode: 'CLAIM',
          label: 'Claim A',
          status: 'DRAFT',
          fields: { priority: 'NORMAL' },
          currentWorkflowLinkId: null,
          version: 0,
          createdBy: 'user-1',
          createdAt: '2026-08-12T00:00:00Z',
          updatedBy: 'user-1',
          updatedAt: '2026-08-12T00:00:00Z',
          closedBy: null,
          closedAt: null,
        }),
      })
      return
    }
    if (url.includes('/elements')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
      return
    }
    if (url.includes('/workflows') && method === 'GET') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
      return
    }
    if (url.includes('/status') && method === 'PATCH') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: '11111111-1111-1111-1111-111111111111',
          tenantId: 'tenant_dev',
          reference: 'DOS-1',
          dossierTypeCode: 'CLAIM',
          label: 'Claim A',
          status: 'OUVERT',
          fields: { priority: 'NORMAL' },
          currentWorkflowLinkId: null,
          version: 1,
          createdBy: 'user-1',
          createdAt: '2026-08-12T00:00:00Z',
          updatedBy: 'user-1',
          updatedAt: '2026-08-12T00:00:00Z',
          closedBy: null,
          closedAt: null,
        }),
      })
      return
    }
    if (method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: '11111111-1111-1111-1111-111111111111',
          tenantId: 'tenant_dev',
          reference: 'DOS-1',
          dossierTypeCode: 'CLAIM',
          label: 'Claim A',
          status: 'DRAFT',
          fields: { priority: 'NORMAL' },
          currentWorkflowLinkId: null,
          version: 0,
          createdBy: 'user-1',
          createdAt: '2026-08-12T00:00:00Z',
          updatedBy: 'user-1',
          updatedAt: '2026-08-12T00:00:00Z',
          closedBy: null,
          closedAt: null,
        }),
      })
      return
    }
    await route.continue()
  })
  await page.route('**/api/dossier-types/CLAIM', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 't1',
        tenantId: 'tenant_dev',
        code: 'CLAIM',
        label: 'Sinistre',
        workflowKey: 'claimWorkflow',
        active: true,
        allowUnknownFields: false,
        fieldDefinitions: [],
      }),
    })
  })
  await page.route('**/api/workflow-tasks/mine', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          taskId: 'task-1',
          name: 'Valider',
          assigneeId: 'user-1',
          createdAt: '2026-08-12T00:00:00Z',
          processInstanceId: 'pi-1',
          dossierId: '11111111-1111-1111-1111-111111111111',
          dossierReference: 'DOS-1',
          linkId: '22222222-2222-2222-2222-222222222222',
        },
      ]),
    })
  })
  await page.route('**/api/workflow-tasks/*/complete', async (route) => {
    await route.fulfill({ status: 204, body: '' })
  })
  await page.route('**/api/workflow-type-configs**', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
  })
  await page.route('**/api/document-templates**', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
  })
  await page.route('**/api/connectors**', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
  })
}

/** base:'./' casse les deep-links (assets relatifs) — démarrer à / puis naviguer côté client. */
async function openApp(page: Page) {
  await page.goto('./')
  await expect(page.getByText('FlowForge')).toBeVisible()
  await expect(page.getByText('Ada')).toBeVisible()
}

test.beforeEach(async ({ page }) => {
  await mockSession(page)
})

test('E2E-1 auth shell shows user', async ({ page }) => {
  await openApp(page)
})

test('E2E-2 create dossier navigates to detail', async ({ page }) => {
  await openApp(page)
  await page.getByRole('link', { name: 'Nouveau dossier' }).click()
  await expect(page.getByRole('heading', { name: 'Nouveau dossier' })).toBeVisible()
  await page.locator('#dossier-label').fill('Claim A')
  await page.getByRole('button', { name: 'Créer' }).click()
  await expect(page).toHaveURL(/dossiers\/11111111/)
  await expect(page.getByRole('heading', { name: 'Claim A' })).toBeVisible()
})

test('E2E-3 patch status', async ({ page }) => {
  await openApp(page)
  await page.getByRole('link', { name: 'Nouveau dossier' }).click()
  await page.locator('#dossier-label').fill('Claim A')
  await page.getByRole('button', { name: 'Créer' }).click()
  await expect(page.getByRole('heading', { name: 'Claim A' })).toBeVisible()
  await page.locator('.status-select select').selectOption('OUVERT')
  await page.getByRole('button', { name: 'Enregistrer statut' }).click()
  await expect(page.locator('.status-select select')).toHaveValue('OUVERT')
})

test('E2E-7 inbox lists mine tasks', async ({ page }) => {
  await openApp(page)
  await page.getByRole('link', { name: 'Tâches' }).click()
  await expect(page.getByText('Valider')).toBeVisible()
  await expect(page.getByText('DOS-1')).toBeVisible()
})

test('E2E-9 supervision has no fake KPI and no Rapports', async ({ page }) => {
  await openApp(page)
  await page.getByRole('link', { name: 'Supervision' }).click()
  await expect(page.getByRole('heading', { name: 'Supervision' })).toBeVisible()
  await expect(page.getByText('Rapports')).toHaveCount(0)
  await expect(page.getByText('KPI')).toHaveCount(0)
})

test('E2E-10 admin types smoke', async ({ page }) => {
  await openApp(page)
  await page.getByRole('link', { name: 'Types' }).click()
  await expect(page.getByRole('heading', { name: 'Types de dossier' })).toBeVisible()
})
