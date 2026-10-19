import { expect, test, type Page } from '@playwright/test'

type MockOpts = {
  roles?: string[]
  withActiveLink?: boolean
  dossierStatus?: string
  /** La tâche du lien n'a pas d'assigné (D-ASSIGN-CLAIM). */
  freeTask?: boolean
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
      assignee: opts.freeTask ? null : 'user-1',
      taskDefinitionKey: 'review',
      createTime: '2026-08-12T00:00:00Z',
    } as { taskId: string; name: string; assignee: string | null; taskDefinitionKey: string; createTime: string },
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
  // F3 (TODO §66, Phase 11): backfill() now fetches this in parallel with the list — without a
  // mock, the request 404s, Promise.all rejects, and backfill() never sets `items` either.
  await page.route('**/api/notifications/unread-count', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ count: 0 }),
    })
  })
  await page.route(/\/api\/notifications(\?|$)/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope([])),
    })
  })
  await page.route('**/api/notifications/*/read', async (route) => {
    await route.fulfill({ status: 204, body: '' })
  })
  await page.route('**/api/notifications/read-all', async (route) => {
    await route.fulfill({ status: 204, body: '' })
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
  await page.route('**/api/reports/operations', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ deadLetterJobCount: 2, unassignedOpenTaskCount: 4 }),
    })
  })
  await page.route('**/api/reports/bi-exports**', async (route) => {
    if (route.request().url().includes('/bi-exports/')) {
      await route.fulfill({
        status: 200,
        contentType: 'text/csv;charset=UTF-8',
        headers: { 'Content-Disposition': 'attachment; filename="sla-report-2026.csv"' },
        body: 'type,workflow\r\n',
      })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [
          {
            id: 'bi-1',
            kind: 'SLA',
            dossierTypeCode: null,
            fileName: 'sla-report-2026.csv',
            createdAt: '2026-08-01T00:00:00Z',
            sizeBytes: 12,
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      }),
    })
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
      const { assigneeId } = route.request().postDataJSON() as { assigneeId: string }
      tasks = tasks.map((task) => ({ ...task, assignee: assigneeId }))
      await route.fulfill({ status: 204, body: '' })
      return
    }
    if (url.includes('/claim') && route.request().method() === 'POST') {
      // Miroir de D-ASSIGN-CLAIM : prise réservée aux éditeurs, sur une tâche libre.
      if (!canEdit) {
        await route.fulfill({ status: 403, contentType: 'application/json', body: '{"status":403}' })
        return
      }
      tasks = tasks.map((task) => ({ ...task, assignee: task.assignee ?? 'user-1' }))
      await route.fulfill({ status: 204, body: '' })
      return
    }
    if (url.includes('/assignable-users')) {
      const admin = roles.some((role) => ['TENANT_ADMIN', 'WORKFLOW_ADMIN'].includes(role))
      await route.fulfill({
        status: admin ? 200 : 403,
        contentType: 'application/json',
        body: JSON.stringify(
          admin
            ? [
                { userId: 'user-1', displayName: 'Ada', email: 'ada@example.com' },
                { userId: 'user-2', displayName: 'Grace Hopper', email: 'grace@example.com' },
              ]
            : { status: 403 },
        ),
      })
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
  const connectorDefinitions = [
    {
      type: 'http-rest',
      label: 'HTTP REST',
      capability: 'BOTH',
      configSchema: [
        {
          name: 'webhookAuthType',
          label: 'Webhook auth',
          type: 'SELECT',
          required: true,
          defaultValue: 'HMAC_SHA256',
          options: ['HMAC_SHA256', 'SHARED_SECRET_TOKEN'],
        },
        { name: 'webhookSecret', label: 'Webhook secret', type: 'PASSWORD', required: true },
        { name: 'processKey', label: 'Process key', type: 'STRING', required: true },
        { name: 'dossierTypeCode', label: 'Dossier type', type: 'STRING', required: true },
      ],
    },
    {
      type: 'smtp',
      label: 'SMTP (ad hoc email send)',
      capability: 'SERVICE',
      configSchema: [],
    },
    {
      type: 'batch-import',
      label: 'Batch import (SFTP, CSV/Excel, cron trigger)',
      capability: 'TRIGGER',
      configSchema: [
        { name: 'sftpHost', label: 'SFTP host', type: 'STRING', required: true },
        { name: 'sftpPort', label: 'SFTP port', type: 'INTEGER', required: false, defaultValue: '22' },
        { name: 'sftpUsername', label: 'SFTP username', type: 'STRING', required: true },
        {
          name: 'sftpAuthType',
          label: 'SFTP auth',
          type: 'SELECT',
          required: true,
          defaultValue: 'PASSWORD',
          options: ['PASSWORD', 'PRIVATE_KEY'],
        },
        { name: 'sftpPassword', label: 'SFTP password', type: 'PASSWORD', required: false },
        {
          name: 'sftpPrivateKey',
          label: 'SFTP private key',
          type: 'SECRET_TEXTAREA',
          required: false,
          helpText: 'PEM-encoded SSH private key, required when sftpAuthType=PRIVATE_KEY',
        },
        { name: 'sftpHostKeyFingerprint', label: 'SFTP host key fingerprint', type: 'STRING', required: true },
        { name: 'remoteDirectory', label: 'Remote directory', type: 'STRING', required: true },
        { name: 'filenamePattern', label: 'Filename pattern', type: 'STRING', required: true },
        { name: 'archiveDirectory', label: 'Archive directory', type: 'STRING', required: true },
        { name: 'cronExpression', label: 'Cron expression', type: 'STRING', required: true },
        { name: 'processKey', label: 'Process key', type: 'STRING', required: true },
        { name: 'dossierTypeCode', label: 'Dossier type', type: 'STRING', required: true },
        { name: 'mapping', label: 'Row mapping', type: 'STRING', required: false },
      ],
    },
    {
      type: 'flowable-callback',
      label: 'Flowable message callback',
      capability: 'CALLBACK',
      configSchema: [
        {
          name: 'webhookAuthType',
          label: 'Webhook auth',
          type: 'SELECT',
          required: true,
          defaultValue: 'HMAC_SHA256',
          options: ['HMAC_SHA256', 'SHARED_SECRET_TOKEN'],
        },
        { name: 'webhookSecret', label: 'Webhook secret', type: 'PASSWORD', required: true },
        { name: 'messageName', label: 'BPMN message name', type: 'STRING', required: true },
        {
          name: 'correlationVariableName',
          label: 'Correlation process variable',
          type: 'STRING',
          required: true,
        },
        {
          name: 'correlationValuePath',
          label: 'Correlation value JSONPath',
          type: 'STRING',
          required: true,
        },
      ],
    },
  ]
  let connectors: Array<Record<string, unknown>> = []
  await page.route(/\/api\/connectors(?:\?|$)/, async (route) => {
    const method = route.request().method()
    if (method === 'POST') {
      const posted = route.request().postDataJSON() as {
        name: string
        connectorType: string
        enabled?: boolean
        config?: Record<string, unknown>
      }
      const created = {
        id: `c-${connectors.length + 1}`,
        tenantId: 'tenant_dev',
        name: posted.name,
        connectorType: posted.connectorType,
        enabled: posted.enabled !== false,
        config: posted.config ?? {},
        secretConfigured: {},
      }
      connectors = [...connectors, created]
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(created),
      })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope(connectors)),
    })
  })
  await page.route('**/api/connector-definitions*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope(connectorDefinitions)),
    })
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

test('E2E-START-VARS-1 start variables typed in the dialog are sent with the start request (TODO §93)', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await createAndOpenDossier(page)
  await page.getByRole('button', { name: 'Démarrer workflow' }).click()
  await page.getByText('Variables de démarrage').click()
  await page.getByRole('button', { name: 'Ajouter une variable' }).click()
  await page.getByRole('textbox', { name: 'Nom' }).fill('priorite')
  await page.getByRole('textbox', { name: 'Valeur' }).fill('haute')
  const request = page.waitForRequest(
    (r) => r.method() === 'POST' && /\/api\/dossiers\/[^/]+\/workflows$/.test(new URL(r.url()).pathname),
  )
  await page.locator('.dialog button.primary').click()
  expect((await request).postDataJSON()).toEqual({ processDefinitionKey: expect.any(String), variables: { priorite: 'haute' } })
})

test('E2E-START-VARS-2 a reserved start variable name is refused before any request (TODO §93)', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await createAndOpenDossier(page)
  let started = false
  page.on('request', (r) => {
    if (r.method() === 'POST' && /\/api\/dossiers\/[^/]+\/workflows$/.test(new URL(r.url()).pathname)) started = true
  })
  await page.getByRole('button', { name: 'Démarrer workflow' }).click()
  await page.getByText('Variables de démarrage').click()
  await page.getByRole('button', { name: 'Ajouter une variable' }).click()
  await page.getByRole('textbox', { name: 'Nom' }).fill('dossierId')
  await page.locator('.dialog button.primary').click()
  await expect(page.getByRole('alert').filter({ hasText: 'réservé' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Démarrer un workflow' })).toBeVisible()
  expect(started).toBe(false)
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
  await page.getByTestId('mode-source').click() // l'éditeur visuel est le mode par défaut (LC4b)
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
  await page.getByTestId('mode-source').click()
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

// TODO §76 (D1) — champs structurés : montant, référence externe, groupe répétable.
const RICH_TYPE = {
  ...CLAIM_TYPE,
  fieldDefinitions: [
    { name: 'montant', label: 'Montant', type: 'MONEY', required: false, options: ['EUR', 'USD'], group: null, sensitive: false },
    {
      name: 'fournisseur',
      label: 'Fournisseur',
      type: 'EXTERNAL_REF',
      required: false,
      options: ['d365fo'],
      group: null,
      sensitive: false,
    },
    {
      name: 'lignes',
      label: 'Lignes',
      type: 'REPEATABLE_GROUP',
      required: false,
      options: [],
      group: null,
      sensitive: false,
      minRows: 0,
      maxRows: 5,
      fields: [
        { name: 'poste', label: 'Poste', type: 'STRING', required: true, options: [], group: null, sensitive: false },
        { name: 'prix', label: 'Prix', type: 'MONEY', required: false, options: ['EUR'], group: null, sensitive: false },
      ],
    },
  ],
}
const RICH_FIELDS = {
  montant: { amount: '12.50', currency: 'EUR' },
  fournisseur: { system: 'd365fo', id: 'V-0042', label: 'Contoso' },
  lignes: [{ poste: 'Toiture', prix: { amount: '1000.00', currency: 'EUR' } }],
}

async function mockRichDossier(page: Page, canEdit = true) {
  await page.route('**/api/dossier-types/CLAIM', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(RICH_TYPE) }),
  )
  await page.route(`**/api/dossiers/${DOSSIER_ID}`, async (route) => {
    if (route.request().method() !== 'GET') return route.fallback()
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(dossierBody({ fields: RICH_FIELDS, canEdit })),
    })
  })
}

test('E2E-RICH-FIELDS-1 manager edits a money amount and a group row, only the changes are patched', async ({ page }) => {
  await mockSession(page)
  await mockRichDossier(page)
  await openApp(page)
  await createAndOpenDossier(page)

  await expect(page.getByLabel('Montant — montant')).toHaveValue('12.50')
  await expect(page.getByLabel('Montant — devise')).toHaveValue('EUR')
  await expect(page.getByLabel('Fournisseur — identifiant')).toHaveValue('V-0042')
  await expect(page.getByLabel('Poste (ligne 1)')).toHaveValue('Toiture')

  const fieldsRequest = page.waitForRequest((req) => req.method() === 'PATCH' && req.url().includes('/fields'))
  await page.getByLabel('Montant — montant').fill('15')
  await page.getByTestId('group-add-row').click()
  await page.getByLabel('Poste (ligne 2)').fill('Cuisine')
  await page.getByRole('button', { name: 'Enregistrer les champs' }).click()

  const body = (await fieldsRequest).postDataJSON() as { fields: Record<string, unknown> }
  expect(body.fields).toEqual({
    montant: { amount: '15', currency: 'EUR' },
    lignes: [
      { poste: 'Toiture', prix: { amount: '1000.00', currency: 'EUR' } },
      { poste: 'Cuisine' },
    ],
  })
  // La référence externe n'a pas été touchée : elle n'est pas renvoyée.
  expect(body.fields).not.toHaveProperty('fournisseur')
})

