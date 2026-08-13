import { expect, test, type Page } from '@playwright/test'

type MockOpts = {
  roles?: string[]
  withActiveLink?: boolean
}

const DOSSIER_ID = '11111111-1111-1111-1111-111111111111'
const LINK_ID = '22222222-2222-2222-2222-222222222222'

function dossierBody(overrides: Record<string, unknown> = {}) {
  return {
    id: DOSSIER_ID,
    tenantId: 'tenant_dev',
    reference: 'DOS-1',
    dossierTypeCode: 'CLAIM',
    label: 'Claim A',
    status: 'DRAFT',
    fields: { priority: 'NORMAL' },
    currentWorkflowLinkId: null as string | null,
    version: 0,
    createdBy: 'user-1',
    createdAt: '2026-08-12T00:00:00Z',
    updatedBy: 'user-1',
    updatedAt: '2026-08-12T00:00:00Z',
    closedBy: null,
    closedAt: null,
    ...overrides,
  }
}

async function mockSession(page: Page, opts: MockOpts = {}) {
  const roles = opts.roles ?? ['TENANT_ADMIN', 'WORKFLOW_ADMIN', 'DOSSIER_MANAGER']
  let linkId: string | null = opts.withActiveLink ? LINK_ID : null
  let mineTasks = [
    {
      taskId: 'task-1',
      name: 'Valider',
      assigneeId: 'user-1',
      createdAt: '2026-08-12T00:00:00Z',
      processInstanceId: 'pi-1',
      dossierId: DOSSIER_ID,
      dossierReference: 'DOS-1',
      linkId: LINK_ID,
    },
  ]
  let tasks = [
    {
      taskId: 'task-1',
      name: 'Review',
      assignee: 'user-1',
      taskDefinitionKey: 'review',
      createTime: '2026-08-12T00:00:00Z',
    },
  ]

  await page.route('**/api/auth/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'user-1',
        name: 'Ada',
        email: 'ada@example.com',
        roles,
      }),
    })
  })
  await page.route('**/api/notifications/stream', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      body: 'data: {"id":"n1","title":"Ping","body":"hello"}\n\n',
    })
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
            fieldDefinitions: [],
          },
        ]),
      })
      return
    }
    await route.fulfill({ status: 201, contentType: 'application/json', body: '{}' })
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
  await page.route('**/api/dossiers**', async (route) => {
    const url = route.request().url()
    const method = route.request().method()
    if (method === 'POST' && /\/api\/dossiers\/[^/]+\/workflows$/.test(new URL(url).pathname)) {
      const key = route.request().headers()['idempotency-key']
      expect(key).toBeTruthy()
      linkId = LINK_ID
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          id: LINK_ID,
          dossierId: DOSSIER_ID,
          tenantId: 'tenant_dev',
          processInstanceId: 'pi-1',
          processDefinitionKey: 'claimWorkflow',
          sequence: 1,
          status: 'ACTIVE',
          triggerRef: null,
          startedAt: '2026-08-12T00:00:00Z',
          endedAt: null,
          endReason: null,
          startedBy: 'user-1',
        }),
      })
      return
    }
    if (method === 'POST' && url.endsWith('/api/dossiers')) {
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(dossierBody()),
      })
      return
    }
    if (url.includes('/elements')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
      return
    }
    if (url.includes('/workflows') && method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(
          linkId
            ? [
                {
                  id: linkId,
                  dossierId: DOSSIER_ID,
                  tenantId: 'tenant_dev',
                  processInstanceId: 'pi-1',
                  processDefinitionKey: 'claimWorkflow',
                  sequence: 1,
                  status: 'ACTIVE',
                },
              ]
            : [],
        ),
      })
      return
    }
    if (url.includes('/status') && method === 'PATCH') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(dossierBody({ status: 'OUVERT', version: 1, currentWorkflowLinkId: linkId })),
      })
      return
    }
    if (method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(dossierBody({ currentWorkflowLinkId: linkId })),
      })
      return
    }
    await route.continue()
  })
  await page.route('**/api/workflow-links/**', async (route) => {
    const url = route.request().url()
    if (url.includes('/diagram')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          bpmnXml:
            '<?xml version="1.0"?><definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" id="Defs"><process id="P" isExecutable="true"><startEvent id="start"/></process></definitions>',
          activeIds: ['start'],
          completedIds: [],
          failedIds: [],
        }),
      })
      return
    }
    if (url.includes('/history')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
      return
    }
    if (url.includes('/reassign') && route.request().method() === 'POST') {
      await route.fulfill({ status: 204, body: '' })
      return
    }
    if (url.includes('/tasks')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(tasks) })
      return
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' })
  })
  await page.route('**/api/workflow-tasks/mine', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(mineTasks),
    })
  })
  await page.route('**/api/workflow-tasks/*/complete', async (route) => {
    tasks = []
    mineTasks = []
    await route.fulfill({ status: 204, body: '' })
  })
  await page.route('**/api/workflow-type-configs**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          id: '1',
          tenantId: 'tenant_dev',
          workflowKey: 'otherWorkflow',
          label: 'Autre',
          statusDefinitions: [],
          allowedDossierStatuses: [],
          onStartDossierStatus: null,
          observerUserIds: [],
        },
      ]),
    })
  })
  await page.route('**/api/document-templates**', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
  })
  await page.route('**/api/connectors**', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
  })
  await page.route('**/api/connector-definitions**', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
  })
}

