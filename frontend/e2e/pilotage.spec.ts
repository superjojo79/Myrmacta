import { expect, test, type Page } from '@playwright/test'

type MockOpts = {
  roles?: string[]
  withActiveLink?: boolean
  dossierStatus?: string
}

const DOSSIER_ID = '11111111-1111-1111-1111-111111111111'
const LINK_ID = '22222222-2222-2222-2222-222222222222'

function envelope(content: unknown[], extras: Record<string, number> = {}) {
  const size = extras.size ?? 20
  const totalElements = extras.totalElements ?? content.length
  return {
    content,
    page: extras.page ?? 0,
    size,
    totalElements,
    totalPages: extras.totalPages ?? (totalElements === 0 ? 0 : Math.ceil(totalElements / size)),
  }
}

const CLAIM_TYPE = {
  id: 't1',
  tenantId: 'tenant_dev',
  code: 'CLAIM',
  label: 'Sinistre',
  workflowKey: 'claimWorkflow',
  active: true,
  allowUnknownFields: false,
  retentionDays: null,
  retentionNever: false,
  fieldDefinitions: [
    {
      name: 'priority',
      label: 'Priority',
      type: 'STRING',
      required: true,
      defaultValue: 'NORMAL',
      options: [],
      group: null,
      sensitive: false,
    },
  ],
  slaTargetHours: 48,
}

const SLA_REPORT = {
  from: '2026-02-13T00:00:00Z',
  to: '2026-03-15T00:00:00Z',
  complianceTargetPercent: 95,
  compliancePercent: 94.2,
  overdueCount: 148,
  meanProcessingDays: 6.4,
  medianProcessingDays: 4.1,
  meanDeltaVsPreviousDays: -0.8,
  typeShares: [{ code: 'CLAIM', label: 'Sinistre', volume: 10, percent: 100 }],
  stepDurationsHours: [{ name: 'Instruction', meanHours: 37.6 }],
  managerLoads: [
    { assigneeId: null, displayName: 'Non assignés', inProgress: 48, overdue: 11, slaPercent: null },
  ],
  types: [
    {
      typeCode: 'CLAIM',
      typeLabel: 'Sinistre auto',
      workflowKey: 'instruction-sinistre',
      volume: 1042,
      meanDays: 7.1,
      slaPercent: 92.4,
      producedDocCount: 2108,
      volumeTrendPercent: 4,
    },
  ],
}

const OTHER_WORKFLOW = {
  id: '1',
  tenantId: 'tenant_dev',
  workflowKey: 'otherWorkflow',
  label: 'Autre',
  statusDefinitions: [],
  allowedDossierStatuses: [],
  onStartDossierStatus: null,
  observerUserIds: [],
}

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
    purgeEligibleAt: null,
    ...overrides,
  }
}