test('E2E-RICH-FIELDS-2 an unchanged amount typed without trailing zeros sends no patch', async ({ page }) => {
  await mockSession(page)
  await mockRichDossier(page)
  await openApp(page)
  await createAndOpenDossier(page)
  let patched = false
  page.on('request', (req) => {
    if (req.method() === 'PATCH' && req.url().includes('/fields')) patched = true
  })
  await page.getByLabel('Montant — montant').fill('12.5')
  await page.getByRole('button', { name: 'Enregistrer les champs' }).click()
  await page.waitForTimeout(300)
  expect(patched).toBe(false)
})

test('E2E-RICH-FIELDS-3 a viewer reads the structured values as text and as a table', async ({ page }) => {
  await mockSession(page, { roles: ['DOSSIER_VIEWER'] })
  await mockRichDossier(page, false)
  await openApp(page)
  await createAndOpenDossier(page)
  const fields = page.locator('.dossier-fields-editor')
  await expect(fields.getByText('12.50 EUR')).toBeVisible()
  await expect(fields.getByText('d365fo · V-0042 — Contoso')).toBeVisible()
  await expect(fields.locator('table')).toContainText('Toiture')
  await expect(fields.locator('table')).toContainText('1000.00 EUR')
  await expect(fields).not.toContainText('[object Object]')
  await expect(fields.getByRole('button', { name: 'Enregistrer les champs' })).toHaveCount(0)
})

// ---- TODO §80 (D1, règles) : champs calculés, visibilité, obligation conditionnelle -------------------------------------

const RULES_TYPE = {
  ...RICH_TYPE,
  fieldDefinitions: [
    { name: 'pays', label: 'Pays', type: 'STRING', required: false, options: [], group: null, sensitive: false },
    { name: 'ht', label: 'Montant HT', type: 'NUMBER', required: false, options: [], group: null, sensitive: false },
    { name: 'ttc', label: 'Montant TTC', type: 'NUMBER', required: false, options: [], group: null, sensitive: false, formula: 'dossier.fields.ht * 1.2' },
    { name: 'siret', label: 'SIRET', type: 'STRING', required: true, options: [], group: null, sensitive: false, visibleWhen: "dossier.fields.pays = 'FR'" },
    { name: 'justificatif', label: 'Justificatif', type: 'STRING', required: false, options: [], group: null, sensitive: false, requiredWhen: 'dossier.fields.ht > 1000' },
  ],
}

async function mockRulesDossier(page: Page) {
  await page.route('**/api/dossier-types/CLAIM', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(RULES_TYPE) }),
  )
  await page.route(`**/api/dossiers/${DOSSIER_ID}`, async (route) => {
    if (route.request().method() !== 'GET') return route.fallback()
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(dossierBody({ fields: { pays: 'FR', ht: 100, ttc: 120, siret: '12345678901234' }, canEdit: true })),
    })
  })
  const previewRequests: Array<Record<string, unknown>> = []
  await page.route('**/api/dossier-types/CLAIM/evaluate-rules', async (route) => {
    const fields = (route.request().postDataJSON() as { fields: Record<string, unknown> }).fields
    previewRequests.push(fields)
    const ht = Number(fields.ht ?? 0)
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        computed: fields.ht === undefined ? {} : { ttc: ht * 1.2 },
        hidden: fields.pays === 'FR' ? [] : ['siret'],
        requiredByRule: ht > 1000 ? ['justificatif'] : [],
        errors: [],
      }),
    })
  })
  return previewRequests
}

test('E2E-RULES-1 editing: a field becomes not applicable, the computed value follows, a rule makes a field required', async ({ page }) => {
  await mockSession(page)
  const previews = await mockRulesDossier(page)
  await openApp(page)
  await createAndOpenDossier(page)

  const fields = page.locator('.dossier-fields-editor')
  await expect(fields.getByTestId('computed-ttc')).toHaveText('120')
  await expect(fields.getByText('SIRET')).toBeVisible()

  await fields.getByLabel('Pays').fill('DE')
  await expect(fields.getByText('SIRET')).toHaveCount(0)

  await fields.getByLabel('Montant HT').fill('2000')
  await expect(fields.getByTestId('computed-ttc')).toHaveText('2400')
  await expect(fields.getByText('Justificatif').first()).toContainText('*')
  expect(previews.length).toBeGreaterThan(0)

  // Le champ calculé n'a pas de saisie et n'est jamais renvoyé ; le champ devenu sans objet est retiré, pas envoyé comme valeur.
  const fieldsRequest = page.waitForRequest((req) => req.method() === 'PATCH' && req.url().includes('/fields'))
  await fields.getByRole('button', { name: 'Enregistrer les champs' }).click()
  const body = (await fieldsRequest).postDataJSON() as { fields: Record<string, unknown> }
  expect(body.fields).not.toHaveProperty('ttc')
  expect(body.fields).toMatchObject({ pays: 'DE', ht: 2000 })
  expect(body.fields.siret ?? null).toBeNull()
})

test('E2E-RULES-2 a viewer reads the stored values, computed field included, without any preview call', async ({ page }) => {
  await mockSession(page, { roles: ['DOSSIER_VIEWER'] })
  const previews = await mockRulesDossier(page)
  await page.route(`**/api/dossiers/${DOSSIER_ID}`, async (route) => {
    if (route.request().method() !== 'GET') return route.fallback()
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(dossierBody({ fields: { pays: 'FR', ht: 100, ttc: 120, siret: '12345678901234' }, canEdit: false })),
    })
  })
  await openApp(page)
  await createAndOpenDossier(page)
  const fields = page.locator('.dossier-fields-editor')
  await expect(fields).toContainText('Montant TTC')
  await expect(fields).toContainText('120')
  await expect(fields).toContainText('12345678901234')
  await page.waitForTimeout(500)
  expect(previews).toHaveLength(0)
})

test('E2E-RICH-FIELDS-4 admin defines a type with a group and its columns', async ({ page }) => {
  let createdBody: Record<string, unknown> | null = null
  await mockSession(page)
  await page.route(/\/api\/dossier-types(?:\?|$)/, async (route) => {
    if (route.request().method() === 'POST') {
      createdBody = route.request().postDataJSON() as Record<string, unknown>
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({ ...RICH_TYPE, code: createdBody.code, fieldDefinitions: createdBody.fieldDefinitions }),
      })
      return
    }
    await route.fallback()
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Types' }).click()
  const form = page.getByTestId('type-create-form')
  await form.getByPlaceholder('code').fill('SUBV')
  await form.getByPlaceholder('libellé').fill('Subventions')
  await form.getByPlaceholder('workflowKey').fill('wf-subv')
  await form.getByTestId('field-def-add').click()
  await form.getByTestId('field-def-name').first().fill('lignes')
  await form.getByTestId('field-def-label').first().fill('Lignes budgétaires')
  await form.getByTestId('field-def-type').first().selectOption('REPEATABLE_GROUP')
  await form.getByTestId('field-def-max-rows').fill('10')
  const nested = form.getByTestId('field-def-subfields')
  await nested.getByTestId('field-def-add').click()
  await nested.getByTestId('field-def-name').fill('nature')
  await nested.getByTestId('field-def-label').fill('Nature')
  await nested.getByTestId('field-def-add').click()
  await nested.getByTestId('field-def-name').nth(1).fill('montant')
  await nested.getByTestId('field-def-label').nth(1).fill('Montant')
  await nested.getByTestId('field-def-type').nth(1).selectOption('MONEY')
  await nested.getByTestId('field-def-options-input').fill('EUR')
  await form.getByRole('button', { name: 'Créer' }).click()

  await expect.poll(() => createdBody !== null).toBe(true)
  const group = (createdBody!.fieldDefinitions as Array<Record<string, unknown>>)[0]!
  expect(group).toMatchObject({ name: 'lignes', type: 'REPEATABLE_GROUP', maxRows: 10, defaultValue: null })
  const subs = group.fields as Array<Record<string, unknown>>
  expect(subs.map((f) => [f.name, f.type])).toEqual([
    ['nature', 'STRING'],
    ['montant', 'MONEY'],
  ])
  expect(subs[1]).toMatchObject({ options: ['EUR'], defaultValue: null })
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

// NOTE: navigation to the BPMN editor goes through the "Éditer" link on
// /admin/workflow-types (client-side router push), like a user would. Direct
// loads of nested routes also work since
// DD-BASE <base href> (see E2E-BASE-1); they used to resolve "./assets/…" against
// the nested path and 404 the JS bundle.
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
  await expect.poll(() => putCalled).toBe(true)
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
  await expect.poll(() => putCalled).toBe(true)
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

const BPMN_WITH_SERVICE_TASK = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
             xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
             xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://flowforge/e2e">
  <process id="dmnFieldProcess" isExecutable="true">
    <startEvent id="start"/>
    <serviceTask id="svc" name="Evaluate"/>
    <endEvent id="end"/>
    <sequenceFlow id="flow1" sourceRef="start" targetRef="svc"/>
    <sequenceFlow id="flow2" sourceRef="svc" targetRef="end"/>
  </process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="dmnFieldProcess">
      <bpmndi:BPMNShape id="start_di" bpmnElement="start">
        <dc:Bounds x="150" y="100" width="36" height="36"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="svc_di" bpmnElement="svc">
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

test('E2E-EDIT-BPMN-5 serviceTask decisionTableReferenceKey field round-trips through saveXML', async ({
  page,
}) => {
  await mockSession(page)
  await page.route('**/api/workflow-bpmn-drafts/otherWorkflow', async (route) => {
    await route.fulfill({ status: 204, body: '' })
  })
  await page.route('**/api/workflow-bpmn-deployments/otherWorkflow/xml', async (route) => {
    await route.fulfill({ status: 204, body: '' })
  })
  let putBody = ''
  await page.route('**/api/workflow-bpmn-drafts/dmnFieldProcess', async (route) => {
    putBody = route.request().postData() ?? ''
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        processDefinitionKey: 'dmnFieldProcess',
        xml: '<definitions/>',
        updatedAt: '2026-09-11T00:00:00Z',
        updatedBy: 'user-1',
      }),
    })
  })
  await openApp(page)
  await openBpmnEditor(page)

  await page.locator('input[type="file"]').setInputFiles({
    name: 'dmn-field.bpmn20.xml',
    mimeType: 'application/xml',
    buffer: Buffer.from(BPMN_WITH_SERVICE_TASK),
  })
  await expect(page.locator('[data-element-id="svc"]')).toBeVisible()

  await page.locator('[data-element-id="svc"]').click()
  await page.getByLabel('Type (ex. "dmn")').fill('dmn')
  await page.getByLabel('Clé de la table de décision').fill('eligibilite')
  // Move focus off the debounced text field so its change commits before save.
  await page.keyboard.press('Tab')

  await page.getByRole('button', { name: 'Enregistrer le brouillon' }).click()
  await expect.poll(() => putBody.length > 0).toBe(true)
  expect(putBody).toContain('flowable:type=\\"dmn\\"')
  // Exact nesting, not just "these words appear somewhere" -- proves flowable:string is a real
  // child of flowable:field (both parsed correctly by FlowableBpmnParserAdapterTest server-side
  // with this identical shape), not a sibling or a stringified attribute value.
  expect(putBody).toContain(
    '<flowable:field name=\\"decisionTableReferenceKey\\"><flowable:string>eligibilite</flowable:string></flowable:field>',
  )
})

