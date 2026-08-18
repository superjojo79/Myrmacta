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
  let elements: Array<Record<string, unknown>> = []

  await page.route('**/api/documents/preview/pdf**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/pdf',
      body: Buffer.from('%PDF-1.4 preview'),
    })
  })
  await page.route('**/api/documents/preview**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'text/html;charset=UTF-8',
      body: '<html><body><div class="page">Aperçu mock</div></body></html>',
    })
  })
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
    if (url.includes('/relations') && method === 'POST') {
      const posted = route.request().postDataJSON() as { targetDossierId: string; type: string }
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'rel-1',
          type: posted.type,
          direction: 'OUTGOING',
          relatedDossier: {
            id: posted.targetDossierId,
            reference: 'DOS-2',
            label: 'Dossier lié',
            dossierTypeCode: 'FAC',
            dossierTypeLabel: 'Facture',
            status: 'OUVERT',
            createdAt: '2026-08-15T00:00:00Z',
            updatedAt: '2026-08-15T00:00:00Z',
          },
          createdBy: 'user-1',
          createdAt: '2026-08-15T00:00:00Z',
        }),
      })
      return
    }
    if (url.includes('/relations')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: 'rel-existing',
            type: 'PARENT_CHILD',
            direction: 'INCOMING',
            relatedDossier: {
              id: '33333333-3333-3333-3333-333333333333',
              reference: 'DOS-2',
              label: 'Dossier lié',
              dossierTypeCode: 'FAC',
              dossierTypeLabel: 'Facture',
              status: 'OUVERT',
              createdAt: '2026-08-15T00:00:00Z',
              updatedAt: '2026-08-15T00:00:00Z',
            },
            createdBy: 'user-1',
            createdAt: '2026-08-15T00:00:00Z',
          },
        ]),
      })
      return
    }
    if (url.endsWith('/elements/attachment') && method === 'POST') {
      const raw = route.request().postData() ?? ''
      const labelMatch = raw.match(/name="label"\r?\n\r?\n([^\r\n]*)/)
      const filenameMatch = raw.match(/filename="([^"]*)"/)
      const created = {
        id: `att-${elements.length + 1}`,
        type: 'ATTACHMENT',
        label: labelMatch?.[1] ?? 'Pièce jointe',
        addedBy: 'user-1',
        addedAt: '2026-08-16T00:00:00Z',
        workflowStepId: null,
        metadata: { filename: filenameMatch?.[1] ?? 'file.pdf', contentType: 'application/pdf', sizeBytes: 4 },
        downloadable: true,
      }
      elements = [...elements, created]
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(created) })
      return
    }
    if (/\/elements$/.test(new URL(url).pathname) && method === 'POST') {
      const posted = route.request().postDataJSON() as {
        type: string
        label: string
        metadata: Record<string, unknown>
      }
      const created = {
        id: `el-${elements.length + 1}`,
        type: posted.type,
        label: posted.label,
        addedBy: 'user-1',
        addedAt: '2026-08-16T00:00:00Z',
        workflowStepId: null,
        metadata: posted.metadata,
        downloadable: false,
      }
      elements = [...elements, created]
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(created) })
      return
    }
    if (url.includes('/elements')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(elements) })
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
      const parsed = new URL(url)
      const path = parsed.pathname.replace(/\/$/, '')
      // Paginated list: GET /api/dossiers (no id, no ?ref=)
      if (path.endsWith('/api/dossiers') && !parsed.searchParams.has('ref')) {
        const body = dossierBody({ currentWorkflowLinkId: linkId })
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            content: [
              {
                id: body.id,
                reference: body.reference,
                label: body.label,
                dossierTypeCode: body.dossierTypeCode,
                dossierTypeLabel: 'Sinistre',
                status: body.status,
                createdAt: body.createdAt,
                updatedAt: body.updatedAt,
              },
              {
                id: '33333333-3333-3333-3333-333333333333',
                reference: 'DOS-2',
                label: 'Dossier lié',
                dossierTypeCode: 'FAC',
                dossierTypeLabel: 'Facture',
                status: 'OUVERT',
                createdAt: body.createdAt,
                updatedAt: body.updatedAt,
              },
            ],
            page: Number(parsed.searchParams.get('page') ?? 0),
            size: Number(parsed.searchParams.get('size') ?? 20),
            totalElements: 2,
            totalPages: 1,
          }),
        })
        return
      }
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
  await expect(page.getByRole('button', { name: 'Filtrer' })).toBeVisible()
  await expect(page.getByText('DOS-1')).toBeVisible()
  await expect(page.getByText('Rapports')).toHaveCount(0)
  await expect(page.getByText('KPI')).toHaveCount(0)
})

test('E2E-11 link dossier from detail without reload', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await createAndOpenDossier(page)
  await expect(page.getByRole('heading', { name: 'Dossiers liés' })).toBeVisible()
  await expect(page.getByText('Dossier lié')).toBeVisible()
  await page.getByRole('button', { name: 'Lier un dossier' }).click()
  await expect(page.getByRole('heading', { name: 'Lier un dossier' })).toBeVisible()
  await page.getByPlaceholder('préfixe DOS-…').fill('DOS')
  await page.getByRole('button', { name: 'Rechercher' }).click()
  await page.getByRole('button', { name: /Dossier lié · DOS-2/ }).click()
  await page.locator('.dialog select').selectOption('LINKED')
  await page.locator('.dialog button.primary').click()
  await expect(page.locator('.relations-list strong', { hasText: /^Lié$/ })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Lier un dossier' })).toHaveCount(0)
})