async function mockSession(page: Page, opts: MockOpts = {}) {
  const roles = opts.roles ?? ['TENANT_ADMIN', 'WORKFLOW_ADMIN', 'DOSSIER_MANAGER']
  // TODO §36: the mocked backend now mirrors DossierAccessPolicy — canEdit is a server-computed
  // flag, not re-derived from the role on the frontend. Approximate it here the same way the
  // fixture ACLs would: TENANT_ADMIN/WORKFLOW_ADMIN always bypass, DOSSIER_MANAGER acts as if it
  // held an EDIT grant (no ACL model in these mocks), everything else (viewers) cannot edit.
  const canEdit = roles.some((role) => ['TENANT_ADMIN', 'WORKFLOW_ADMIN', 'DOSSIER_MANAGER'].includes(role))
  let dossierStatus = opts.dossierStatus ?? 'DRAFT'
  let purgeItems: Array<Record<string, unknown>> = [
    {
      id: 'po-1',
      dossierId: DOSSIER_ID,
      status: 'FAILED',
      origin: 'RETENTION',
      eventType: 'DOSSIER_PURGE_REQUESTED',
      createdAt: '2026-08-19T10:00:00Z',
      lastError: 'S3 timeout',
    },
  ]
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
      slaRemainingHours: 12,
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
  await page.route(/\/api\/dossier-types(?:\?|$)/, async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(envelope([CLAIM_TYPE])),
      })
      return
    }
    await route.fulfill({ status: 201, contentType: 'application/json', body: '{}' })
  })
  await page.route('**/api/tenant/retention-settings', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 404,
        contentType: 'application/json',
        body: JSON.stringify({
          title: 'Not found',
          status: 404,
          detail: 'Retention settings not found',
        }),
      })
      return
    }
    if (route.request().method() === 'PUT') {
      const body = route.request().postDataJSON() as { defaultRetentionDays: number }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          tenantId: 'tenant_dev',
          defaultRetentionDays: body.defaultRetentionDays,
          updatedAt: '2026-08-19T00:00:00Z',
          updatedBy: 'admin',
        }),
      })
      return
    }
    await route.fulfill({ status: 405, body: '' })
  })
  await page.route('**/api/tenant/sla-settings', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 404,
        contentType: 'application/json',
        body: JSON.stringify({
          title: 'SLA settings not found',
          status: 404,
          detail: 'SLA settings not found for tenant: tenant_dev',
        }),
      })
      return
    }
    if (route.request().method() === 'PUT') {
      const body = route.request().postDataJSON() as { complianceTargetPercent: number }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          tenantId: 'tenant_dev',
          complianceTargetPercent: body.complianceTargetPercent,
          updatedAt: '2026-08-26T00:00:00Z',
          updatedBy: 'admin',
        }),
      })
      return
    }
    await route.fulfill({ status: 405, body: '' })
  })
  await page.route('**/api/reports/sla**', async (route) => {
    const url = route.request().url()
    if (url.includes('sla.csv')) {
      await route.fulfill({
        status: 200,
        contentType: 'text/csv;charset=UTF-8',
        headers: { 'Content-Disposition': 'attachment; filename="sla-report.csv"' },
        body: '# compliance=94.2; target=95; overdue=148; meanDays=6.4\r\ntype,workflow,volume\r\n',
      })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(SLA_REPORT),
    })
  })
  await page.route('**/api/purge-outbox**', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          ...envelope(purgeItems),
          counts: {
            pending: purgeItems.filter((i) => i.status === 'PENDING').length,
            processing: 0,
            failed: purgeItems.filter((i) => i.status === 'FAILED').length,
            completed: 2,
          },
        }),
      })
      return
    }
    await route.fulfill({ status: 405, body: '' })
  })
  let observabilityGets = 0
  await page.route('**/api/observability**', async (route) => {
    if (route.request().method() === 'GET') {
      observabilityGets += 1
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          tenantId: 'tenant_dev',
          observedAt: '2026-08-20T07:41:00Z',
          triggerOutboxOldestPendingAgeSeconds: 12,
          notificationOutboxOldestPendingAgeSeconds: 0,
          purgeOutboxOldestUnfinishedAgeSeconds: 3600,
          flowableDeadLetterJobCount: 0,
          flowableExceptionJobCount: 1,
          pdfRender: { count: 0, meanMs: null, maxMs: null },
          webhookDeadCount: 2,
          sseActiveCount: 3,
          severity: {
            triggerOutboxOldestPendingAge: 'ok',
            notificationOutboxOldestPendingAge: 'ok',
            purgeOutboxOldestUnfinishedAge: 'crit',
            flowableDeadLetterJobs: 'ok',
            flowableExceptionJobs: 'warn',
            pdfRender: 'ok',
            webhookDead: 'warn',
            sseActive: 'ok',
          },
          _gets: observabilityGets,
        }),
      })
      return
    }
    await route.fulfill({ status: 405, body: '' })
  })
  await page.route('**/api/dossier-types/CLAIM', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(CLAIM_TYPE),
    })
  })
  await page.route('**/api/dossiers**', async (route) => {
    const url = route.request().url()
    const method = route.request().method()
    if (method === 'POST' && /\/api\/dossiers\/[^/]+\/purge-requests$/.test(new URL(url).pathname)) {
      if (dossierStatus !== 'CLOTURE' && dossierStatus !== 'ARCHIVE') {
        await route.fulfill({
          status: 409,
          contentType: 'application/json',
          body: JSON.stringify({
            title: 'Dossier conflict',
            status: 409,
            detail: `Purge enqueue requires CLOTURE or ARCHIVE, was ${dossierStatus}`,
          }),
        })
        return
      }
      purgeItems = [
        {
          id: 'po-admin',
          dossierId: DOSSIER_ID,
          status: 'PENDING',
          origin: 'ADMIN',
          eventType: 'DOSSIER_PURGE_REQUESTED',
          createdAt: '2026-08-19T12:00:00Z',
          lastError: null,
        },
        ...purgeItems,
      ]
      await route.fulfill({ status: 201, body: '' })
      return
    }
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
        body: JSON.stringify(dossierBody({ status: dossierStatus, currentWorkflowLinkId: linkId, canEdit })),
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
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope(elements)) })
      return
    }
    if (url.includes('/workflows') && method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(
          envelope(
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
        ),
      })
      return
    }
    if (url.includes('/status') && method === 'PATCH') {
      const posted = route.request().postDataJSON() as { status?: string }
      if (posted.status) dossierStatus = posted.status
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(
          dossierBody({ status: dossierStatus, version: 1, currentWorkflowLinkId: linkId, canEdit }),
        ),
      })
      return
    }
    if (url.includes('/fields') && method === 'PATCH') {
      const posted = route.request().postDataJSON() as {
        fields: Record<string, unknown>
        expectedVersion: number
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(
          dossierBody({
            fields: { ...dossierBody().fields, ...posted.fields },
            status: dossierStatus,
            version: posted.expectedVersion + 1,
            currentWorkflowLinkId: linkId,
            canEdit,
          }),
        ),
      })
      return
    }
    if (method === 'GET') {
      const parsed = new URL(url)
      const path = parsed.pathname.replace(/\/$/, '')
      // Paginated list: GET /api/dossiers (no id, no ?ref=)
      if (path.endsWith('/api/dossiers') && !parsed.searchParams.has('ref')) {
        const body = dossierBody({ status: dossierStatus, currentWorkflowLinkId: linkId, canEdit })
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
        body: JSON.stringify(dossierBody({ status: dossierStatus, currentWorkflowLinkId: linkId, canEdit })),
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
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope([])) })
      return
    }
    if (url.includes('/reassign') && route.request().method() === 'POST') {
      await route.fulfill({ status: 204, body: '' })
      return
    }
    if (url.includes('/tasks')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope(tasks)) })
      return
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' })
  })
  await page.route('**/api/workflow-tasks/mine*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope(mineTasks)),
    })
  })
  await page.route('**/api/workflow-tasks/*/complete', async (route) => {
    tasks = []
    mineTasks = []
    await route.fulfill({ status: 204, body: '' })
  })
  await page.route(/\/api\/workflow-type-configs(?:\?|$)/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope([OTHER_WORKFLOW])),
    })
  })
  await page.route(/\/api\/document-templates(?:\?|$)/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope([])),
    })
  })
  await page.route(/\/api\/connectors(?:\?|$)/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope([])),
    })
  })
  await page.route('**/api/connector-definitions*', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope([])) })
  })
  let webhooks: Array<Record<string, unknown>> = [
    {
      id: 'wh-1',
      tenantId: 'tenant_dev',
      name: 'Hook A',
      targetUrl: 'https://example.com/hook',
      secret: null,
      secretConfigured: true,
      enabled: true,
      createdAt: '2026-08-19T10:00:00Z',
      updatedAt: '2026-08-19T10:00:00Z',
    },
  ]
  await page.route(/\/api\/notification-webhooks(?:\?|$)/, async (route) => {
    const method = route.request().method()
    if (method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(envelope(webhooks)),
      })
      return
    }
    if (method === 'POST') {
      const posted = route.request().postDataJSON() as {
        name: string
        targetUrl: string
        secret?: string
        enabled?: boolean
      }
      const created = {
        id: `wh-${webhooks.length + 1}`,
        tenantId: 'tenant_dev',
        name: posted.name,
        targetUrl: posted.targetUrl,
        secret: null,
        secretConfigured: Boolean(posted.secret),
        enabled: posted.enabled !== false,
        createdAt: '2026-08-19T12:00:00Z',
        updatedAt: '2026-08-19T12:00:00Z',
      }
      webhooks = [...webhooks, created]
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(created),
      })
      return
    }
    await route.fulfill({ status: 405, body: '' })
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

test('E2E-9 supervision has no fake KPI', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'Supervision' }).click()
  await expect(page.getByRole('heading', { name: 'Supervision' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Filtrer' })).toBeVisible()
  await expect(page.getByText('DOS-1')).toBeVisible()
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
  await expect(page.getByRole('cell', { name: 'CLAIM', exact: true })).toBeVisible()
  await expect(page.getByTestId('admin-pager-prev')).toBeDisabled()
})

test('E2E-SLA-6 admin clears type SLA hours', async ({ page }) => {
  let patched: Record<string, unknown> | null = null
  await mockSession(page)
  await page.route('**/api/dossier-types/CLAIM', async (route) => {
    if (route.request().method() === 'PATCH') {
      patched = route.request().postDataJSON() as Record<string, unknown>
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ ...CLAIM_TYPE, slaTargetHours: null }),
      })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(CLAIM_TYPE),
    })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Types' }).click()
  await expect(page.getByTestId('type-clear-sla')).toBeVisible()
  await page.getByTestId('type-clear-sla').click()
  await expect.poll(() => patched).toEqual({ clearSlaTargetHours: true })
})