async function openDmnEditor(page: Page) {
  await page.getByRole('link', { name: 'Tables de décision' }).click()
  await expect(page.getByRole('heading', { name: 'Éditeur de table de décision DMN' })).toBeVisible()
  // Mirrors openBpmnEditor's .djs-element wait: the dmn-js Modeler is
  // constructed via dynamic import, then importXML runs asynchronously --
  // wait for the decision table's own rendered content (its name field),
  // otherwise saveXML() races ahead of the import and returns blank content.
  await expect(page.getByRole('textbox', { name: 'Decision name' })).toBeVisible()
}

test('E2E-EDIT-DMN-1 blank decision table (no draft, not yet deployed): save creates the draft', async ({
  page,
}) => {
  await mockSession(page)
  let putCalled = false
  let putBody = ''
  await page.route('**/api/dmn-decision-drafts/Decision_1', async (route) => {
    putCalled = true
    putBody = route.request().postData() ?? ''
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        decisionKey: 'Decision_1',
        xml: '<definitions/>',
        updatedAt: '2026-09-11T00:00:00Z',
        updatedBy: 'user-1',
      }),
    })
  })
  await openApp(page)
  await openDmnEditor(page)
  await expect(page.getByText(/non reconnue/)).toHaveCount(0)

  await page.getByRole('button', { name: 'Enregistrer le brouillon' }).click()
  await expect.poll(() => putCalled).toBe(true)
  expect(putBody).toContain('Decision_1')
})

test('E2E-EDIT-DMN-2 deploy the current decision table via the confirmation flow', async ({ page }) => {
  await mockSession(page)
  await page.route('**/api/dmn-decision-deployments/info*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ decisionKey: 'Decision_1', latestVersion: 1 }),
    })
  })
  let deployCalled = false
  await page.route('**/api/dmn-decision-deployments', async (route) => {
    deployCalled = true
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({ decisionKey: 'Decision_1', deployedVersion: 2, deploymentId: 'dep-dmn-1' }),
    })
  })
  await openApp(page)
  await openDmnEditor(page)

  await page.getByRole('button', { name: 'Déployer' }).click()
  await expect(page.getByText('Version actuelle déployée : 1')).toBeVisible()

  await page.getByRole('button', { name: 'Confirmer le déploiement' }).click()
  await expect(page.getByText(/Déployé : version 2/)).toBeVisible()
  expect(deployCalled).toBe(true)
})

const DMN_VERSIONS = [
  { decisionKey: 'Decision_1', version: 1, deploymentId: 'dep-1', deployedAt: '2026-09-01T00:00:00Z' },
  { decisionKey: 'Decision_1', version: 2, deploymentId: 'dep-2', deployedAt: '2026-09-02T00:00:00Z' },
]

test('E2E-DMN-TOOL-1 tester JSON shows the row list', async ({ page }) => {
  await mockSession(page)
  await page.route('**/api/dmn-decision-deployments/*/versions', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(DMN_VERSIONS) })
  })
  await page.route('**/api/dmn-decision-deployments/evaluate**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ decisionKey: 'Decision_1', version: 2, rows: [{ result: 'ok' }] }),
    })
  })
  await openApp(page)
  await openDmnEditor(page)
  await page.getByRole('button', { name: 'Tester' }).click()
  await page.getByLabel('Entrées JSON').fill('{"customerCat":"BRONZE"}')
  await page.getByRole('button', { name: 'Évaluer' }).click()
  await expect(page.locator('.dmn-editor__rows')).toContainText('ok')
})

test('E2E-DMN-TOOL-2 version selector is sent as a query parameter', async ({ page }) => {
  await mockSession(page)
  await page.route('**/api/dmn-decision-deployments/*/versions', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(DMN_VERSIONS) })
  })
  let evaluateUrl = ''
  await page.route('**/api/dmn-decision-deployments/evaluate**', async (route) => {
    evaluateUrl = route.request().url()
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ decisionKey: 'Decision_1', version: 1, rows: [{ result: 'old' }] }),
    })
  })
  await openApp(page)
  await openDmnEditor(page)
  await page.getByRole('button', { name: 'Tester' }).click()
  await page.locator('.dmn-editor__version').selectOption('1')
  await page.getByRole('button', { name: 'Évaluer' }).click()
  await expect.poll(() => evaluateUrl).toContain('version=1')
})

test('E2E-DMN-TOOL-3 JSON and CSV uploads show the pass/fail report', async ({ page }) => {
  await mockSession(page)
  await page.route('**/api/dmn-decision-deployments/*/versions', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(DMN_VERSIONS) })
  })
  await page.route('**/api/dmn-decision-deployments/test**', async (route) => {
    const csv = (route.request().headers()['content-type'] ?? '').includes('text/csv')
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        decisionKey: 'Decision_1',
        version: 2,
        results: [{ index: 0, passed: !csv, actualRows: [], failureMessage: csv ? 'mismatch' : null }],
      }),
    })
  })
  await openApp(page)
  await openDmnEditor(page)
  await page.getByRole('button', { name: 'Jeu de tests' }).click()
  await page.locator('.dmn-editor__suite-file').setInputFiles({
    name: 'suite.json',
    mimeType: 'application/json',
    buffer: Buffer.from('{"cases":[{"inputs":{},"expectedRows":[]}]}'),
  })
  await expect(page.getByText('Cas 1 : réussi')).toBeVisible()
  await page.locator('.dmn-editor__suite-file').setInputFiles({
    name: 'suite.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from('input.customerCat,expected.result\nBRONZE,ok\n'),
  })
  await expect(page.getByText(/Cas 1 : échec/)).toBeVisible()
})

test('E2E-DMN-TOOL-4 history lists versions after two deployments', async ({ page }) => {
  await mockSession(page)
  await page.route('**/api/dmn-decision-deployments/*/versions', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(DMN_VERSIONS) })
  })
  await openApp(page)
  await openDmnEditor(page)
  await page.getByRole('button', { name: 'Historique' }).click()
  await expect(page.getByText('Version 1 — 2026-09-01T00:00:00Z')).toBeVisible()
  await expect(page.getByText('Version 2 — 2026-09-02T00:00:00Z')).toBeVisible()
})

test('E2E-DMN-TOOL-5 redeploy confirmation can be cancelled', async ({ page }) => {
  await mockSession(page)
  await page.route('**/api/dmn-decision-deployments/*/versions', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(DMN_VERSIONS) })
  })
  let redeployed = 0
  await page.route('**/api/dmn-decision-deployments/*/redeploy/*', async (route) => {
    redeployed += 1
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ decisionKey: 'Decision_1', deployedVersion: 3, deploymentId: 'dep-3' }),
    })
  })
  await openApp(page)
  await openDmnEditor(page)
  await page.getByRole('button', { name: 'Historique' }).click()
  await page.getByRole('button', { name: 'Redéployer la version 1' }).click()
  await page.getByRole('button', { name: 'Annuler' }).click()
  expect(redeployed).toBe(0)
  await page.getByRole('button', { name: 'Redéployer la version 1' }).click()
  await page.getByRole('button', { name: 'Confirmer le redéploiement' }).click()
  await expect(page.getByText('Redéployé : version 3')).toBeVisible()
  expect(redeployed).toBe(1)
})

test('E2E-DMN-TOOL-6 opening the editor does not call tooling endpoints', async ({ page }) => {
  await mockSession(page)
  const tooling: string[] = []
  page.on('request', (request) => {
    const url = request.url()
    if (url.includes('/versions') || url.includes('/evaluate') || url.includes('/redeploy') || url.includes('/test')) {
      tooling.push(url)
    }
  })
  await openApp(page)
  await openDmnEditor(page)
  expect(tooling).toEqual([])
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

test('E2E-RPT-1 rapports shows operational counters and BI files', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'Rapports & SLA' }).click()
  await expect(page.getByTestId('kpi-dead-letter')).toContainText('2')
  await expect(page.getByTestId('kpi-unassigned')).toContainText('4')
  await expect(page.getByTestId('rapports-bi-exports')).toContainText('sla-report-2026.csv')
  const download = page.waitForRequest((request) => request.url().includes('/api/reports/bi-exports/bi-1'))
  await page.getByTestId('rapports-bi-download').click()
  await download
})

test('E2E-RPT-2 admin saves alerting and BI settings', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'Types' }).click()
  await page.getByTestId('sla-alerting-enabled').check()
  await page.getByTestId('sla-alert-emails').fill('ops@example.com')
  await page.getByTestId('sla-alert-webhooks').selectOption('wh-1')
  await page.getByTestId('sla-bi-enabled').check()
  const saved = page.waitForRequest(
    (request) => request.method() === 'PUT' && request.url().includes('/api/tenant/sla-settings'),
  )
  await page.getByTestId('sla-save').click()
  const request = await saved
  const body = request.postDataJSON() as {
    alertingEnabled: boolean
    alertEmails: string[]
    webhookIds: string[]
    biExportEnabled: boolean
  }
  expect(body.alertingEnabled).toBe(true)
  expect(body.alertEmails).toEqual(['ops@example.com'])
  expect(body.webhookIds).toEqual(['wh-1'])
  expect(body.biExportEnabled).toBe(true)
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

test('E2E-CONN-1 admin creates http-rest connector from generated form', async ({ page }) => {
  let createdBody: Record<string, unknown> | null = null
  let connectors: Array<Record<string, unknown>> = []
  await mockSession(page)
  await page.route(/\/api\/connectors(?:\?|$)/, async (route) => {
    if (route.request().method() === 'POST') {
      createdBody = route.request().postDataJSON() as Record<string, unknown>
      const created = {
        id: 'c-new',
        tenantId: 'tenant_dev',
        name: createdBody.name,
        connectorType: createdBody.connectorType,
        enabled: true,
        config: createdBody.config,
        secretConfigured: { webhookSecret: true },
      }
      connectors = [...connectors, created]
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(created),
      })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope(connectors)),
    })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Connecteurs' }).click()
  const form = page.getByTestId('connector-create-form')
  await form.getByPlaceholder('nom').fill('Hook prod')
  await form.getByTestId('connector-type').selectOption('http-rest')
  await form.getByTestId('connector-config-field-webhookSecret').fill('a-very-long-secret')
  await form.getByTestId('connector-config-field-processKey').fill('signal-smoke')
  await form.getByTestId('connector-config-field-dossierTypeCode').fill('SINISTRE')
  await expect(form.locator('textarea')).toHaveCount(0)
  await form.getByRole('button', { name: 'Créer' }).click()
  await expect.poll(() => createdBody?.connectorType).toBe('http-rest')
  await expect
    .poll(() => (createdBody?.config as Record<string, unknown> | undefined)?.webhookAuthType)
    .toBe('HMAC_SHA256')
  await expect
    .poll(() => (createdBody?.config as Record<string, unknown> | undefined)?.webhookSecret)
    .toBe('a-very-long-secret')
  await expect(page.getByText('Hook prod')).toBeVisible()
  await expect(page.getByTestId('connector-ingress-url')).toHaveText(
    /POST http:\/\/127\.0\.0\.1:\d+\/api\/webhooks\/c-new/,
  )
})