async function openApp(page: Page) {
  await page.goto('./')
  await expect(page.getByText('FlowForge')).toBeVisible()
  await expect(page.getByText('Ada')).toBeVisible()
}

async function createAndOpenDossier(page: Page) {
  await page.getByRole('link', { name: 'Nouveau dossier' }).click()
  await page.locator('#dossier-label').fill('Claim A')
  await page.getByRole('button', { name: 'Créer' }).click()
  await expect(page.getByRole('heading', { name: 'Claim A' })).toBeVisible()
}

test('E2E-1 auth shell shows user', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
})

test('E2E-2 create dossier navigates to detail', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await createAndOpenDossier(page)
  await expect(page).toHaveURL(/dossiers\/11111111/)
})

test('E2E-3 patch status', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await createAndOpenDossier(page)
  await page.locator('.status-select select').selectOption('OUVERT')
  await page.getByRole('button', { name: 'Enregistrer statut' }).click()
  await expect(page.locator('.status-select select')).toHaveValue('OUVERT')
})

test('E2E-4 start workflow sends Idempotency-Key', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await createAndOpenDossier(page)
  await page.getByRole('button', { name: 'Démarrer workflow' }).click()
  await expect(page.getByRole('heading', { name: 'Démarrer un workflow' })).toBeVisible()
  await page.locator('.dialog button.primary').click()
  // getByText('Diagramme') is ambiguous: it also matches the "Diagramme indisponible (...)"
  // warning paragraph BpmnViewer renders before the diagram has loaded (strict-mode violation).
  await expect(page.getByRole('heading', { name: 'Diagramme' })).toBeVisible()
})

test('E2E-5 complete task from inbox with {}', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'Tâches' }).click()
  await expect(page.getByText('Valider')).toBeVisible()
  await page.getByRole('button', { name: 'Compléter' }).click()
  await expect(page.getByText('Aucune tâche assignée')).toBeVisible()
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

test('E2E-7 inbox lists mine tasks', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'Tâches' }).click()
  await expect(page.getByText('Valider')).toBeVisible()
  await expect(page.getByText('DOS-1')).toBeVisible()
})

test('E2E-8 notifications drawer', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('button', { name: /Notifications/ }).click()
  await expect(page.getByText('Ping')).toBeVisible()
})

test('E2E-9 supervision has no fake KPI and no Rapports', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'Supervision' }).click()
  await expect(page.getByRole('heading', { name: 'Supervision' })).toBeVisible()
  await expect(page.getByText('Rapports')).toHaveCount(0)
  await expect(page.getByText('KPI')).toHaveCount(0)
})

test('E2E-10 admin types smoke', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'Types' }).click()
  await expect(page.getByRole('heading', { name: 'Types de dossier' })).toBeVisible()
})