test('E2E-PAGE-1 admin types pager writes page query', async ({ page }) => {
  await mockSession(page)
  await page.route(/\/api\/dossier-types(?:\?|$)/, async (route) => {
    if (route.request().method() !== 'GET') {
      await route.fallback()
      return
    }
    const url = new URL(route.request().url())
    const pageNo = Number(url.searchParams.get('page') || '0')
    const type = {
      ...CLAIM_TYPE,
      id: pageNo === 0 ? 't1' : 't2',
      code: pageNo === 0 ? 'CLAIM' : 'FAC',
      label: pageNo === 0 ? 'Sinistre' : 'Facture',
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope([type], { page: pageNo, size: 20, totalElements: 21, totalPages: 2 })),
    })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Types' }).click()
  await expect(page.getByRole('cell', { name: 'CLAIM', exact: true })).toBeVisible()
  await page.getByTestId('admin-pager-next').click()
  await expect(page).toHaveURL(/[?&]page=1/)
  await expect(page.getByRole('cell', { name: 'FAC', exact: true })).toBeVisible()
})

test('E2E-RET-1 admin saves tenant retention default', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'Types' }).click()
  await expect(page.getByRole('heading', { name: 'Rétention tenant' })).toBeVisible()
  await page.getByTestId('retention-default-days').fill('90')
  await page.getByTestId('retention-save').click()
  await expect(page.getByText('Défaut actuel : 90 jours')).toBeVisible()
})

test('E2E-RET-2 admin creates type with NEVER retention', async ({ page }) => {
  let createdBody: Record<string, unknown> | null = null
  await mockSession(page)
  await page.route(/\/api\/dossier-types(?:\?|$)/, async (route) => {
    if (route.request().method() === 'POST') {
      createdBody = route.request().postDataJSON() as Record<string, unknown>
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 't2',
          tenantId: 'tenant_dev',
          code: createdBody.code,
          label: createdBody.label,
          workflowKey: createdBody.workflowKey,
          active: true,
          allowUnknownFields: false,
          retentionDays: null,
          retentionNever: true,
          fieldDefinitions: [],
        }),
      })
      return
    }
    await route.fallback()
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Types' }).click()
  const createForm = page.getByTestId('type-create-form')
  await createForm.getByPlaceholder('code').fill('LEGAL')
  await createForm.getByPlaceholder('libellé').fill('Legal')
  await createForm.getByPlaceholder('workflowKey').fill('wf-legal')
  await page.getByTestId('type-retention-mode').selectOption('never')
  await createForm.getByRole('button', { name: 'Créer' }).click()
  await expect.poll(() => createdBody?.retentionNever).toBe(true)
})

test('E2E-SCHEMA-1 admin creates a type with fieldDefinitions and allowUnknownFields', async ({ page }) => {
  let createdBody: Record<string, unknown> | null = null
  await mockSession(page)
  await page.route(/\/api\/dossier-types(?:\?|$)/, async (route) => {
    if (route.request().method() === 'POST') {
      createdBody = route.request().postDataJSON() as Record<string, unknown>
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 't3',
          tenantId: 'tenant_dev',
          code: createdBody.code,
          label: createdBody.label,
          workflowKey: createdBody.workflowKey,
          active: true,
          allowUnknownFields: createdBody.allowUnknownFields,
          retentionDays: null,
          retentionNever: false,
          fieldDefinitions: createdBody.fieldDefinitions,
        }),
      })
      return
    }
    await route.fallback()
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Types' }).click()
  const createForm = page.getByTestId('type-create-form')
  await createForm.getByPlaceholder('code').fill('LOGI')
  await createForm.getByPlaceholder('libellé').fill('Logistique')
  await createForm.getByPlaceholder('workflowKey').fill('wf-logi')
  await createForm.getByTestId('type-allow-unknown-fields').check()
  await createForm.getByTestId('field-def-add').click()
  await createForm.getByTestId('field-def-name').fill('status')
  await createForm.getByTestId('field-def-label').fill('Statut')
  await createForm.getByTestId('field-def-type').selectOption('SELECT')
  await createForm.getByTestId('field-def-options-input').fill('A, B')
  await createForm.getByRole('button', { name: 'Créer' }).click()
  await expect.poll(() => createdBody?.allowUnknownFields).toBe(true)
  await expect
    .poll(() => (createdBody?.fieldDefinitions as Array<Record<string, unknown>> | undefined)?.[0]?.options)
    .toEqual(['A', 'B'])
})

test('E2E-SCHEMA-2 admin edits a type schema with no existing dossiers', async ({ page }) => {
  let patchedBody: Record<string, unknown> | null = null
  await mockSession(page)
  await page.route('**/api/dossier-types/CLAIM', async (route) => {
    if (route.request().method() === 'PATCH') {
      patchedBody = route.request().postDataJSON() as Record<string, unknown>
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ ...CLAIM_TYPE, fieldDefinitions: patchedBody.fieldDefinitions }),
      })
      return
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(CLAIM_TYPE) })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Types' }).click()
  await page.getByTestId('type-edit-fields').click()
  const panel = page.getByTestId('type-edit-fields-panel')
  await expect(panel.getByTestId('field-def-row')).toHaveCount(1)
  await panel.getByTestId('field-def-add').click()
  await panel.getByTestId('field-def-name').nth(1).fill('amount')
  await panel.getByTestId('field-def-label').nth(1).fill('Montant')
  await panel.getByTestId('type-edit-fields-save').click()
  await expect.poll(() => patchedBody?.fieldDefinitions).not.toBeNull()
  await expect(panel).toBeHidden()
})