test('E2E-CONN-2 admin creates smtp connector with empty config', async ({ page }) => {
  let createdBody: Record<string, unknown> | null = null
  let connectors: Array<Record<string, unknown>> = []
  await mockSession(page)
  await page.route(/\/api\/connectors(?:\?|$)/, async (route) => {
    if (route.request().method() === 'POST') {
      createdBody = route.request().postDataJSON() as Record<string, unknown>
      const created = {
        id: 'c-smtp',
        tenantId: 'tenant_dev',
        name: createdBody.name,
        connectorType: createdBody.connectorType,
        enabled: true,
        config: createdBody.config ?? {},
        secretConfigured: {},
      }
      connectors = [...connectors, created]
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(created),
      })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope(connectors)),
    })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Connecteurs' }).click()
  const form = page.getByTestId('connector-create-form')
  await form.getByTestId('connector-type').selectOption('smtp')
  await expect(form.getByTestId('connector-config-empty')).toBeVisible()
  await expect(form.locator('textarea')).toHaveCount(0)
  await form.getByPlaceholder('nom').fill('Mailer')
  await form.getByRole('button', { name: 'Créer' }).click()
  await expect.poll(() => createdBody?.connectorType).toBe('smtp')
  await expect.poll(() => createdBody?.config).toEqual({})
  await expect(page.getByText('Mailer')).toBeVisible()
  await expect(page.getByTestId('connector-list').getByTestId('connector-ingress')).toHaveCount(0)
})

test('E2E-CONN-3 admin sees callback URL after creating flowable-callback', async ({ page }) => {
  let createdBody: Record<string, unknown> | null = null
  let connectors: Array<Record<string, unknown>> = []
  await mockSession(page)
  await page.route(/\/api\/connectors(?:\?|$)/, async (route) => {
    if (route.request().method() === 'POST') {
      createdBody = route.request().postDataJSON() as Record<string, unknown>
      const created = {
        id: 'c-cb',
        tenantId: 'tenant_dev',
        name: createdBody.name,
        connectorType: createdBody.connectorType,
        enabled: true,
        config: createdBody.config,
        secretConfigured: { webhookSecret: true },
      }
      connectors = [...connectors, created]
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(created),
      })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope(connectors)),
    })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Connecteurs' }).click()
  const form = page.getByTestId('connector-create-form')
  await form.getByPlaceholder('nom').fill('Async hook')
  await form.getByTestId('connector-type').selectOption('flowable-callback')
  await form.getByTestId('connector-config-field-webhookSecret').fill('a-very-long-secret')
  await form.getByTestId('connector-config-field-messageName').fill('orderReceived')
  await form.getByTestId('connector-config-field-correlationVariableName').fill('orderId')
  await form.getByTestId('connector-config-field-correlationValuePath').fill('$.orderId')
  await form.getByRole('button', { name: 'Créer' }).click()
  await expect.poll(() => createdBody?.connectorType).toBe('flowable-callback')
  await expect(page.getByText('Async hook')).toBeVisible()
  await expect(page.getByTestId('connector-ingress')).toContainText('URL callback')
  await expect(page.getByTestId('connector-ingress-url')).toHaveText(
    /POST http:\/\/127\.0\.0\.1:\d+\/api\/callbacks\/c-cb/,
  )
})

test('E2E-CONN-BATCH-IMPORT-KEY admin configures batch-import with a private key (textarea, not input)', async ({
  page,
}) => {
  // connecteurs-cible (lot B1), D-CONFIG-FIELD-SECRET-TEXTAREA : ConfigFieldType.PASSWORD rend un
  // <input type="password"> mono-ligne, incompatible avec une clé privée SSH PEM multi-lignes.
  let createdBody: Record<string, unknown> | null = null
  let connectors: Array<Record<string, unknown>> = []
  await mockSession(page)
  await page.route(/\/api\/connectors(?:\?|$)/, async (route) => {
    if (route.request().method() === 'POST') {
      createdBody = route.request().postDataJSON() as Record<string, unknown>
      const created = {
        id: 'c-batch',
        tenantId: 'tenant_dev',
        name: createdBody.name,
        connectorType: createdBody.connectorType,
        enabled: true,
        config: createdBody.config,
        secretConfigured: { sftpPrivateKey: true },
      }
      connectors = [...connectors, created]
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(created),
      })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope(connectors)),
    })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Connecteurs' }).click()
  const form = page.getByTestId('connector-create-form')
  await form.getByPlaceholder('nom').fill('Import nightly')
  await form.getByTestId('connector-type').selectOption('batch-import')
  await form.getByTestId('connector-config-field-sftpHost').fill('sftp.example.com')
  await form.getByTestId('connector-config-field-sftpUsername').fill('flowforge')
  await form.getByTestId('connector-config-field-sftpAuthType').selectOption('PRIVATE_KEY')

  const privateKeyField = form.getByTestId('connector-config-field-sftpPrivateKey')
  await expect(privateKeyField).toHaveJSProperty('tagName', 'TEXTAREA')
  const privateKey = '-----BEGIN OPENSSH PRIVATE KEY-----\nabc\ndef\n-----END OPENSSH PRIVATE KEY-----'
  await privateKeyField.fill(privateKey)

  await form.getByTestId('connector-config-field-sftpHostKeyFingerprint').fill('SHA256:abcdefgh')
  await form.getByTestId('connector-config-field-remoteDirectory').fill('/in')
  await form.getByTestId('connector-config-field-filenamePattern').fill('*.csv')
  await form.getByTestId('connector-config-field-archiveDirectory').fill('/archive')
  await form.getByTestId('connector-config-field-cronExpression').fill('0 0 * * * *')
  await form.getByTestId('connector-config-field-processKey').fill('importProcess')
  await form.getByTestId('connector-config-field-dossierTypeCode').fill('IMPORT')
  await form.getByRole('button', { name: 'Créer' }).click()

  await expect.poll(() => createdBody?.connectorType).toBe('batch-import')
  await expect
    .poll(() => (createdBody?.config as Record<string, unknown> | undefined)?.sftpPrivateKey)
    .toBe(privateKey)
  await expect(page.getByText('Import nightly')).toBeVisible()
})

// Must stay equal to SecurityHeadersCustomizer.CONTENT_SECURITY_POLICY (D-CSP-STRING A).
const A4_CSP =
  "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; frame-src 'self' blob:; worker-src 'self' blob:; frame-ancestors 'none'; object-src 'none'; base-uri 'self'"

async function injectDocumentCsp(page: Page) {
  await page.route('**/*', async (route) => {
    if (route.request().resourceType() !== 'document') {
      await route.continue()
      return
    }
    const response = await route.fetch()
    const headers = { ...response.headers(), 'content-security-policy': A4_CSP }
    await route.fulfill({
      status: response.status(),
      headers,
      body: await response.body(),
    })
  })
}

async function attachCspProbe(page: Page) {
  const consoleCsp: string[] = []
  page.on('console', (msg) => {
    if (/content.security.policy/i.test(msg.text())) {
      consoleCsp.push(msg.text())
    }
  })
  await page.addInitScript(() => {
    ;(window as unknown as { __cspViolations: string[] }).__cspViolations = []
    document.addEventListener('securitypolicyviolation', (event) => {
      const list = (window as unknown as { __cspViolations: string[] }).__cspViolations
      list.push(`${event.effectiveDirective}:${event.blockedURI}`)
    })
  })
  return {
    assertClean: async () => {
      const violations = await page.evaluate(
        () => (window as unknown as { __cspViolations?: string[] }).__cspViolations ?? [],
      )
      expect(violations, `CSP violations: ${violations.join('; ')}`).toEqual([])
      expect(consoleCsp, `CSP console: ${consoleCsp.join('; ')}`).toEqual([])
    },
  }
}

test('E2E-HDR-1 SPA renders under injected A4 CSP', async ({ page }) => {
  const probe = await attachCspProbe(page)
  await injectDocumentCsp(page)
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'Supervision' }).click()
  await expect(page.getByRole('heading', { name: 'Supervision' })).toBeVisible()
  await probe.assertClean()
})

test('E2E-HDR-2 template preview iframes remain visible under injected A4 CSP', async ({ page }) => {
  const probe = await attachCspProbe(page)
  await injectDocumentCsp(page)
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'Templates' }).click()
  await expect(page.getByRole('heading', { name: 'Templates documents' })).toBeVisible()
  // Éditeur visuel (LC4b) : il doit aussi fonctionner sous la CSP injectée (ProseMirror ne doit rien exiger d'inline interdit).
  await page.locator('form .ProseMirror').click()
  await page.keyboard.type('Bonjour')
  await expect(page.locator('iframe[title="Aperçu HTML"]')).toBeVisible()
  await page.getByRole('button', { name: 'Aperçu PDF' }).click()
  await expect(page.locator('iframe[title="Aperçu PDF"]')).toBeVisible()
  await probe.assertClean()
})

test('E2E-HDR-3 BPMN editor renders under injected A4 CSP', async ({ page }) => {
  const probe = await attachCspProbe(page)
  await injectDocumentCsp(page)
  await mockSession(page)
  await page.route('**/api/workflow-bpmn-drafts/otherWorkflow', async (route) => {
    await route.fulfill({ status: 204, body: '' })
  })
  await page.route('**/api/workflow-bpmn-deployments/otherWorkflow/xml', async (route) => {
    await route.fulfill({ status: 204, body: '' })
  })
  await openApp(page)
  await openBpmnEditor(page)
  await probe.assertClean()
})

test('E2E-HDR-4 DMN editor renders under injected A4 CSP', async ({ page }) => {
  const probe = await attachCspProbe(page)
  await injectDocumentCsp(page)
  await mockSession(page)
  await openApp(page)
  await openDmnEditor(page)
  await probe.assertClean()
})


// ---------------------------------------------------------------------------------------------
// TODO §68 (portail-externe) — page publique /portal/ (build Vite séparé) et panneau instructeur.
// ---------------------------------------------------------------------------------------------

const PORTAL_TOKEN = 'E2ePortalTokenAbcdefghijklmnopqrstuvwxyz0123'

const PORTAL_TASK = {
  dossierReference: 'DOS-2026-0042',
  taskName: 'Avis du tiers',
  instructions: 'Merci de confirmer vos disponibilités et de joindre le devis signé.',
  form: {
    label: 'Votre avis',
    fields: [
      { name: 'disponible', label: 'Disponible ?', type: 'BOOLEAN', required: true, options: [], group: null },
      { name: 'commentaire', label: 'Commentaire', type: 'STRING', required: false, options: [], group: null },
    ],
  },
  expiresAt: '2026-10-01T10:00:00Z',
  attachments: [],
}

test('E2E-PORTAL-1 external party answers from the e-mail link: form, attachment, submission', async ({ page }) => {
  const seen: { url: string; token?: string; cookie?: string }[] = []
  let submitted: unknown = null
  await page.route('**/api/portal/task/attachments', async (route) => {
    seen.push({ url: route.request().url(), token: route.request().headers()['x-portal-token'] })
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({ id: 'a1', label: 'Devis signé', filename: 'devis.pdf', sizeBytes: 9, addedAt: '2026-09-24T10:00:00Z' }),
    })
  })
  await page.route('**/api/portal/task/submit', async (route) => {
    seen.push({ url: route.request().url(), token: route.request().headers()['x-portal-token'] })
    submitted = route.request().postDataJSON()
    await route.fulfill({ status: 204, body: '' })
  })
  await page.route('**/api/portal/task', async (route) => {
    const headers = route.request().headers()
    seen.push({ url: route.request().url(), token: headers['x-portal-token'], cookie: headers['cookie'] })
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(PORTAL_TASK) })
  })

  await page.goto(`./portal/#${PORTAL_TOKEN}`)

  await expect(page.getByRole('heading', { name: 'Avis du tiers' })).toBeVisible()
  await expect(page.getByText('Dossier DOS-2026-0042')).toBeVisible()
  // D-PORTAL-CSRF-SESSION : le jeton a quitté la barre d'adresse et l'historique.
  await expect(page).toHaveURL(/\/portal\/$/)

  await page.locator('#field-disponible').check()
  await page.locator('#field-commentaire').fill('Disponible lundi')
  await page.locator('#portal-file').setInputFiles({ name: 'devis.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.7') })
  await page.locator('#portal-file-label').fill('Devis signé')
  await page.getByRole('button', { name: 'Déposer le fichier' }).click()
  await expect(page.getByTestId('portal-attachments')).toContainText('Devis signé')

  await page.getByRole('button', { name: 'Envoyer ma réponse' }).click()
  await expect(page.getByTestId('portal-done')).toBeVisible()

  expect(submitted).toEqual({ answers: { disponible: true, commentaire: 'Disponible lundi' } })
  expect(seen.length).toBe(3)
  for (const request of seen) {
    expect(request.token).toBe(PORTAL_TOKEN)
    expect(request.url).not.toContain(PORTAL_TOKEN)
    expect(request.cookie).toBeUndefined()
  }
})