test('E2E-12 navigate to related dossier from relations list', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await createAndOpenDossier(page)
  await page.getByRole('link', { name: 'Dossier lié' }).click()
  await expect(page).toHaveURL(/dossiers\/33333333/)
})

test('E2E-15 add a NOTE element from detail without reload', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await createAndOpenDossier(page)
  await expect(page.getByRole('heading', { name: 'Éléments' })).toBeVisible()
  await page.getByRole('button', { name: 'Ajouter' }).click()
  await expect(page.getByRole('heading', { name: 'Ajouter un élément' })).toBeVisible()
  await page.locator('.dialog select').selectOption('NOTE')
  await page.locator('.dialog input[type="text"]').first().fill('Rappel client')
  await page.locator('.dialog textarea').fill('A relancer le 20/08')
  await page.locator('.dialog button.primary').click()
  await expect(page.getByRole('heading', { name: 'Ajouter un élément' })).toHaveCount(0)
  await expect(page.locator('.elements-list strong', { hasText: 'Rappel client' })).toBeVisible()
  await expect(page.getByText('A relancer le 20/08')).toBeVisible()
})

test('E2E-16 add a LINK element shows the URL without fetching it', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await createAndOpenDossier(page)
  await page.getByRole('button', { name: 'Ajouter' }).click()
  await page.locator('.dialog select').selectOption('LINK')
  await page.locator('.dialog input[type="text"]').first().fill('Ticket externe')
  await page.locator('.dialog input[type="url"]').fill('https://example.com/ticket/1')
  await page.locator('.dialog button.primary').click()
  const link = page.locator('.elements-list a.elements-list__link')
  await expect(link).toBeVisible()
  await expect(link).toHaveAttribute('href', 'https://example.com/ticket/1')
})

test('E2E-17 upload an ATTACHMENT element from detail', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await createAndOpenDossier(page)
  await page.getByRole('button', { name: 'Ajouter' }).click()
  await page.locator('.dialog input[type="text"]').first().fill('Justificatif')
  await page.locator('.dialog input[type="file"]').setInputFiles({
    name: 'justificatif.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-content'),
  })
  await page.locator('.dialog button.primary').click()
  await expect(page.getByRole('heading', { name: 'Ajouter un élément' })).toHaveCount(0)
  await expect(page.locator('.elements-list strong', { hasText: 'Justificatif' })).toBeVisible()
  await expect(page.locator('.elements-list li', { hasText: 'Justificatif' }).getByText('Télécharger')).toBeVisible()
})

test('E2E-19 oversized attachment upload shows an error, no crash', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await createAndOpenDossier(page)
  await page.route('**/api/dossiers/**/elements/attachment', async (route) => {
    await route.fulfill({
      status: 413,
      contentType: 'application/problem+json',
      body: JSON.stringify({ status: 413, title: 'Upload too large', detail: 'The uploaded file exceeds the maximum allowed size' }),
    })
  })
  await page.getByRole('button', { name: 'Ajouter' }).click()
  await page.locator('.dialog input[type="text"]').first().fill('Trop gros')
  await page.locator('.dialog input[type="file"]').setInputFiles({
    name: 'big.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-content'),
  })
  await page.locator('.dialog button.primary').click()
  await expect(page.getByText('The uploaded file exceeds the maximum allowed size')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Ajouter un élément' })).toBeVisible()
})

test('E2E-18 viewer cannot add an element', async ({ page }) => {
  await mockSession(page, { roles: ['DOSSIER_VIEWER'] })
  await openApp(page)
  await createAndOpenDossier(page)
  await expect(page.getByRole('heading', { name: 'Éléments' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Ajouter' })).toHaveCount(0)
})

test('E2E-13 viewer cannot link a dossier', async ({ page }) => {
  await mockSession(page, { roles: ['DOSSIER_VIEWER'] })
  await openApp(page)
  await createAndOpenDossier(page)
  await expect(page.getByRole('heading', { name: 'Dossiers liés' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Lier un dossier' })).toHaveCount(0)
})

test('E2E-14 workflow visualization page still shows related dossiers', async ({ page }) => {
  await mockSession(page, { withActiveLink: true })
  await openApp(page)
  await createAndOpenDossier(page)
  await expect(page.getByRole('heading', { name: 'Dossiers liés' })).toBeVisible()
  await expect(page.getByText('Parent')).toBeVisible()
  await expect(page.getByRole('link', { name: 'Dossier lié' })).toBeVisible()
})

test('E2E-10 admin types smoke', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'Types' }).click()
  await expect(page.getByRole('heading', { name: 'Types de dossier' })).toBeVisible()
})

test('E2E-20 template preview html and pdf', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'Templates' }).click()
  await expect(page.getByRole('heading', { name: 'Templates documents' })).toBeVisible()
  await page.locator('form textarea').fill('<p>Bonjour</p>')
  await expect(page.locator('iframe[title="Aperçu HTML"]')).toBeVisible()
  await page.getByRole('button', { name: 'Aperçu PDF' }).click()
  await expect(page.locator('iframe[title="Aperçu PDF"]')).toBeVisible()
})

test('E2E-21 invalid variables JSON does not request PDF preview', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'Templates' }).click()
  await expect(page.getByRole('heading', { name: 'Templates documents' })).toBeVisible()
  await page.locator('form textarea').fill('<p>Bonjour</p>')
  await page.getByLabel('Variables JSON').fill('{not-json')
  await expect(page.getByText('JSON invalide')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Aperçu PDF' })).toBeDisabled()
  await expect(page.locator('iframe[title="Aperçu PDF"]')).toHaveCount(0)
})