test('E2E-SCHEMA-3 incompatible schema change shows the 409 message and keeps the panel open', async ({ page }) => {
  await mockSession(page)
  await page.route('**/api/dossier-types/CLAIM', async (route) => {
    if (route.request().method() === 'PATCH') {
      await route.fulfill({
        status: 409,
        contentType: 'application/json',
        body: JSON.stringify({
          title: 'Dossier type schema incompatible',
          status: 409,
          detail:
            'Cette modification rend 2 dossier(s) existant(s) incompatible(s) avec le nouveau schéma (ex. DOS-1, DOS-2)',
        }),
      })
      return
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(CLAIM_TYPE) })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Types' }).click()
  await page.getByTestId('type-edit-fields').click()
  const panel = page.getByTestId('type-edit-fields-panel')
  await panel.getByTestId('type-edit-fields-save').click()
  await expect(panel.getByText('incompatible')).toBeVisible()
  await expect(panel).toBeVisible()
})

test('E2E-SCHEMA-4 duplicate field name is rejected client-side, never reaches the network', async ({ page }) => {
  let createCalled = false
  await mockSession(page)
  await page.route(/\/api\/dossier-types(?:\?|$)/, async (route) => {
    if (route.request().method() === 'POST') {
      createCalled = true
      await route.fulfill({ status: 201, contentType: 'application/json', body: '{}' })
      return
    }
    await route.fallback()
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Types' }).click()
  const createForm = page.getByTestId('type-create-form')
  await createForm.getByPlaceholder('code').fill('DUP')
  await createForm.getByPlaceholder('libellé').fill('Duplicate')
  await createForm.getByPlaceholder('workflowKey').fill('wf-dup')
  await createForm.getByTestId('field-def-add').click()
  await createForm.getByTestId('field-def-name').fill('amount')
  await createForm.getByTestId('field-def-label').fill('Amount')
  await createForm.getByTestId('field-def-add').click()
  await createForm.getByTestId('field-def-name').nth(1).fill('amount')
  await createForm.getByTestId('field-def-label').nth(1).fill('Amount again')
  await createForm.getByRole('button', { name: 'Créer' }).click()
  await expect(page.getByText('utilisé plusieurs fois')).toBeVisible()
  expect(createCalled).toBe(false)
})

test('E2E-RET-3 non-admin does not see Types admin nav', async ({ page }) => {
  await mockSession(page, { roles: ['DOSSIER_VIEWER'] })
  await openApp(page)
  await expect(page.getByRole('link', { name: 'Types' })).toHaveCount(0)
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

test('E2E-22 manager patches dossier fields from detail', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await createAndOpenDossier(page)
  const fieldsRequest = page.waitForRequest(
    (req) => req.method() === 'PATCH' && req.url().includes('/fields'),
  )
  await page.locator('.dossier-fields-editor input').fill('HIGH')
  await page.getByRole('button', { name: 'Enregistrer les champs' }).click()
  const request = await fieldsRequest
  expect(request.postDataJSON()).toEqual({
    fields: { priority: 'HIGH' },
    expectedVersion: 0,
  })
  await expect(page.locator('.dossier-fields-editor input')).toHaveValue('HIGH')
})

test('E2E-23 viewer cannot edit dossier fields', async ({ page }) => {
  await mockSession(page, { roles: ['DOSSIER_VIEWER'] })
  await openApp(page)
  await createAndOpenDossier(page)
  await expect(page.getByRole('heading', { name: 'Champs' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Enregistrer les champs' })).toHaveCount(0)
})

test('E2E-PURGE-1 admin sees purge queue counts and list', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'File purge' }).click()
  await expect(page.getByRole('heading', { name: 'File de purge' })).toBeVisible()
  await expect(page.getByTestId('purge-outbox-counts')).toContainText('FAILED : 1')
  await expect(page.getByTestId('purge-outbox-counts')).toContainText('PROCESSING : 0')
  await expect(page.getByTestId('purge-outbox-queue')).toContainText('S3 timeout')
})

test('E2E-PURGE-2 non-admin does not see purge queue nav', async ({ page }) => {
  await mockSession(page, { roles: ['DOSSIER_VIEWER'] })
  await openApp(page)
  await expect(page.getByRole('link', { name: 'File purge' })).toHaveCount(0)
})

test('E2E-PURGE-3 purge queue has no force button', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'File purge' }).click()
  await expect(page.getByRole('heading', { name: 'File de purge' })).toBeVisible()
  await expect(page.getByRole('button', { name: /force|forcer|relancer/i })).toHaveCount(0)
})

test('E2E-PURGE-4 enqueue CLOTURE shows PENDING in queue', async ({ page }) => {
  await mockSession(page, { dossierStatus: 'CLOTURE' })
  await openApp(page)
  await createAndOpenDossier(page)
  await page.getByTestId('dossier-purge-request').click()
  await expect(page.getByText('Demande enregistrée')).toBeVisible()
  await page.getByRole('link', { name: 'File purge' }).click()
  await expect(page.getByTestId('purge-outbox-queue')).toContainText('PENDING')
  await expect(page.getByTestId('purge-outbox-queue')).toContainText('ADMIN')
})

test('E2E-PURGE-5 enqueue open dossier returns 4xx', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await createAndOpenDossier(page)
  await page.getByTestId('dossier-purge-request').click()
  await expect(page.getByText(/CLOTURE or ARCHIVE/)).toBeVisible()
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

test('E2E-OBS-1 admin sees observability kpis', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'Observabilité' }).click()
  await expect(page.getByRole('heading', { name: 'Observabilité' })).toBeVisible()
  await expect(page.getByTestId('kpi-trigger-outbox-age')).toContainText('12 s')
  await expect(page.getByTestId('kpi-notification-outbox-age')).toContainText('0 s')
  await expect(page.getByTestId('kpi-purge-outbox-age')).toContainText('3600 s')
  await expect(page.getByTestId('kpi-flowable-dlq')).toBeVisible()
  await expect(page.getByTestId('kpi-pdf-render')).toContainText('n/a')
  await expect(page.getByTestId('kpi-webhook-dead')).toContainText('2')
  await expect(page.getByTestId('kpi-sse-active')).toContainText('3')
})

test('E2E-OBS-2 non-admin does not see observability nav', async ({ page }) => {
  await mockSession(page, { roles: ['DOSSIER_VIEWER'] })
  await openApp(page)
  await expect(page.getByRole('link', { name: 'Observabilité' })).toHaveCount(0)
  await page.goto('/admin/observabilite')
  await expect(page.getByRole('heading', { name: 'Observabilité' })).toHaveCount(0)
})

test('E2E-OBS-3 observability has no force button', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'Observabilité' }).click()
  await expect(page.getByRole('heading', { name: 'Observabilité' })).toBeVisible()
  await expect(page.getByRole('button', { name: /force|forcer|relancer/i })).toHaveCount(0)
})

test('E2E-OBS-4 refresh triggers a second get', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'Observabilité' }).click()
  await expect(page.getByTestId('observability-page')).toBeVisible()
  const refresh = page.waitForRequest((req) => req.url().includes('/api/observability') && req.method() === 'GET')
  await page.getByTestId('observability-refresh').click()
  await refresh
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

const BPMN_FOR_OTHER_WORKFLOW = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="http://flowforge/e2e">
  <process id="otherWorkflow" isExecutable="true">
    <startEvent id="start"/>
    <userTask id="wait" name="Wait"/>
    <endEvent id="end"/>
    <sequenceFlow id="flow1" sourceRef="start" targetRef="wait"/>
    <sequenceFlow id="flow2" sourceRef="wait" targetRef="end"/>
  </process>