test('E2E-PORTAL-2 invalid link shows the neutral screen and never touches the internal app', async ({ page }) => {
  const internalCalls: string[] = []
  page.on('request', (request) => {
    if (/\/api\/(auth|notifications|dossiers)/.test(request.url())) internalCalls.push(request.url())
  })
  await page.route('**/api/portal/task', async (route) => {
    await route.fulfill({
      status: 404,
      contentType: 'application/problem+json',
      body: JSON.stringify({ title: 'Lien invalide ou expiré', status: 404, detail: 'x' }),
    })
  })

  await page.goto(`./portal/#${PORTAL_TOKEN}`)

  await expect(page.getByTestId('portal-denied')).toBeVisible()
  await expect(page).toHaveURL(/\/portal\/$/)
  expect(internalCalls).toEqual([])
})

test('E2E-PORTAL-3 instructor resends then revokes an external party link from the dossier', async ({ page }) => {
  await mockSession(page)
  let links = [
    {
      id: 'pl-2', taskId: 'task-9', status: 'NOT_SENT', statusReason: 'NO_ADDRESS', recipient: null, ttlDays: 7,
      createdAt: '2026-09-24T09:00:00Z', sentAt: null, expiresAt: null, lastAccessedAt: null, completedAt: null,
      revokedAt: null, createdBy: 'flowable-task-created', revokedBy: null,
    },
  ]
  const calls: string[] = []
  await page.route('**/api/dossiers/*/portal-links**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (request.method() === 'POST' && path.endsWith('/tasks/task-9/send')) {
      calls.push('send')
      const active = { ...links[0]!, id: 'pl-3', status: 'ACTIVE', statusReason: null, recipient: 'tiers@example.org',
        sentAt: '2026-09-24T10:00:00Z', expiresAt: '2026-10-01T10:00:00Z' }
      links = [active, { ...links[0]!, status: 'REVOKED', statusReason: 'RESENT' }]
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(active) })
      return
    }
    if (request.method() === 'POST' && path.endsWith('/pl-3/revoke')) {
      calls.push('revoke')
      links = [{ ...links[0]!, status: 'REVOKED', statusReason: 'REVOKED_BY_USER' }, links[1]!]
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(links[0]) })
      return
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ canManage: true, links }) })
  })
  await openApp(page)
  await createAndOpenDossier(page)

  const panel = page.getByTestId('portal-links-panel')
  await expect(panel).toContainText('Non envoyé — aucune adresse e-mail renseignée')
  await panel.getByRole('button', { name: 'Envoyer' }).click()
  await expect(panel).toContainText('En attente du tiers')
  await expect(panel).toContainText('tiers@example.org')
  await panel.getByRole('button', { name: 'Révoquer' }).click()
  await expect(panel).toContainText('Révoqué — révoqué manuellement')
  expect(calls).toEqual(['send', 'revoke'])
})

test('E2E-SIG-1 instructor cancels and resends a signature request (S-PW-7)', async ({ page }) => {
  await mockSession(page)
  let status = 'PENDING'
  const calls: string[] = []
  const body = () => ({
    canManage: true,
    requests: [
      {
        id: 'sr-1', status, statusReason: null, mode: 'PARALLEL', simulated: true, providerCode: 'stub',
        sourceElementId: 'e1', deadlineAt: '2026-10-30T10:00:00Z', createdAt: '2026-09-30T10:00:00Z',
        completedAt: null, canManage: true,
        signers: [
          { id: 'sg-1', position: 0, kind: 'EXTERNAL', required: true, displayName: 'Alice',
            contact: 'a***@example.com', status: status === 'PENDING' ? 'PENDING' : 'REVOKED', decidedAt: null,
            declineReason: null, invited: true },
        ],
      },
    ],
  })
  await page.route('**/api/signature-requests/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    calls.push(path.endsWith('/cancel') ? 'cancel' : 'resend')
    if (path.endsWith('/cancel')) status = 'CANCELLED'
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body().requests[0]) })
  })
  await page.route('**/api/dossiers/*/signature-requests**', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body()) })
  })
  await openApp(page)
  await createAndOpenDossier(page)

  const panel = page.getByTestId('signature-requests-panel')
  await expect(panel).toContainText('SIMULATION — sans valeur juridique')
  await expect(panel).toContainText('a***@example.com')
  await panel.getByRole('button', { name: 'Renvoyer' }).click()
  await panel.getByRole('button', { name: 'Annuler la demande' }).click()
  await expect(panel).toContainText('Annulée')
  await expect(panel.getByRole('button', { name: 'Annuler la demande' })).toHaveCount(0)
  expect(calls).toEqual(['resend', 'cancel'])
})

test('E2E-SIG-2 internal signer signs from « Mes signatures » (S-PW-1)', async ({ page }) => {
  await mockSession(page)
  let items = [
    { signerId: 'sg-1', dossierId: DOSSIER_ID, dossierReference: 'DOS-1', documentLabel: 'Contrat',
      required: true, myTurn: true, deadlineAt: '2026-10-30T10:00:00Z', simulated: true },
    { signerId: 'sg-2', dossierId: DOSSIER_ID, dossierReference: 'DOS-1', documentLabel: 'Avenant',
      required: true, myTurn: false, deadlineAt: null, simulated: true },
  ]
  await page.route('**/api/signatures/mine**', async (route) => {
    const request = route.request()
    if (request.method() === 'POST') {
      items = items.filter((i) => i.signerId !== 'sg-1').map((i) => ({ ...i, myTurn: true }))
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ status: 'SIGNED' }) })
      return
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(items) })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Mes signatures' }).click()

  await expect(page.getByTestId('signatures-active')).toContainText('Contrat')
  await expect(page.getByTestId('signatures-waiting')).toContainText('Avenant')
  await expect(page.getByTestId('signatures-waiting').getByRole('button', { name: 'Signer' })).toHaveCount(0)
  await page.getByTestId('signatures-active').getByRole('button', { name: 'Signer' }).click()
  await expect(page.getByTestId('signatures-active')).toContainText('Avenant')
  await expect(page.getByTestId('signatures-waiting')).toHaveCount(0)
})

test('E2E-SIG-3 « Mes signatures » says the feature is off when the routes are absent (S-PW-8)', async ({ page }) => {
  await mockSession(page)
  await page.route('**/api/signatures/mine**', async (route) => {
    await route.fulfill({ status: 404, contentType: 'application/json', body: '{}' })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Mes signatures' }).click()
  await expect(page.getByTestId('signatures-unavailable')).toBeVisible()
})

test('E2E-PORTAL-4 BPMN editor marks a userTask as external, round-trips on save', async ({ page }) => {
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
        body: JSON.stringify({ processDefinitionKey: 'otherWorkflow', xml: savedXml, updatedAt: '2026-09-24T00:00:00Z', updatedBy: 'user-1' }),
      })
      return
    }
    await route.fulfill({ status: 204, body: '' })
  })
  await openApp(page)
  await openBpmnEditor(page)

  await page.locator('[data-element-id="wait"]').click()
  await page.getByLabel('Form key').fill('user-question:avis')
  await page.getByLabel(/Tiers externe — adresse/).fill('dossierField:contactEmail')
  await page.getByLabel(/Tiers externe — validité du lien/).fill('10')
  await page.waitForTimeout(1000)

  await page.getByRole('button', { name: 'Enregistrer le brouillon' }).click()
  await expect.poll(() => savedXml).toContain('flowable:portalRecipient="dossierField:contactEmail"')
  expect(savedXml).toContain('flowable:portalLinkTtlDays="10"')
  expect(savedXml).toContain('flowable:formKey="user-question:avis"')
})

test('E2E-PORTAL-5 portal page renders under the injected A4 CSP (no unsafe-eval, no violation)', async ({ page }) => {
  const probe = await attachCspProbe(page)
  await injectDocumentCsp(page)
  await page.route('**/api/portal/task', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(PORTAL_TASK) })
  })

  await page.goto(`./portal/#${PORTAL_TOKEN}`)

  await expect(page.getByRole('heading', { name: 'Avis du tiers' })).toBeVisible()
  await probe.assertClean()
})

// ---------------------------------------------------------------------------------------------------------------
// TODO §73 (palette-metier-bpmn) — briques métier dans l'éditeur BPMN (S-PW-1 à S-PW-5).
// Catalogue simulé : sous-ensemble des marqueurs du catalogue serveur (BuildingBlockCatalog).
// ---------------------------------------------------------------------------------------------------------------
function blockParam(over: Record<string, unknown> & { name: string; label: string }) {
  return {
    type: 'TEXT',
    binding: 'ATTRIBUTE',
    required: false,
    valueSource: null,
    enumValues: [],
    maxLength: 200,
    min: null,
    max: null,
    help: null,
    ...over,
  }
}

const BLOCK_CATALOG = {
  version: 1,
  blocks: [
    {
      id: 'set-dossier-status',
      label: 'Changer le statut du dossier',
      description: 'Modifie le statut du dossier courant.',
      category: 'DOSSIER',
      element: 'SERVICE_TASK',
      marker: { delegate: 'updateDossierStatusTask', serviceTaskType: null, formKeyPrefix: null, portalRecipient: false },
      parameters: [
        blockParam({
          name: 'targetDossierStatus',
          label: 'Statut du dossier',
          type: 'ENUM',
          required: true,
          enumValues: ['DRAFT', 'OUVERT', 'EN_COURS', 'SUSPENDU', 'CLOTURE', 'ARCHIVE'],
        }),
      ],
    },
    {
      id: 'generate-document',
      label: 'Générer un document',
      description: 'Génère un PDF depuis un template.',
      category: 'DOCUMENT',
      element: 'SERVICE_TASK',
      marker: { delegate: 'documentGenerationTask', serviceTaskType: null, formKeyPrefix: null, portalRecipient: false },
      parameters: [
        blockParam({
          name: 'documentType',
          label: 'Template de document',
          type: 'REFERENCE',
          required: true,
          valueSource: 'documentTemplates',
        }),
      ],
    },
    {
      id: 'dmn-decision',
      label: 'Décision DMN',
      description: 'Évalue une table de décision DMN.',
      category: 'DECISION',
      element: 'SERVICE_TASK',
      marker: { delegate: null, serviceTaskType: 'dmn', formKeyPrefix: null, portalRecipient: false },
      parameters: [
        blockParam({
          name: 'decisionTableReferenceKey',
          label: 'Table de décision',
          type: 'REFERENCE',
          binding: 'FIELD_ELEMENT',
          required: true,
          valueSource: 'dmnTables',
        }),
      ],
    },
    {
      id: 'request-signature',
      label: 'Demander une signature',
      description: 'Demande la signature d’un document PDF.',
      category: 'SIGNATURE',
      element: 'SERVICE_TASK',
      marker: { delegate: 'signatureRequestTask', serviceTaskType: null, formKeyPrefix: null, portalRecipient: false },
      parameters: [
        blockParam({ name: 'signatureDocument', label: 'Document à signer', type: 'SOURCE_REF', required: true }),
        blockParam({
          name: 'signers',
          label: 'Signataires',
          type: 'SIGNERS',
          binding: 'SIGNER_ELEMENTS',
          required: true,
        }),
      ],
    },
    {
      id: 'map-fields',
      label: 'Enregistrer des valeurs dans le dossier',
      description: 'Enregistre des valeurs calculées dans des champs du dossier.',
      category: 'DOSSIER',
      element: 'SERVICE_TASK',
      marker: { delegate: 'fieldMappingTask', serviceTaskType: null, formKeyPrefix: null, portalRecipient: false },
      parameters: [
        blockParam({
          name: 'fieldMapping',
          label: 'Valeurs à enregistrer',
          type: 'TEXT',
          required: true,
          maxLength: 8000,
          multiline: true,
        }),
      ],
    },
    {
      id: 'user-question',
      label: 'Question utilisateur',
      description: 'Tâche humaine.',
      category: 'HUMAN',
      element: 'USER_TASK',
      marker: { delegate: null, serviceTaskType: null, formKeyPrefix: 'user-question:', portalRecipient: false },
      parameters: [
        blockParam({
          name: 'schemaKey',
          label: 'Question',
          type: 'REFERENCE',
          binding: 'FORM_KEY_SUFFIX',
          required: true,
          valueSource: 'questionSchemas',
        }),
      ],
    },
  ],
}