</definitions>`

test('E2E-BPMN-1 deploy a valid BPMN diagram: preview then confirm', async ({ page }) => {
  await mockSession(page)
  await page.route('**/api/workflow-bpmn-deployments/info*', async (route) => {
    await route.fulfill({ status: 204, body: '' })
  })
  await page.route('**/api/workflow-bpmn-deployments', async (route) => {
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({ processDefinitionKey: 'otherWorkflow', deployedVersion: 1, deploymentId: 'dep-1' }),
    })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Workflows' }).click()
  await expect(page.getByRole('heading', { name: 'Déployer un diagramme BPMN' })).toBeVisible()

  await page.locator('input[type="file"]').setInputFiles({
    name: 'diagram.bpmn20.xml',
    mimeType: 'application/xml',
    buffer: Buffer.from(BPMN_FOR_OTHER_WORKFLOW),
  })
  const section = page.locator('.bpmn-upload-preview')
  await expect(section.getByText('otherWorkflow')).toBeVisible()
  await expect(section.getByText('Aucune version déployée pour cette clé pour l\'instant.')).toBeVisible()

  await page.getByRole('button', { name: 'Confirmer le déploiement' }).click()
  await expect(page.getByText(/Déployé : version 1/)).toBeVisible()
  await expect(page.getByText('Diagramme déployé avec succès.')).toBeVisible()
})

test('E2E-BPMN-2 invalid BPMN file shows a client-side error, never calls deploy', async ({ page }) => {
  await mockSession(page)
  let deployCalled = false
  await page.route('**/api/workflow-bpmn-deployments', async (route) => {
    deployCalled = true
    await route.fulfill({ status: 201, contentType: 'application/json', body: '{}' })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Workflows' }).click()

  await page.locator('input[type="file"]').setInputFiles({
    name: 'not-bpmn.xml',
    mimeType: 'application/xml',
    buffer: Buffer.from('<?xml version="1.0"?><root><child/></root>'),
  })
  await expect(page.getByText(/non reconnu comme un diagramme BPMN valide/)).toBeVisible()
  await expect(page.getByRole('button', { name: 'Confirmer le déploiement' })).toHaveCount(0)
  expect(deployCalled).toBe(false)
})

test('E2E-BPMN-3 redeploying an existing key shows current version and active instances', async ({ page }) => {
  await mockSession(page)
  await page.route('**/api/workflow-bpmn-deployments/info*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ processDefinitionKey: 'otherWorkflow', latestVersion: 3, activeInstanceCount: 12 }),
    })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Workflows' }).click()

  await page.locator('input[type="file"]').setInputFiles({
    name: 'diagram.bpmn20.xml',
    mimeType: 'application/xml',
    buffer: Buffer.from(BPMN_FOR_OTHER_WORKFLOW),
  })
  await expect(page.getByText('Version actuelle déployée : 3')).toBeVisible()
  await expect(page.getByText(/instances actives : 12/)).toBeVisible()
})

// The editor renders with a real bpmn-js Modeler (unlike the read-only
// preview in BpmnUploadPreview.vue), which requires
// bpmndi:BPMNDiagram/DI layout information to import successfully.
const BPMN_IMPORTED = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
             xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
             xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
             targetNamespace="http://flowforge/e2e">
  <process id="importedProcess" isExecutable="true">
    <startEvent id="start"/>
    <endEvent id="end"/>
    <sequenceFlow id="flow1" sourceRef="start" targetRef="end"/>
  </process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="importedProcess">
      <bpmndi:BPMNShape id="start_di" bpmnElement="start">
        <dc:Bounds x="150" y="100" width="36" height="36"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="end_di" bpmnElement="end">
        <dc:Bounds x="300" y="100" width="36" height="36"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="flow1_di" bpmnElement="flow1">
        <di:waypoint x="186" y="118"/>
        <di:waypoint x="300" y="118"/>
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>`