function editableBpmn(processKey: string, tasks: string, taskIds: string[]): string {
  const shapes = taskIds
    .map(
      (id, i) => `<bpmndi:BPMNShape id="${id}_di" bpmnElement="${id}"><dc:Bounds x="${250 + i * 140}" y="80" width="100" height="80"/></bpmndi:BPMNShape>`,
    )
    .join('')
  return `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn"
             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
             targetNamespace="http://flowforge/e2e">
  <process id="${processKey}" isExecutable="true">
    <startEvent id="start"/>
    ${tasks}
  </process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="${processKey}">
      <bpmndi:BPMNShape id="start_di" bpmnElement="start"><dc:Bounds x="150" y="100" width="36" height="36"/></bpmndi:BPMNShape>
      ${shapes}
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>`
}

async function mockBlockApis(page: Page, opts: { catalog?: boolean; templates?: Array<{ value: string; label: string }> } = {}) {
  await mockSession(page)
  await page.route('**/api/workflow-bpmn-drafts/otherWorkflow', (route) => route.fulfill({ status: 204, body: '' }))
  await page.route('**/api/workflow-bpmn-deployments/otherWorkflow/xml', (route) => route.fulfill({ status: 204, body: '' }))
  await page.route('**/api/bpmn-building-blocks', (route) =>
    opts.catalog === false
      ? route.fulfill({ status: 500, body: '' })
      : route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(BLOCK_CATALOG) }),
  )
  await page.route('**/api/bpmn-building-blocks/references/documentTemplates', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(opts.templates ?? [{ value: 'contrat', label: 'Contrat de prêt' }]),
    }),
  )
  await page.route('**/api/bpmn-building-blocks/references/dmnTables', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        { value: 'eligibilite', label: 'eligibilite (v1)' },
        { value: 'scoring', label: 'scoring (v2)' },
      ]),
    }),
  )
  await page.route('**/api/task-question-schemas*', (route) => route.fulfill({ status: 403, body: '' }))
}

async function importBpmn(page: Page, xml: string, anchorId: string) {
  await page.locator('input[type="file"]').setInputFiles({
    name: 'blocks.bpmn20.xml',
    mimeType: 'application/xml',
    buffer: Buffer.from(xml),
  })
  await expect(page.locator(`[data-element-id="${anchorId}"]`)).toBeVisible()
}

test('E2E-PALETTE-1 pose une brique « Changer le statut », la paramètre, enregistre puis la retrouve reconnue', async ({
  page,
}) => {
  await mockBlockApis(page)
  let putBody = ''
  await page.route('**/api/workflow-bpmn-drafts/Process_1', async (route) => {
    putBody = route.request().postData() ?? ''
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ processDefinitionKey: 'Process_1', xml: '<definitions/>', updatedAt: '2026-09-29T00:00:00Z', updatedBy: 'u' }),
    })
  })
  await openApp(page)
  await openBpmnEditor(page)

  // S-UI-1 : le groupe « Briques métier » est dans la palette, à côté de la palette native.
  const entry = page.locator('[data-action="create-block-set-dossier-status"]')
  await expect(entry).toBeVisible()
  await expect(page.locator('[data-action="create-block-generate-document"]')).toBeVisible()
  await expect(page.locator('.djs-palette [data-action="create.task"]')).toBeVisible()

  await entry.click()
  await page.mouse.move(500, 300)
  await page.mouse.click(500, 300)
  const task = page.locator('.djs-element.djs-shape[data-element-id^="Activity_"], .djs-element.djs-shape[data-element-id^="ServiceTask_"]').first()
  await expect(task).toBeVisible()
  // bpmn-js ouvre l'édition directe du libellé à la pose : on la quitte (le nom de la brique est conservé).
  await page.keyboard.press('Escape')
  await task.click()

  await page.getByLabel('Statut du dossier').selectOption('CLOTURE')
  await page.getByRole('button', { name: 'Enregistrer le brouillon' }).click()
  await expect.poll(() => putBody.length > 0).toBe(true)
  expect(putBody).toContain('flowable:delegateExpression=\\"${updateDossierStatusTask}\\"')
  expect(putBody).toContain('flowable:targetDossierStatus=\\"CLOTURE\\"')
  expect(putBody).toContain('xmlns:flowable')

  // Rechargement (réimport du XML enregistré) : la brique est reconnue avec sa valeur (S-PW-1).
  const saved = JSON.parse(putBody).xml as string
  await importBpmn(page, saved, 'StartEvent_1')
  await page.locator('.djs-shape[data-element-id^="Activity_"], .djs-shape[data-element-id^="ServiceTask_"]').first().click()
  await expect(page.getByLabel('Statut du dossier')).toHaveValue('CLOTURE')
})

test('E2E-PALETTE-2 liste des templates issue de l’API ; référence supprimée ⇒ avertissement visible', async ({ page }) => {
  await mockBlockApis(page)
  const xml = editableBpmn(
    'otherWorkflow',
    '<serviceTask id="gen" name="Générer" flowable:delegateExpression="${documentGenerationTask}" flowable:documentType="supprime"/>',
    ['gen'],
  )
  await openApp(page)
  await openBpmnEditor(page)
  await importBpmn(page, xml, 'gen')
  await expect(page.locator('[data-element-id="gen"].fb-block-warning')).toBeVisible()
  await page.locator('[data-element-id="gen"]').click()
  const select = page.getByLabel('Template de document')
  await expect(select.locator('option', { hasText: 'Contrat de prêt' })).toHaveCount(1)
  await expect(select.locator('option', { hasText: 'supprime (introuvable)' })).toHaveCount(1)
  await expect(page.getByText(/n'existe plus/)).toBeVisible()
  await select.selectOption('contrat')
  await expect(page.locator('[data-element-id="gen"].fb-block-warning')).toHaveCount(0)
})

test('E2E-PALETTE-3 BPMN existant : tâches reconnues en briques, le reste en brut (section Avancé ouverte)', async ({ page }) => {
  await mockBlockApis(page)
  const xml = editableBpmn(
    'otherWorkflow',
    `<serviceTask id="st" name="Statut" flowable:delegateExpression="\${updateDossierStatusTask}" flowable:targetDossierStatus="OUVERT"/>
     <serviceTask id="raw" name="Brut" flowable:delegateExpression="\${autreDelegue}"/>`,
    ['st', 'raw'],
  )
  await openApp(page)
  await openBpmnEditor(page)
  await importBpmn(page, xml, 'st')

  await page.locator('[data-element-id="st"]').click()
  await expect(page.getByLabel('Statut du dossier')).toHaveValue('OUVERT')

  await page.locator('[data-element-id="raw"]').click()
  await expect(page.getByLabel('Statut du dossier')).toHaveCount(0)
  await expect(page.getByLabel('Delegate expression')).toBeVisible() // « Avancé » ouvert pour un élément brut
})

test('E2E-PALETTE-4 section Avancé : une valeur brute modifiée se reflète dans le panneau de brique', async ({ page }) => {
  await mockBlockApis(page)
  const xml = editableBpmn(
    'otherWorkflow',
    '<userTask id="q" name="Question" flowable:formKey="user-question:demande"/>',
    ['q'],
  )
  await openApp(page)
  await openBpmnEditor(page)
  await importBpmn(page, xml, 'q')
  await page.locator('[data-element-id="q"]').click()
  await expect(page.getByLabel('Question')).toHaveValue('demande') // liste indisponible ⇒ saisie libre

  await page.getByText('Avancé', { exact: true }).click()
  await page.getByLabel('Form key').fill('user-question:autre')
  await page.keyboard.press('Tab')
  await expect(page.getByLabel('Question')).toHaveValue('autre')
})

test('E2E-PALETTE-5 catalogue indisponible : l’éditeur reste utilisable en mode brut', async ({ page }) => {
  await mockBlockApis(page, { catalog: false })
  const xml = editableBpmn(
    'otherWorkflow',
    '<serviceTask id="st" name="Statut" flowable:delegateExpression="${updateDossierStatusTask}"/>',
    ['st'],
  )
  await openApp(page)
  await openBpmnEditor(page)
  await expect(page.locator('[data-action="create-block-set-dossier-status"]')).toHaveCount(0)
  await importBpmn(page, xml, 'st')
  await page.locator('[data-element-id="st"]').click()
  await expect(page.getByLabel('Delegate expression')).toHaveValue('${updateDossierStatusTask}')
})

test('E2E-PALETTE-6 brique signature : ajout d’un signataire dans la liste, écrit en <flowable:signer>', async ({ page }) => {
  await mockBlockApis(page)
  let putBody = ''
  await page.route('**/api/workflow-bpmn-drafts/otherWorkflow', async (route) => {
    if (route.request().method() === 'PUT') {
      putBody = route.request().postData() ?? ''
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ processDefinitionKey: 'otherWorkflow', xml: '', updatedAt: '2026-09-29T00:00:00Z', updatedBy: 'u' }),
      })
      return
    }
    await route.fulfill({ status: 204, body: '' })
  })
  const xml = editableBpmn(
    'otherWorkflow',
    '<serviceTask id="sig" name="Signer" flowable:delegateExpression="${signatureRequestTask}" flowable:signatureDocument="variable:doc"/>',
    ['sig'],
  )
  await openApp(page)
  await openBpmnEditor(page)
  await importBpmn(page, xml, 'sig')
  await expect(page.locator('[data-element-id="sig"].fb-block-warning')).toBeVisible() // aucun signataire, aucune attente
  await page.locator('[data-element-id="sig"]').click()
  await page.locator('[data-group-id="group-block-signers"] button.bio-properties-panel-add-entry').click()
  // Le nouvel élément de liste s'ouvre de lui-même mais le groupe reste replié : on l'ouvre.
  const source = page.getByLabel('Source (dossierField:<champ> ou variable:<nom>)')
  await page.locator('[data-group-id="group-block-signers"] .bio-properties-panel-group-header-title').click()
  await expect(source).toBeVisible()
  await source.fill('dossierField:emailClient')
  await page.keyboard.press('Tab')
  await page.getByRole('button', { name: 'Enregistrer le brouillon' }).click()
  await expect.poll(() => putBody.length > 0).toBe(true)
  expect(putBody).toContain('<flowable:signer')
  expect(putBody).toContain('source=\\"dossierField:emailClient\\"')
  expect(putBody).toContain('kind=\\"EXTERNAL\\"')
})

test('E2E-PALETTE-8 brique « Décision DMN » : la table se choisit dans la liste des tables déployées (LC1-suite)', async ({ page }) => {
  await mockBlockApis(page)
  let putBody = ''
  await page.route('**/api/workflow-bpmn-drafts/otherWorkflow', async (route) => {
    if (route.request().method() === 'PUT') {
      putBody = route.request().postData() ?? ''
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ processDefinitionKey: 'otherWorkflow', xml: '', updatedAt: '2026-09-30T00:00:00Z', updatedBy: 'u' }),
      })
      return
    }
    await route.fulfill({ status: 204, body: '' })
  })
  const xml = editableBpmn(
    'otherWorkflow',
    '<serviceTask id="dec" name="Décider" flowable:type="dmn"><extensionElements><flowable:field name="decisionTableReferenceKey"><flowable:string>scoring</flowable:string></flowable:field></extensionElements></serviceTask>',
    ['dec'],
  )
  await openApp(page)
  await openBpmnEditor(page)
  await importBpmn(page, xml, 'dec')
  await page.locator('[data-element-id="dec"]').click()
  const select = page.getByLabel('Table de décision', { exact: true })
  await expect(select.locator('option', { hasText: 'scoring (v2)' })).toHaveCount(1)
  await expect(select.locator('option', { hasText: 'eligibilite (v1)' })).toHaveCount(1)
  await select.selectOption('eligibilite')
  await page.getByRole('button', { name: 'Enregistrer le brouillon' }).click()
  await expect.poll(() => putBody.length > 0).toBe(true)
  expect(putBody).toContain('decisionTableReferenceKey')
  expect(putBody).toContain('eligibilite')
})

test('E2E-PALETTE-9 brique « Enregistrer des valeurs » : le mapping multiligne s’édite dans une zone de texte sans faux avertissement (D3b)', async ({
  page,
}) => {
  await mockBlockApis(page)
  let putBody = ''
  await page.route('**/api/workflow-bpmn-drafts/otherWorkflow', async (route) => {
    if (route.request().method() === 'PUT') {
      putBody = route.request().postData() ?? ''
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ processDefinitionKey: 'otherWorkflow', xml: '', updatedAt: '2026-10-01T00:00:00Z', updatedBy: 'u' }),
      })
      return
    }
    await route.fulfill({ status: 204, body: '' })
  })
  const xml = editableBpmn(
    'otherWorkflow',
    '<serviceTask id="map" name="Enrichir" flowable:delegateExpression="${fieldMappingTask}" flowable:fieldMapping="score = connectorResult.json.score&#10;ville = connectorResult.json.city"/>',
    ['map'],
  )
  await openApp(page)
  await openBpmnEditor(page)
  await importBpmn(page, xml, 'map')
  await page.locator('[data-element-id="map"]').click()
  const area = page.getByLabel('Valeurs à enregistrer', { exact: true })
  await expect(area).toHaveCount(1)
  await expect(area).toHaveJSProperty('tagName', 'TEXTAREA')
  await expect(area).toHaveValue('score = connectorResult.json.score\nville = connectorResult.json.city')
  await expect(page.getByText('retours à la ligne interdits')).toHaveCount(0)
  await area.fill('score = connectorResult.json.score\nville = connectorResult.json.city\nratio = 1')
  await page.getByRole('button', { name: 'Enregistrer le brouillon' }).click()
  await expect.poll(() => putBody.length > 0).toBe(true)
  expect(putBody).toContain('ratio = 1')
})

test('E2E-PALETTE-7 brique posée sur un diagramme importé sans xmlns:flowable : le marqueur survit à l’export (revue I3)', async ({
  page,
}) => {
  await mockBlockApis(page)
  let putBody = ''
  await page.route('**/api/workflow-bpmn-drafts/noNsProcess', async (route) => {
    putBody = route.request().postData() ?? ''
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ processDefinitionKey: 'noNsProcess', xml: '', updatedAt: '2026-09-30T00:00:00Z', updatedBy: 'u' }),
    })
  })
  const xml = editableBpmn('noNsProcess', '', []).replace(' xmlns:flowable="http://flowable.org/bpmn"', '')
  await openApp(page)
  await openBpmnEditor(page)
  await importBpmn(page, xml, 'start')
  await page.locator('[data-action="create-block-set-dossier-status"]').click()
  await page.mouse.move(500, 300)
  await page.mouse.click(500, 300)
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: 'Enregistrer le brouillon' }).click()
  await expect.poll(() => putBody.length > 0).toBe(true)
  expect(putBody).toContain('xmlns:flowable')
  expect(putBody).toContain('flowable:delegateExpression=\\"${updateDossierStatusTask}\\"')
})

test('E2E-SEARCH-1 supervision: filters on searchable fields of the chosen type reach the API and the URL (D2)', async ({ page }) => {
  await mockSession(page)
  const searchType = {
    ...CLAIM_TYPE,
    fieldDefinitions: [
      { name: 'montant', label: 'Montant', type: 'NUMBER', required: false, searchable: true },
      { name: 'note', label: 'Note', type: 'STRING', required: false },
      { name: 'iban', label: 'IBAN', type: 'STRING', required: false, sensitive: true },
    ],
  }
  await page.route(/\/api\/dossier-types(?:\?|$)/, async (route) => {
    if (route.request().method() !== 'GET') return route.fallback()
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope([searchType])) })
  })
  const listQueries: string[] = []
  await page.route('**/api/dossiers?**', async (route) => {
    if (route.request().method() !== 'GET') return route.fallback()
    listQueries.push(new URL(route.request().url()).search)
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
    })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Supervision' }).click()
  await expect(page.getByTestId('field-filters')).toHaveCount(0)

  await page.getByTestId('type-filter').selectOption('CLAIM')
  await expect(page.getByTestId('field-filters')).toBeVisible()
  await expect(page.getByTestId('field-filter-montant.gte')).toBeVisible()
  await expect(page.getByTestId('field-filters').getByText('Note')).toHaveCount(0)
  await expect(page.getByTestId('field-filters').getByText('IBAN')).toHaveCount(0)

  await page.getByTestId('field-filter-montant.gte').fill('5000')
  await page.getByRole('button', { name: 'Filtrer' }).click()
  await expect(page).toHaveURL(/f\.montant\.gte=5000/)
  expect(listQueries.some((q) => q.includes('dossierTypeCode=CLAIM') && q.includes('f.montant.gte=5000'))).toBe(true)
})

test('E2E-DRAFTS-1 an agent draft is reviewed and published by a human with the reviewed timestamp (D6)', async ({ page }) => {
  await mockSession(page)
  const stamp = '2026-10-01T10:00:00.123456Z'
  let published = false
  await page.route('**/api/admin/drafts/publications**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }),
  )
  await page.route('**/api/admin/drafts', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(
        published
          ? []
          : [
              {
                type: 'BPMN',
                key: 'claim',
                provenance: 'MCP',
                author: 'agent-1',
                updatedAt: stamp,
                hasProposedConfig: true,
                note: null,
                publishedVersion: 3,
                addedLines: 4,
                removedLines: 2,
                proposedConfig: {
                  label: 'Sinistre',
                  statusDefinitions: [{ code: 'STARTED', label: 'Démarré', initial: true, outcome: 'NONE' }],
                  allowedDossierStatuses: ['OUVERT'],
                  onStartDossierStatus: null,
                  observerUserIds: [],
                },
                configProvenance: 'MCP',
                configAuthor: 'agent-1',
              },
            ],
      ),
    }),
  )
  await page.route('**/api/admin/drafts/BPMN/claim/publish', async (route) => {
    published = true
    expect((route.request().postDataJSON() as { expectedUpdatedAt: string }).expectedUpdatedAt).toBe(stamp)
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ id: 'p1', version: 4, mode: 'MANUAL' }),
    })
  })
  page.on('dialog', (dialog) => dialog.accept())

  await openApp(page)
  await page.getByRole('link', { name: 'Brouillons' }).click()
  await expect(page.getByRole('heading', { name: 'Brouillons à publier' })).toBeVisible()
  const row = page.getByTestId('draft-row-claim')
  await expect(row).toContainText('Agent (MCP)')
  await expect(row).toContainText('agent-1')
  await expect(row).toContainText('configuration de workflow jointe')
  await row.getByTestId('draft-publish').click()
  await expect(page.getByTestId('drafts-notice')).toContainText('version 4')
  await expect(page.getByTestId('drafts-empty')).toBeVisible()
})

test('E2E-PROMO-1 import: dry-run plan first, secrets entered by name and sent only with the apply (D4)', async ({ page }) => {
  await mockSession(page)
  const plan = {
    bundleSha256: 'sha-e2e',
    formatVersion: 1,
    sourceLabel: 'recette',
    entries: [
      { type: 'DOSSIER_TYPE', key: 'SIN', action: 'CREATE', reason: '', secretsRequired: [] },
      { type: 'CONNECTOR_INSTANCE', key: 'erp-prod', action: 'BLOCKED', reason: 'cannot be created: webhookSecret is required', secretsRequired: ['webhookSecret'], connectorName: 'ERP Prod' },
    ],
  }
  let applyBody = ''
  await page.route('**/api/admin/configuration/imports**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }),
  )
  await page.route('**/api/admin/configuration/plan', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(plan) }),
  )
  await page.route('**/api/admin/configuration/apply', async (route) => {
    applyBody = route.request().postData() ?? ''
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        bundleSha256: 'sha-e2e',
        performedAt: '2026-10-01T10:00:00Z',
        entries: [
          { type: 'DOSSIER_TYPE', key: 'SIN', outcome: 'APPLIED', message: 'created' },
          { type: 'CONNECTOR_INSTANCE', key: 'erp-prod', outcome: 'APPLIED', message: 'created disabled — review its endpoints, then enable it' },
        ],
      }),
    })
  })
  page.on('dialog', (dialog) => dialog.accept())

  await openApp(page)
  await page.getByRole('link', { name: 'Promotion' }).click()
  await expect(page.getByRole('heading', { name: 'Promotion de configuration' })).toBeVisible()
  const exportRequest = page.waitForRequest('**/api/admin/configuration/export**')
  await page.route('**/api/admin/configuration/export**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/zip', body: 'zip' }),
  )
  await page.getByTestId('source-label').fill('recette')
  await page.getByTestId('export-button').click()
  expect((await exportRequest).url()).toContain('sourceLabel=recette')

  await page.getByTestId('bundle-file').setInputFiles({ name: 'bundle.zip', mimeType: 'application/zip', buffer: Buffer.from('zip') })
  await page.getByTestId('plan-button').click()
  await expect(page.getByTestId('plan-DOSSIER_TYPE-SIN')).toContainText('Créer')
  await expect(page.getByTestId('plan-CONNECTOR_INSTANCE-erp-prod')).toContainText('Bloqué')
  expect(applyBody).toBe('') // le plan n'écrit rien

  await page.getByTestId('secret-erp-prod-webhookSecret').fill('e2e-secret-value')
  await page.getByTestId('apply-button').click()
  await expect(page.getByTestId('report-table')).toContainText('created disabled')
  expect(applyBody).toContain('expectedBundleSha256')
  expect(applyBody).toContain('sha-e2e')
  expect(applyBody).toContain('e2e-secret-value')
  expect(applyBody).toContain('ERP Prod') // les secrets sont indexés par le nom exact du connecteur
  // les secrets saisis ne restent pas dans la page après l'application
  await expect(page.locator('body')).not.toContainText('e2e-secret-value')
  await expect(page.getByTestId('secret-erp-prod-webhookSecret')).toHaveValue('')
})