// Same "otherWorkflow" process as BPMN_FOR_OTHER_WORKFLOW above, but with
// DI — that one has none and throws "no diagram to display" if fed to the
// editor's Modeler (it works for BpmnUploadPreview.vue only because that
// component never asserts the render actually succeeded).
const BPMN_FOR_OTHER_WORKFLOW_EDITABLE = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
             xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
             xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
             targetNamespace="http://flowforge/e2e">
  <process id="otherWorkflow" isExecutable="true">
    <startEvent id="start"/>
    <userTask id="wait" name="Wait"/>
    <endEvent id="end"/>
    <sequenceFlow id="flow1" sourceRef="start" targetRef="wait"/>
    <sequenceFlow id="flow2" sourceRef="wait" targetRef="end"/>
  </process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="otherWorkflow">
      <bpmndi:BPMNShape id="start_di" bpmnElement="start">
        <dc:Bounds x="150" y="100" width="36" height="36"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="wait_di" bpmnElement="wait">
        <dc:Bounds x="250" y="80" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="end_di" bpmnElement="end">
        <dc:Bounds x="420" y="100" width="36" height="36"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="flow1_di" bpmnElement="flow1">
        <di:waypoint x="186" y="118"/>
        <di:waypoint x="250" y="120"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow2_di" bpmnElement="flow2">
        <di:waypoint x="350" y="120"/>
        <di:waypoint x="420" y="118"/>
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>`

// NOTE: navigation to the BPMN editor always goes through the "Éditer"
// link on /admin/workflow-types (client-side router push), never a direct
// page.goto('/admin/bpmn-editor...'). This app's production build uses
// relative asset paths (decision n°27, same-origin embedded SPA) — a full
// page load of a nested route resolves "./assets/…" against that nested
// path and 404s the JS bundle. Every other test in this file already
// avoids this by navigating via link clicks; the BPMN editor tests follow
// the same proven pattern rather than hitting a pre-existing, unrelated
// deep-link limitation.
async function openBpmnEditor(page: Page) {
  await page.getByRole('link', { name: 'Workflows' }).click()
  await page.getByRole('link', { name: 'Éditer' }).click()
  await expect(page.getByRole('heading', { name: 'Éditeur de diagramme BPMN' })).toBeVisible()
  // The Modeler is constructed via dynamic import, then importXML runs
  // asynchronously — the palette appears as soon as the Modeler is built,
  // *before* importXML resolves, so it isn't enough on its own. Wait for an
  // actually rendered diagram element instead, otherwise saveXML() races
  // ahead of the import and returns an empty/invalid document.
  await expect(page.locator('.djs-element').first()).toBeVisible()
}

test('E2E-EDIT-BPMN-1 blank diagram (no draft, not yet deployed): save creates the draft', async ({ page }) => {
  await mockSession(page)
  await page.route('**/api/workflow-bpmn-drafts/otherWorkflow', async (route) => {
    await route.fulfill({ status: 204, body: '' })
  })
  await page.route('**/api/workflow-bpmn-deployments/otherWorkflow/xml', async (route) => {
    await route.fulfill({ status: 204, body: '' })
  })
  let putCalled = false
  await page.route('**/api/workflow-bpmn-drafts/Process_1', async (route) => {
    putCalled = true
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        processDefinitionKey: 'Process_1',
        xml: '<definitions/>',
        updatedAt: '2026-08-24T00:00:00Z',
        updatedBy: 'user-1',
      }),
    })
  })
  await openApp(page)
  await openBpmnEditor(page)
  await expect(page.getByText(/reconnu comme un diagramme BPMN valide/)).toHaveCount(0)

  // The blank starter template's own process id (bpmn.io's default,
  // "Process_1") drives the save key — not the URL's processDefinitionKey
  // (édition d'un diagramme sans clé encore définie, cf. requirements doc).
  await page.getByRole('button', { name: 'Enregistrer le brouillon' }).click()
  expect(putCalled).toBe(true)
})

test('E2E-EDIT-BPMN-2 edit an already-deployed diagram (no draft) then deploy', async ({ page }) => {
  await mockSession(page)
  await page.route('**/api/workflow-bpmn-drafts/otherWorkflow', async (route) => {
    await route.fulfill({ status: 204, body: '' })
  })
  let deployedXmlFetched = false
  await page.route('**/api/workflow-bpmn-deployments/otherWorkflow/xml', async (route) => {
    deployedXmlFetched = true
    await route.fulfill({ status: 200, contentType: 'text/xml', body: BPMN_FOR_OTHER_WORKFLOW_EDITABLE })
  })
  await page.route('**/api/workflow-bpmn-deployments/info*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ processDefinitionKey: 'otherWorkflow', latestVersion: 2, activeInstanceCount: 4 }),
    })
  })
  await page.route('**/api/workflow-bpmn-deployments', async (route) => {
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({ processDefinitionKey: 'otherWorkflow', deployedVersion: 3, deploymentId: 'dep-9' }),
    })
  })
  await openApp(page)
  await openBpmnEditor(page)
  expect(deployedXmlFetched).toBe(true)
  await expect(page.getByText(/reconnu comme un diagramme BPMN valide/)).toHaveCount(0)

  await page.getByRole('button', { name: 'Déployer' }).click()
  await expect(page.getByText('Version actuelle déployée : 2 — instances actives : 4')).toBeVisible()

  await page.getByRole('button', { name: 'Confirmer le déploiement' }).click()
  await expect(page.getByText(/Déployé : version 3/)).toBeVisible()
})

test('E2E-EDIT-BPMN-3 import a local BPMN file into the editor', async ({ page }) => {
  await mockSession(page)
  await page.route('**/api/workflow-bpmn-drafts/otherWorkflow', async (route) => {
    await route.fulfill({ status: 204, body: '' })
  })
  await page.route('**/api/workflow-bpmn-deployments/otherWorkflow/xml', async (route) => {
    await route.fulfill({ status: 204, body: '' })
  })
  let putCalled = false
  await page.route('**/api/workflow-bpmn-drafts/importedProcess', async (route) => {
    putCalled = true
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        processDefinitionKey: 'importedProcess',
        xml: BPMN_IMPORTED,
        updatedAt: '2026-08-24T00:00:00Z',
        updatedBy: 'user-1',
      }),
    })
  })
  await openApp(page)
  await openBpmnEditor(page)

  await page.locator('input[type="file"]').setInputFiles({
    name: 'imported.bpmn20.xml',
    mimeType: 'application/xml',
    buffer: Buffer.from(BPMN_IMPORTED),
  })
  // The re-import into the already-constructed Modeler is async too — wait
  // for the imported diagram's own element (distinct id from the blank
  // template's "StartEvent_1") to actually be rendered.
  await expect(page.locator('[data-element-id="start"]')).toBeVisible()
  await expect(page.locator('[data-element-id="StartEvent_1"]')).toHaveCount(0)
  await expect(page.getByText(/reconnu comme un diagramme BPMN valide/)).toHaveCount(0)

  // The imported file's own process id ("importedProcess") drives the save
  // key, proving import → re-render → key extraction all wired correctly.
  await page.getByRole('button', { name: 'Enregistrer le brouillon' }).click()
  expect(putCalled).toBe(true)
})

test('E2E-EDIT-BPMN-4 draft takes priority over the deployed diagram, deploy then reopen falls back to it', async ({
  page,
}) => {
  await mockSession(page)
  let draftDeleted = false
  await page.route('**/api/workflow-bpmn-drafts/otherWorkflow', async (route) => {
    if (draftDeleted) {
      await route.fulfill({ status: 204, body: '' })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        processDefinitionKey: 'otherWorkflow',
        xml: BPMN_FOR_OTHER_WORKFLOW_EDITABLE,
        updatedAt: '2026-08-24T00:00:00Z',
        updatedBy: 'user-1',
      }),
    })
  })
  let deployedXmlFetchCount = 0
  await page.route('**/api/workflow-bpmn-deployments/otherWorkflow/xml', async (route) => {
    deployedXmlFetchCount += 1
    await route.fulfill({ status: 200, contentType: 'text/xml', body: BPMN_FOR_OTHER_WORKFLOW_EDITABLE })
  })
  await page.route('**/api/workflow-bpmn-deployments/info*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ processDefinitionKey: 'otherWorkflow', latestVersion: 1, activeInstanceCount: 0 }),
    })
  })
  await page.route('**/api/workflow-bpmn-deployments', async (route) => {
    draftDeleted = true // D-DESIGN-DRAFT-LIFECYCLE: the backend deletes the draft on success
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({ processDefinitionKey: 'otherWorkflow', deployedVersion: 2, deploymentId: 'dep-42' }),
    })
  })
  await openApp(page)

  // First visit: the draft exists, so it takes priority over the deployed diagram.
  await openBpmnEditor(page)
  expect(deployedXmlFetchCount).toBe(0)

  await page.getByRole('button', { name: 'Déployer' }).click()
  await expect(page.getByText('Version actuelle déployée : 1 — instances actives : 0')).toBeVisible()
  await page.getByRole('button', { name: 'Confirmer le déploiement' }).click()
  await expect(page.getByText(/Déployé : version 2/)).toBeVisible()

  // Reopening the editor on the same key (leave, come back): the backend
  // reports no draft anymore, so the frontend correctly falls back to the
  // deployed diagram instead of showing stale client-side state.
  await openBpmnEditor(page)
  expect(deployedXmlFetchCount).toBe(1)
})

test('E2E-SLA-1 reports page shows KPI cards for manager', async ({ page }) => {
  await mockSession(page, { roles: ['DOSSIER_MANAGER'] })
  await openApp(page)
  await page.getByRole('link', { name: 'Rapports & SLA' }).click()
  await expect(page.getByRole('heading', { name: 'Rapports & SLA' })).toBeVisible()
  await expect(page.getByTestId('kpi-compliance')).toContainText('94.2 %')
  await expect(page.getByText('6.4 j')).toBeVisible()
})

test('E2E-SLA-2 export triggers CSV download request', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'Rapports & SLA' }).click()
  await expect(page.getByRole('heading', { name: 'Rapports & SLA' })).toBeVisible()
  const csvRequest = page.waitForRequest((request) => request.url().includes('/api/reports/sla.csv'))
  await page.getByTestId('rapports-export').click()
  await csvRequest
})

test('E2E-SLA-3 schedule send is disabled', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'Rapports & SLA' }).click()
  await expect(page.getByTestId('rapports-schedule')).toBeDisabled()
})

test('E2E-SLA-4 viewer has no reports link', async ({ page }) => {
  await mockSession(page, { roles: ['DOSSIER_VIEWER'] })
  await openApp(page)
  await expect(page.getByRole('link', { name: 'Rapports & SLA' })).toHaveCount(0)
})

test('E2E-SLA-5 inbox shows remaining SLA hours', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'Tâches' }).click()
  await expect(page.getByTestId('task-sla')).toContainText('SLA')
  await expect(page.getByTestId('task-sla')).toContainText('12')
})

test('E2E-SLA-7 custom period is sent as UTC Instant not datetime-local', async ({ page }) => {
  await mockSession(page, { roles: ['DOSSIER_MANAGER'] })
  await openApp(page)
  await page.getByRole('link', { name: 'Rapports & SLA' }).click()
  await expect(page.getByTestId('kpi-compliance')).toBeVisible()
  await page.getByTestId('rapports-from').fill('2026-03-01T00:00')
  await page.getByTestId('rapports-to').fill('2026-03-15T23:59')
  const snapshotRequest = page.waitForRequest(
    (request) =>
      request.url().includes('/api/reports/sla') &&
      !request.url().includes('.csv') &&
      request.url().includes('from='),
  )
  await page.getByTestId('rapports-refresh').click()
  const url = new URL((await snapshotRequest).url())
  expect(url.searchParams.get('from')).toBe('2026-03-01T00:00:00Z')
  expect(url.searchParams.get('to')).toBe('2026-03-15T23:59:00Z')
})

test('E2E-SLA-8 type filter is forwarded as dossierTypeCode', async ({ page }) => {
  await mockSession(page, { roles: ['DOSSIER_MANAGER'] })
  await openApp(page)
  await page.getByRole('link', { name: 'Rapports & SLA' }).click()
  await expect(page.getByTestId('rapports-type')).toBeVisible()
  await page.getByTestId('rapports-type').selectOption('CLAIM')
  const snapshotRequest = page.waitForRequest(
    (request) =>
      request.url().includes('/api/reports/sla') &&
      !request.url().includes('.csv') &&
      request.url().includes('dossierTypeCode='),
  )
  await page.getByTestId('rapports-refresh').click()
  const url = new URL((await snapshotRequest).url())
  expect(url.searchParams.get('dossierTypeCode')).toBe('CLAIM')
})

// TODO §52 (formulaires-dynamiques-taches) — a task with a known formKey shows the matching
// task-form component instead of the blind "Compléter" button (E2E-5 already covers the
// non-regression case: a task without formKey keeps the blind button).
test('E2E-FORM-1 dossier-fields task shows the fields form and completes via PATCH', async ({ page }) => {
  await mockSession(page)
  let formTasks = [
    {
      taskId: 'task-1',
      name: 'Valider',
      assigneeId: 'user-1',
      createdAt: '2026-08-12T00:00:00Z',
      processInstanceId: 'pi-1',
      dossierId: DOSSIER_ID,
      dossierReference: 'DOS-1',
      linkId: LINK_ID,
      slaRemainingHours: 12,
      formKey: 'dossier-fields',
    },
  ]
  await page.route('**/api/workflow-tasks/mine*', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope(formTasks)) })
  })
  await page.route('**/api/workflow-tasks/*/complete', async (route) => {
    formTasks = []
    await route.fulfill({ status: 204, body: '' })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Tâches' }).click()
  await expect(page.getByText('Valider')).toBeVisible()
  // The dossier-fields form renders DynamicFields against CLAIM_TYPE.fieldDefinitions
  // (single "priority" STRING field, defaultValue "NORMAL" per dossierBody()'s fields).
  const priorityInput = page.locator('.dossier-fields-task-form input')
  await expect(priorityInput).toHaveValue('NORMAL')
  await priorityInput.fill('HIGH')

  const patchRequest = page.waitForRequest(
    (request) => request.url().includes(`/api/dossiers/${DOSSIER_ID}/fields`) && request.method() === 'PATCH',
  )
  await page.locator('.dossier-fields-task-form button').click()
  const patched = (await patchRequest).postDataJSON() as { fields: Record<string, unknown> }
  expect(patched.fields.priority).toBe('HIGH')
  await expect(page.getByText('Aucune tâche assignée')).toBeVisible()
})

test('E2E-FORM-2 document-generation task lists only the allowed templates and completes with variables', async ({
  page,
}) => {
  await mockSession(page)
  await page.route('**/api/workflow-tasks/mine*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(
        envelope([
          {
            taskId: 'task-1',
            name: 'Générer',
            assigneeId: 'user-1',
            createdAt: '2026-08-12T00:00:00Z',
            processInstanceId: 'pi-1',
            dossierId: DOSSIER_ID,
            dossierReference: 'DOS-1',
            linkId: LINK_ID,
            slaRemainingHours: 12,
            formKey: 'document-generation',
          },
        ]),
      ),
    })
  })
  await page.route('**/api/dossier-types/CLAIM/document-templates', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        { templateKey: 'invoice', variantKey: 'premium', locale: 'fr', label: 'Facture premium FR' },
      ]),
    })
  })
  let completedWith: unknown = null
  await page.route('**/api/workflow-tasks/*/complete', async (route) => {
    completedWith = route.request().postDataJSON()
    await route.fulfill({ status: 204, body: '' })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Tâches' }).click()
  await expect(page.getByText('Générer')).toBeVisible()
  const picker = page.locator('.document-generation-task-form select')
  await expect(picker.locator('option', { hasText: 'Facture premium FR' })).toHaveCount(1)
  await picker.selectOption({ label: 'Facture premium FR' })
  await page.locator('.document-generation-task-form button').click()
  // documentType/documentVariant/documentLocale are the variable names DocumentGenerationTask.java
  // (the ${documentGenerationTask} serviceTask) actually reads via execution.getVariable(...) --
  // not templateKey/variantKey/locale, which are only the AllowedDocumentTemplate DTO's field
  // names on the way in.
  expect(completedWith).toEqual({
    variables: { documentType: 'invoice', documentVariant: 'premium', documentLocale: 'fr' },
  })
})

// Own fixture (not BPMN_FOR_OTHER_WORKFLOW_EDITABLE): must declare xmlns:flowable up front, like
// every real process file under src/main/resources/processes/ does. Writing a *new* flowable:*
// attribute via the Modeling API only lands in the moddle element's generic $attrs bag (no
// moddleExtensions registered for "flowable" in this app -- see flowablePropertiesProvider.ts
// header) -- moddle-xml's writer can only re-emit it if it already knows a URI for the "flowable"
// prefix from the parsed document. Confirmed empirically: without this xmlns declaration, the
// property still round-trips through getValue()/setValue() (proven by reselecting the element)
// but silently never reaches saveXML()'s output -- the writer swallows the failed prefix
// resolution. Real diagrams built by this editor and re-saved always carry the declaration once a
// flowable:* attribute (delegateExpression/expression/scope/formKey) is set once via the palette,
// same as any other bpmn-js extension attribute -- this fixture only needs to pre-declare it
// because the source XML is fed in fully formed rather than authored by hand in the canvas.
const BPMN_FOR_OTHER_WORKFLOW_WITH_FLOWABLE_NS = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
             xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
             xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
             targetNamespace="http://flowforge/e2e">
  <process id="otherWorkflow" isExecutable="true">
    <startEvent id="start"/>
    <userTask id="wait" name="Wait"/>
    <endEvent id="end"/>
    <sequenceFlow id="flow1" sourceRef="start" targetRef="wait"/>
    <sequenceFlow id="flow2" sourceRef="wait" targetRef="end"/>
  </process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="otherWorkflow">
      <bpmndi:BPMNShape id="start_di" bpmnElement="start">
        <dc:Bounds x="150" y="100" width="36" height="36"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="wait_di" bpmnElement="wait">
        <dc:Bounds x="250" y="80" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="end_di" bpmnElement="end">
        <dc:Bounds x="420" y="100" width="36" height="36"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="flow1_di" bpmnElement="flow1">
        <di:waypoint x="186" y="118"/>
        <di:waypoint x="250" y="120"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow2_di" bpmnElement="flow2">
        <di:waypoint x="350" y="120"/>
        <di:waypoint x="420" y="118"/>
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>`

test('E2E-FORM-4 BPMN editor sets flowable:formKey on a userTask, round-trips on save', async ({ page }) => {
  await mockSession(page)
  await page.route('**/api/workflow-bpmn-deployments/otherWorkflow/xml', async (route) => {
    await route.fulfill({ status: 200, contentType: 'text/xml', body: BPMN_FOR_OTHER_WORKFLOW_WITH_FLOWABLE_NS })
  })
  await page.route('**/api/workflow-bpmn-deployments/info*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ processDefinitionKey: 'otherWorkflow', latestVersion: 2, activeInstanceCount: 0 }),
    })
  })
  let savedXml = ''
  await page.route('**/api/workflow-bpmn-drafts/Process_1', async (route) => {
    await route.fulfill({ status: 204, body: '' })
  })
  await page.route('**/api/workflow-bpmn-drafts/otherWorkflow', async (route) => {
    if (route.request().method() === 'PUT') {
      savedXml = (route.request().postDataJSON() as { xml: string }).xml
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          processDefinitionKey: 'otherWorkflow',
          xml: savedXml,
          updatedAt: '2026-09-03T00:00:00Z',
          updatedBy: 'user-1',
        }),
      })
      return
    }
    await route.fulfill({ status: 204, body: '' })
  })
  await openApp(page)
  await openBpmnEditor(page)

  await page.locator('[data-element-id="wait"]').click()
  const formKeyInput = page.getByLabel('Form key')
  await expect(formKeyInput).toBeVisible()
  await formKeyInput.fill('document-generation')
  // The properties panel text entry debounces its commit to the moddle model
  // (useService('debounceInput'), DEFAULT_DEBOUNCE_TIME = 600ms, same wiring as
  // delegateExpression/expression/scope) -- give it time to flush before saveXML().
  await page.waitForTimeout(1000)

  await page.getByRole('button', { name: 'Enregistrer le brouillon' }).click()
  expect(savedXml).toContain('flowable:formKey="document-generation"')
})