test('E2E-FORMED-1 designer: sections, half width and help text are previewed live and sent as plain fieldDefinitions (LC4a)', async ({ page }) => {
  await mockSession(page)
  let created: { fieldDefinitions: Array<Record<string, unknown>> } | null = null
  await page.route('**/api/task-question-schemas*', async (route) => {
    if (route.request().method() === 'POST') {
      created = route.request().postDataJSON()
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({ id: 's-9', ...created }) })
      return
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope([])) })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Questions de tâche' }).click()
  await page.getByTestId('schema-key-input').fill('contact')
  await page.getByTestId('schema-label-input').fill('Contact')
  const form = page.getByTestId('schema-create-form')
  await form.getByTestId('field-def-add').click()
  const row = form.getByTestId('field-def-row').first()
  await row.getByTestId('field-def-name').fill('nom')
  await row.getByTestId('field-def-label').fill('Nom')
  await row.getByTestId('field-def-group').fill('Identité')
  await row.getByTestId('field-def-width').selectOption('HALF')
  await row.getByTestId('field-def-help').fill('Nom de famille')

  const preview = form.getByTestId('form-designer-preview')
  await expect(preview.getByRole('heading', { name: 'Identité' })).toBeVisible()
  await expect(preview.getByTestId('help-nom')).toHaveText('Nom de famille')

  await form.locator('button[type="submit"]').click()
  await expect.poll(() => created?.fieldDefinitions[0]).toMatchObject({ name: 'nom', group: 'Identité', width: 'HALF', helpText: 'Nom de famille' })
})

test('E2E-ASSIST-1 design assistant: disabled notice, then a conversation that produces drafts to review (LC3)', async ({ page }) => {
  await mockSession(page)
  let enabled = false
  let sent: { message: string; conversationId: string | null } | null = null
  await page.route('**/api/assistant/status', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ enabled, model: enabled ? 'm' : null }) }))
  await page.route('**/api/assistant/conversations', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }))
  await page.route('**/api/assistant/conversations/c-1/actions', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }))
  await page.route('**/api/assistant/messages', async (route) => {
    sent = route.request().postDataJSON()
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        conversationId: 'c-1',
        reply: 'Brouillon du processus créé : relisez-le avant publication.',
        activity: [{ tool: 'deploy_workflow', ok: true, summary: '{}' }],
        producedDrafts: true,
        proposals: [],
      }),
    })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Assistant' }).click()
  await expect(page.getByTestId('assistant-disabled')).toBeVisible()
  await expect(page.getByTestId('assistant-input')).toHaveCount(0)

  enabled = true
  await page.getByRole('link', { name: 'Supervision' }).click()
  await page.getByRole('link', { name: 'Assistant' }).click()
  await page.getByTestId('assistant-input').fill('Un processus de validation à deux étapes')
  await page.getByTestId('assistant-send').click()
  await expect(page.getByTestId('assistant-log')).toContainText('relisez-le avant publication')
  await expect(page.getByTestId('assistant-drafts')).toBeVisible()
  expect(sent).toEqual({ message: 'Un processus de validation à deux étapes', conversationId: null })
})

test('E2E-PARCOURS-1 parcours designer: build a step list, save, compile to a draft to review (LC5)', async ({ page }) => {
  await mockSession(page)
  let saved: { label: string; definition: { steps: { id: string; kind: string; params: Record<string, string> }[] }; expectedVersion: number | null } | null = null
  let compiledWith: { dryRun: boolean } | null = null
  await page.route('**/api/bpmn-building-blocks/references/**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([{ value: 'avis', label: 'Avis' }]) }))
  await page.route('**/api/task-question-schemas**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope([{ schemaKey: 'avis', label: 'Avis' }])) }))
  await page.route('**/api/parcours', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }))
  await page.route('**/api/parcours/p1', async (route) => {
    saved = route.request().postDataJSON()
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ key: 'p1', label: 'Sinistre', version: 1, definition: saved!.definition, updatedAt: '2026-10-01T10:00:00Z', updatedBy: 'u' }),
    })
  })
  await page.route('**/api/parcours/p1/compile', async (route) => {
    compiledWith = route.request().postDataJSON()
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ xml: '<definitions/>', sha256: 'c'.repeat(64), warnings: ['avertissement test'], draftWritten: true, replacedExistingDraft: false }),
    })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Parcours' }).click()
  await expect(page.getByTestId('parcours-empty')).toBeVisible()
  await page.getByTestId('parcours-new').click()
  await page.getByTestId('parcours-key').fill('p1')
  await page.getByTestId('parcours-label').fill('Sinistre')
  await page.getByTestId('step-add').click()
  await page.getByTestId('param-schemaKey').selectOption('avis')
  await page.getByTestId('parcours-save').click()
  await expect(page.getByTestId('parcours-notice')).toContainText('version 1')
  expect(saved).toMatchObject({ label: 'Sinistre', expectedVersion: null })
  expect(saved!.definition.steps[0]).toMatchObject({ kind: 'QUESTION', params: { schemaKey: 'avis' } })
  await page.getByTestId('parcours-compile').click()
  await expect(page.getByTestId('parcours-draft-link')).toBeVisible()
  await expect(page.getByTestId('parcours-warnings')).toContainText('avertissement test')
  expect(compiledWith).toEqual({ dryRun: false, replaceDraft: false })
})

test('E2E-PARCOURS-2 parcours designer: dossier type, field mapping rows, live formula check and BPMN diagram preview', async ({ page }) => {
  await mockSession(page)
  let saved: { definition: { dossierTypeCode: string | null; steps: { params: Record<string, string> }[] } } | null = null
  const validated: string[] = []
  const bpmn = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" id="d" targetNamespace="t">
  <process id="p1" isExecutable="true">
    <startEvent id="start"/><sequenceFlow id="f1" sourceRef="start" targetRef="maj"/>
    <serviceTask id="maj" name="Mettre à jour"/><sequenceFlow id="f2" sourceRef="maj" targetRef="end"/>
    <endEvent id="end"/>
  </process>
  <bpmndi:BPMNDiagram id="dia"><bpmndi:BPMNPlane id="plane" bpmnElement="p1">
    <bpmndi:BPMNShape id="s_start" bpmnElement="start"><dc:Bounds x="100" y="100" width="36" height="36"/></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="s_maj" bpmnElement="maj"><dc:Bounds x="200" y="78" width="100" height="80"/></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="s_end" bpmnElement="end"><dc:Bounds x="360" y="100" width="36" height="36"/></bpmndi:BPMNShape>
    <bpmndi:BPMNEdge id="e_f1" bpmnElement="f1"><di:waypoint x="136" y="118"/><di:waypoint x="200" y="118"/></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="e_f2" bpmnElement="f2"><di:waypoint x="300" y="118"/><di:waypoint x="360" y="118"/></bpmndi:BPMNEdge>
  </bpmndi:BPMNPlane></bpmndi:BPMNDiagram>
</definitions>`
  await page.route('**/api/bpmn-building-blocks/references/**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }))
  await page.route('**/api/task-question-schemas**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope([])) }))
  await page.route(/\/api\/dossier-types(?:\?|$)/, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope([{
        id: 'dt-1', tenantId: 't', code: 'PERMIS', label: 'Permis riverain', workflowKey: 'p1', active: true, allowUnknownFields: false,
        fieldDefinitions: [
          { name: 'adresse', label: 'Adresse', type: 'STRING', required: true },
          { name: 'montant', label: 'Montant', type: 'NUMBER', required: false },
        ],
      }])),
    }))
  await page.route('**/api/expressions/validate', async (route) => {
    const { expression } = route.request().postDataJSON() as { expression: string }
    validated.push(expression)
    const valid = !expression.trim().endsWith('+')
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(valid ? { valid } : { valid, message: 'expression incomplète', position: 3 }) })
  })
  await page.route('**/api/parcours', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }))
  await page.route('**/api/parcours/p1', async (route) => {
    saved = route.request().postDataJSON()
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ key: 'p1', label: 'Permis', version: 1, definition: saved!.definition, updatedAt: '2026-10-01T10:00:00Z', updatedBy: 'u' }),
    })
  })
  await page.route('**/api/parcours/preview', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ xml: bpmn, sha256: 'a'.repeat(64), warnings: [], draftWritten: false, replacedExistingDraft: false }) }))

  await openApp(page)
  await page.getByRole('link', { name: 'Parcours' }).click()
  await page.getByTestId('parcours-new').click()
  await page.getByTestId('parcours-dossier-type').selectOption('PERMIS')
  await expect(page.getByTestId('parcours-key')).toHaveValue('p1')
  await page.getByTestId('parcours-label').fill('Permis')

  await page.getByTestId('step-add').click()
  await page.getByTestId('step-kind').selectOption('EXPRESSION')
  await page.getByTestId('param-formula').fill('1 +')
  await expect(page.getByTestId('param-formula-status')).toContainText('expression incomplète')
  await page.getByTestId('param-formula').fill('')
  await page.getByTestId('param-formula-insert').selectOption('dossier.fields.montant')
  await expect(page.getByTestId('param-formula')).toHaveValue('dossier.fields.montant')
  await expect(page.getByTestId('param-formula-status')).toContainText('Formule valide')
  await page.getByTestId('param-formulaResult').fill('montantCalcule')

  await page.getByTestId('step-add').click()
  await page.getByTestId('step-kind').nth(1).selectOption('MAP_FIELDS')
  await page.getByTestId('mapping-field').selectOption('adresse')
  await page.getByTestId('mapping-expression-insert').selectOption('variables.montantCalcule')
  await expect(page.getByTestId('mapping-warning')).toHaveCount(0)

  await page.getByTestId('parcours-save').click()
  await expect(page.getByTestId('parcours-notice')).toContainText('version 1')
  expect(saved!.definition.dossierTypeCode).toBe('PERMIS')
  expect(saved!.definition.steps[1].params).toEqual({ fieldMapping: 'adresse = variables.montantCalcule' })
  expect(validated).toContain('1 +')

  await page.getByTestId('parcours-preview').click()
  await expect(page.getByTestId('parcours-diagram').locator('[data-element-id="maj"]')).toBeVisible()
  await expect(page.getByTestId('parcours-xml')).toBeHidden()
})

test('E2E-I18N-1 language switch: the whole shell turns English, survives a reload, and can go back (D7)', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await expect(page.getByRole('link', { name: 'Tâches' })).toBeVisible()
  await page.getByTestId('locale-switcher').selectOption('en')
  await expect(page.getByRole('link', { name: 'Tasks', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Log out' })).toBeVisible()
  await expect(page.locator('html')).toHaveAttribute('lang', 'en')
  await page.reload()
  await expect(page.getByRole('link', { name: 'Tasks', exact: true })).toBeVisible()
  await page.getByTestId('locale-switcher').selectOption('fr')
  await expect(page.getByRole('link', { name: 'Tâches' })).toBeVisible()
  await expect(page.locator('html')).toHaveAttribute('lang', 'fr')
})

// DD-BASE : index.html porte <base href="/"> (réécrit par le backend en <base href="{context-path}/">).
// Sans elle, un chargement direct d'une route imbriquée résolvait "./assets/…" contre cette route
// (page blanche) et le préfixe API/logout reprenait le 1er segment de la route (/dossiers/api/…).
test('E2E-BASE-1 a nested route loaded directly renders the app', async ({ page }) => {
  await mockSession(page)
  await page.goto('/dossiers/new')
  await expect(page.getByText('Ada')).toBeVisible()
  await expect(page.locator('#dossier-label')).toBeVisible()
})

test('E2E-BASE-2 logout from a nested route posts to /api/auth/logout', async ({ page }) => {
  await mockSession(page)
  await page.route('**/auth/logout', (route) => route.fulfill({ status: 200, body: 'ok' }))
  await openApp(page)
  await page.getByRole('link', { name: 'Nouveau dossier' }).click()
  await expect(page).toHaveURL(/\/dossiers\/new$/)
  const logout = page.waitForRequest((request) => request.method() === 'POST' && request.url().includes('auth/logout'))
  await page.getByRole('button', { name: 'Déconnexion' }).click()
  expect(new URL((await logout).url()).pathname).toBe('/api/auth/logout')
})