test('E2E-FORM-3 admin associates document templates to a dossier type and it persists', async ({ page }) => {
  await mockSession(page)
  await page.route(/\/api\/document-templates(?:\?|$)/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope([{ id: 'dt-1', templateKey: 'invoice', status: 'ACTIVE' }])),
    })
  })
  let updatedPayload: unknown = null
  await page.route('**/api/dossier-types/CLAIM', async (route) => {
    if (route.request().method() === 'PATCH') {
      updatedPayload = route.request().postDataJSON()
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ ...CLAIM_TYPE, documentTemplateKeys: ['invoice'] }),
      })
      return
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(CLAIM_TYPE) })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Types' }).click()
  await page.getByTestId('type-edit-fields').click()
  const panel = page.getByTestId('type-edit-fields-panel')
  await expect(panel.getByText('invoice')).toBeVisible()
  await panel.locator('[data-testid="document-template-key-invoice"]').check()
  await page.getByTestId('type-edit-fields-save').click()
  expect((updatedPayload as { documentTemplateKeys?: string[] })?.documentTemplateKeys).toEqual(['invoice'])
})

test('E2E-USERQ-1 user-question task shows the schema form and completes with the submitted answer', async ({
  page,
}) => {
  await mockSession(page)
  await page.route('**/api/workflow-tasks/mine*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(
        envelope([
          {
            taskId: 'task-1',
            name: 'Orientation',
            assigneeId: 'user-1',
            createdAt: '2026-08-12T00:00:00Z',
            processInstanceId: 'pi-1',
            dossierId: DOSSIER_ID,
            dossierReference: 'DOS-1',
            linkId: LINK_ID,
            slaRemainingHours: 12,
            formKey: 'user-question:orientation-dossier',
          },
        ]),
      ),
    })
  })
  await page.route('**/api/task-question-schemas/orientation-dossier/runtime*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        schemaKey: 'orientation-dossier',
        label: 'Orientation',
        fieldDefinitions: [
          { name: 'decision', label: 'Decision', type: 'SELECT', required: true, options: ['approve', 'reject'] },
        ],
      }),
    })
  })
  let completedWith: unknown = null
  await page.route('**/api/workflow-tasks/*/complete', async (route) => {
    completedWith = route.request().postDataJSON()
    await route.fulfill({ status: 204, body: '' })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Tâches' }).click()
  await expect(page.getByText('Orientation')).toBeVisible()
  const picker = page.locator('.user-question-task-form select')
  await picker.selectOption('approve')
  await page.locator('.user-question-task-form button').click()
  // Proves the answer leaves as a task-completion variable keyed by the FieldDefinition.name --
  // never a dossier field (no PATCH /api/dossiers/{id}/fields route mocked/expected here).
  expect(completedWith).toEqual({ variables: { decision: 'approve' } })
})

test('E2E-USERQ-2 admin creates a task question schema and it appears in the list', async ({ page }) => {
  await mockSession(page)
  let schemas: unknown[] = []
  await page.route('**/api/task-question-schemas*', async (route) => {
    if (route.request().method() === 'POST') {
      const body = route.request().postDataJSON() as { schemaKey: string; label: string; fieldDefinitions: unknown[] }
      const created = { id: 's-1', createdBy: 'admin-1', updatedBy: 'admin-1', ...body }
      schemas = [created]
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(created) })
      return
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope(schemas)) })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Questions de tâche' }).click()
  await page.getByTestId('schema-key-input').fill('orientation-dossier')
  await page.getByTestId('schema-label-input').fill('Orientation')
  await page.locator('[data-testid="schema-create-form"] button[type="submit"]').click()
  await expect(page.getByText('orientation-dossier')).toBeVisible()
})

