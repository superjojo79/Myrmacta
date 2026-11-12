// Données et helpers partagés des tests e2e « pilotage » (ex-pilotage.spec.ts, découpé par écran : TODO §95).
import { expect, type Page } from '@playwright/test'

export type MockOpts = {
  roles?: string[]
  withActiveLink?: boolean
  dossierStatus?: string
  /** La tâche du lien n'a pas d'assigné (D-ASSIGN-CLAIM). */
  freeTask?: boolean
  /** TODO §94 : une étape du lien est en incident (job en dead letter). */
  withIncident?: boolean
  /** TODO §114 : « Tâches » contient une tâche libre d'un groupe de l'utilisateur. */
  groupTask?: boolean
}

export const DOSSIER_ID = '11111111-1111-1111-1111-111111111111'

export const LINK_ID = '22222222-2222-2222-2222-222222222222'

export function envelope(content: unknown[], extras: Record<string, number> = {}) {
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

export const CLAIM_TYPE = {
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

export const SLA_REPORT = {
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

export const OTHER_WORKFLOW = {
  id: '1',
  tenantId: 'tenant_dev',
  workflowKey: 'otherWorkflow',
  label: 'Autre',
  statusDefinitions: [],
  allowedDossierStatuses: [],
  onStartDossierStatus: null,
  observerUserIds: [],
}

export function dossierBody(overrides: Record<string, unknown> = {}) {
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

export async function mockSession(page: Page, opts: MockOpts = {}) {
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
  ] as Array<Record<string, unknown>>
  if (opts.groupTask) {
    mineTasks = [
      {
        taskId: 'task-group',
        name: 'Trier le courrier',
        assigneeId: null,
        createdAt: '2026-10-31T08:00:00Z',
        processInstanceId: 'pi-2',
        dossierId: DOSSIER_ID,
        dossierReference: 'DOS-COURRIER-2026-000001',
        linkId: LINK_ID,
      },
    ]
  }
  let tasks = [
    {
      taskId: 'task-1',
      name: 'Review',
      assignee: opts.freeTask ? null : 'user-1',
      taskDefinitionKey: 'review',
      createTime: '2026-08-12T00:00:00Z',
      candidateGroups: opts.freeTask ? ['COURRIER'] : [],
    } as {
      taskId: string
      name: string
      assignee: string | null
      taskDefinitionKey: string
      createTime: string
      candidateGroups?: string[]
    },
  ]
  let elements: Array<Record<string, unknown>> = []

  await page.route('**/api/workflow-start-variables**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ variables: [] }),
    })
  })
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
  // TODO §121 : le calendrier garde son état entre les appels (saisies, sources, aperçus, rechargements, suppressions).
  type CalendarEntryMock = { start: string; end: string; nature: string; label: string | null }
  type CalendarSourceMock = {
    id: string
    name: string
    kind: string
    maskedUrl: string | null
    entryCount: number
    loadedAt: string
    loadedBy: string
    lastError: string | null
    entries: CalendarEntryMock[]
  }
  const calendar = {
    tenantId: 'tenant_dev',
    zoneId: 'UTC',
    weekend: 'SATURDAY_SUNDAY',
    entries: [] as CalendarEntryMock[],
    sources: [] as CalendarSourceMock[],
    holidays: [] as string[],
    updatedAt: null as string | null,
    updatedBy: null as string | null,
  }
  const closedDay = (start: string, end: string, label: string): CalendarEntryMock => ({ start, end, nature: 'CLOSED', label })
  const sourceOf = (id: string, name: string, kind: string, entries: CalendarEntryMock[]): CalendarSourceMock => ({
    id,
    name,
    kind,
    maskedUrl: null,
    entryCount: entries.length,
    loadedAt: '2026-11-07T10:00:00Z',
    loadedBy: 'admin',
    lastError: null,
    entries,
  })
  /** Champ texte d'un corps multipart brut. */
  const multipartField = (body: string, name: string): string => new RegExp(`name="${name}"\\r\\n\\r\\n([^\\r]*)`).exec(body)?.[1] ?? ''
  const json = (status: number, body: unknown) => ({ status, contentType: 'application/json', body: JSON.stringify(body) })
  /** Corps JSON d'une requête (sources par URL), nul pour un corps multipart ou vide. */
  const jsonBody = (request: { headers(): Record<string, string>; postData(): string | null }): Record<string, unknown> | null => {
    const raw = request.postData()
    return raw && (request.headers()['content-type'] ?? '').includes('json') ? (JSON.parse(raw) as Record<string, unknown>) : null
  }
  /** Jamais l'URL entière : seulement `schéma://hôte/…`, comme le serveur. */
  const maskedOf = (url: string): string => `${new URL(url).origin}/…`
  await page.route(/\/api\/tenant\/calendar(\/.*)?$/, async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace(/^.*\/api\/tenant\/calendar/, '')
    const method = request.method()
    const body = request.postData() ?? ''
    if (path === '' && method === 'PUT') {
      const sent = request.postDataJSON() as { zoneId: string; weekend: string; entries: CalendarEntryMock[] }
      calendar.zoneId = sent.zoneId
      calendar.weekend = sent.weekend
      calendar.entries = sent.entries
      calendar.updatedAt = '2026-11-07T00:00:00Z'
      calendar.updatedBy = 'admin'
    } else if (path === '/belgian-federal-holidays' && method === 'POST') {
      const entries = [closedDay('2026-12-25', '2026-12-25', 'Noël'), closedDay('2027-01-01', '2027-01-01', "Jour de l'an")]
      calendar.sources = [...calendar.sources.filter((source) => source.kind !== 'BE_FEDERAL'), sourceOf('src-be', 'Fériés fédéraux belges', 'BE_FEDERAL', entries)]
    } else if (path === '/sources/preview' && method === 'POST' && jsonBody(request)) {
      const sent = jsonBody(request) as { name: string }
      await route.fulfill(
        json(200, {
          sourceId: 'src-new-url',
          name: sent.name,
          kind: 'ICS_URL',
          added: [{ start: '2026-12-24', end: '2026-12-26' }],
          removed: [],
          masked: [],
          ignored: {},
          sha256: 'sha-url',
          periodCount: 1,
        }),
      )
      return
    } else if (path === '/sources' && method === 'POST' && jsonBody(request)) {
      const sent = jsonBody(request) as { name: string; url: string; expectedSha256: string }
      if (sent.expectedSha256 !== 'sha-url') {
        await route.fulfill(json(409, { status: 409, detail: 'The content changed since the preview' }))
        return
      }
      const created = sourceOf('src-url', sent.name, 'ICS_URL', [closedDay('2026-12-24', '2026-12-26', 'Agenda')])
      created.maskedUrl = maskedOf(sent.url)
      calendar.sources = [...calendar.sources, created]
      await route.fulfill(json(201, calendar))
      return
    } else if (/^\/sources\/[^/]+\/preview$/.test(path) && method === 'POST' && calendar.sources.some((source) => source.id === path.split('/')[2] && source.kind === 'ICS_URL')) {
      await route.fulfill(
        json(200, {
          sourceId: path.split('/')[2],
          name: calendar.sources.find((source) => source.id === path.split('/')[2])?.name ?? '',
          kind: 'ICS_URL',
          added: [{ start: '2027-01-11', end: '2027-01-11' }],
          removed: [{ start: '2026-12-24', end: '2026-12-26' }],
          masked: [],
          ignored: {},
          sha256: 'sha-url-2',
          periodCount: 1,
        }),
      )
      return
    } else if (/^\/sources\/[^/]+\/reload$/.test(path) && method === 'POST' && jsonBody(request)) {
      const id = path.split('/')[2]
      const sent = jsonBody(request) as { url: string | null; expectedSha256: string }
      if ((sent.url ?? '').includes('stale')) {
        await route.fulfill(json(409, { status: 409, detail: 'The content changed since the preview' }))
        return
      }
      calendar.sources = calendar.sources.map((source) => {
        if (source.id !== id) return source
        const reloaded = sourceOf(id, source.name, source.kind, [closedDay('2027-01-11', '2027-01-11', 'Reprise')])
        reloaded.maskedUrl = sent.url ? maskedOf(sent.url) : source.maskedUrl
        return reloaded
      })
    } else if (path === '/sources/preview' && method === 'POST') {
      await route.fulfill(
        json(200, {
          sourceId: 'src-new',
          name: multipartField(body, 'name'),
          kind: 'ICS_FILE',
          added: [{ start: '2026-12-28', end: '2026-12-31' }],
          removed: [],
          masked: [],
          ignored: { TIMED: 1 },
          sha256: 'sha-new',
          periodCount: 1,
        }),
      )
      return
    } else if (path === '/sources' && method === 'POST') {
      if (multipartField(body, 'expectedSha256') !== 'sha-new') {
        await route.fulfill(json(409, { status: 409, detail: 'The content changed since the preview' }))
        return
      }
      calendar.sources = [...calendar.sources, sourceOf('src-ics', multipartField(body, 'name'), 'ICS_FILE', [closedDay('2026-12-28', '2026-12-31', 'Congé')])]
      await route.fulfill(json(201, calendar))
      return
    } else if (/^\/sources\/[^/]+\/preview$/.test(path) && method === 'POST') {
      await route.fulfill(
        json(200, {
          sourceId: path.split('/')[2],
          name: calendar.sources.find((source) => source.id === path.split('/')[2])?.name ?? '',
          kind: 'ICS_FILE',
          added: [{ start: '2027-01-04', end: '2027-01-04' }],
          removed: [{ start: '2026-12-28', end: '2026-12-31' }],
          masked: [],
          ignored: {},
          sha256: 'sha-reload',
          periodCount: 1,
        }),
      )
      return
    } else if (/^\/sources\/[^/]+\/reload$/.test(path) && method === 'POST') {
      const id = path.split('/')[2]
      if (body.includes('filename="stale.ics"')) {
        await route.fulfill(json(409, { status: 409, detail: 'The content changed since the preview' }))
        return
      }
      calendar.sources = calendar.sources.map((source) =>
        source.id === id ? sourceOf(id, source.name, source.kind, [closedDay('2027-01-04', '2027-01-04', 'Rentrée')]) : source,
      )
    } else if (/^\/sources\/[^/]+$/.test(path) && method === 'DELETE') {
      calendar.sources = calendar.sources.filter((source) => source.id !== path.split('/')[2])
      await route.fulfill({ status: 204 })
      return
    }
    await route.fulfill(json(200, calendar))
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
          migrationStatus: {
            appliedChangesetsCount: 45,
            lastAppliedChangesetId: '045-dossier-deadline',
            lastAppliedAt: '2026-11-01T10:00:00Z',
          },
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
  await page.route('**/api/dossier-types/*/reply-workflow', async (route) => {
    await route.fulfill({ status: 204, body: '' })
  })
  await page.route('**/api/dossier-types/*/deadline', async (route) => {
    await route.fulfill({ status: 204, body: '' })
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
  // TODO §94 : un incident à état — message réservé aux administrateurs, rejeu qui le fait disparaître.
  const incidentAdmin = roles.some((role) => ['TENANT_ADMIN', 'WORKFLOW_ADMIN'].includes(role))
  let incidents = opts.withIncident
    ? [
        {
          jobId: 'job-1',
          activityId: 'mailRefus',
          activityName: 'E-mail : refus',
          state: 'INCIDENT',
          since: '2026-10-18T18:51:26Z',
          retriesLeft: 0,
          message: 'notificationTo source dossierField:email is not a single e-mail address',
        },
      ]
    : []
  const visibleIncidents = () =>
    incidents.map((incident) => (incidentAdmin ? incident : { ...incident, message: undefined }))
  await page.route('**/api/admin/incidents**', async (route) => {
    if (!incidentAdmin) {
      await route.fulfill({ status: 403, contentType: 'application/json', body: '{"status":403,"detail":"denied"}' })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: incidents.map((incident) => ({
          ...incident,
          linkId: LINK_ID,
          dossierId: DOSSIER_ID,
          dossierReference: 'DOS-TEST-1',
          processDefinitionKey: 'claimWorkflow',
        })),
        page: 0,
        size: 20,
        totalElements: incidents.length,
        totalPages: incidents.length > 0 ? 1 : 0,
        truncated: false,
      }),
    })
  })
  await page.route('**/api/workflow-links/**', async (route) => {
    const url = route.request().url()
    if (url.includes('/incidents/') && url.endsWith('/retry') && route.request().method() === 'POST') {
      if (!incidentAdmin) {
        await route.fulfill({ status: 403, contentType: 'application/json', body: '{"status":403}' })
        return
      }
      incidents = []
      await route.fulfill({ status: 204, body: '' })
      return
    }
    if (url.endsWith('/incidents')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(visibleIncidents()) })
      return
    }
    if (url.includes('/diagram')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          bpmnXml:
            '<?xml version="1.0"?><definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" id="Defs"><process id="P" isExecutable="true"><startEvent id="start"/></process></definitions>',
          activeIds: ['start'],
          completedIds: [],
          failedIds: incidents.length > 0 ? ['start'] : [],
        }),
      })
      return
    }
    if (url.includes('/history')) {
      // Miroir D-HISTORY-SIGNIFICANT (TODO §131) : l'API ne renvoie déjà plus les sequenceFlow / passerelles.
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(
          envelope([
            {
              activityId: 'startEvent',
              name: 'Start',
              assignee: null,
              startTime: '2026-11-12T10:00:00Z',
              endTime: '2026-11-12T10:00:01Z',
              durationMs: 1000,
            },
            {
              activityId: 'smokeTask',
              name: 'Smoke Task',
              assignee: 'user-1',
              startTime: '2026-11-12T10:00:01Z',
              endTime: null,
              durationMs: null,
            },
          ]),
        ),
      })
      return
    }
    if (url.includes('/reassign-group') && route.request().method() === 'POST') {
      // Miroir de D-GROUP-REASSIGN (TODO §114) : éditeurs du dossier seulement ; la tâche redevient libre pour ce groupe.
      if (!canEdit) {
        await route.fulfill({ status: 403, contentType: 'application/json', body: '{"status":403}' })
        return
      }
      const { groupCode } = route.request().postDataJSON() as { groupCode: string }
      tasks = tasks.map((task) => ({ ...task, assignee: null, candidateGroups: [groupCode] }))
      await route.fulfill({ status: 204, body: '' })
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
      mineTasks = mineTasks.map((task) => ({ ...task, assigneeId: task.assigneeId ?? 'user-1' }))
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
  await page.route(/\/api\/user-group-options(?:\?|$)/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        { code: 'COURRIER', label: 'Mail room' },
        { code: 'JURIDIQUE', label: 'Service juridique' },
      ]),
    })
  })
  // TODO §114 : groupes d'utilisateurs (administration du tenant), en mémoire.
  let userGroups: Array<{ code: string; label: string; idpGroup: string | null; members: Array<{ userId: string; displayName: string }> }> = [
    { code: 'COMPTABILITE', label: 'Comptabilité', idpGroup: '/services/compta', members: [] },
  ]
  const directory = [
    { userId: 'user-1', displayName: 'Ada', email: 'ada@example.com' },
    { userId: 'user-2', displayName: 'Grace Hopper', email: 'grace@example.com' },
  ]
  await page.route(/\/api\/user-groups(?:\/.*)?(?:\?|$)/, async (route) => {
    const method = route.request().method()
    const path = decodeURIComponent(new URL(route.request().url()).pathname)
    const json = (status: number, body: unknown) =>
      route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (path === '/api/user-groups/directory') {
      await json(200, directory)
      return
    }
    if (path === '/api/user-groups') {
      if (method === 'POST') {
        const posted = route.request().postDataJSON() as { code: string; label: string; idpGroup: string | null }
        const created = { code: posted.code, label: posted.label, idpGroup: posted.idpGroup ?? null, members: [] }
        userGroups = [...userGroups, created]
        await json(201, created)
        return
      }
      await json(200, userGroups)
      return
    }
    const member = path.match(/^\/api\/user-groups\/([^/]+)\/members\/(.+)$/)
    if (member) {
      const [, code, userId] = member
      const known = directory.find((user) => user.userId === userId)
      userGroups = userGroups.map((group) => {
        if (group.code !== code) return group
        const others = group.members.filter((m) => m.userId !== userId)
        return {
          ...group,
          members: method === 'PUT' && known ? [...others, { userId, displayName: known.displayName }] : others,
        }
      })
      await json(200, userGroups.find((group) => group.code === code))
      return
    }
    await json(404, { status: 404 })
  })
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

export async function openApp(page: Page) {
  await page.goto('./')
  await expect(page.getByRole('img', { name: 'Myrmacta' })).toBeVisible()
  await expect(page.getByText('Ada')).toBeVisible()
}

export async function createAndOpenDossier(page: Page) {
  await page.getByRole('link', { name: 'Nouveau dossier' }).click()
  await page.locator('#dossier-label').fill('Claim A')
  await page.getByRole('button', { name: 'Créer' }).click()
  await expect(page.getByRole('heading', { name: 'Claim A' })).toBeVisible()
}

// TODO §76 (D1) — champs structurés : montant, référence externe, groupe répétable.
export const RICH_TYPE = {
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

export const RICH_FIELDS = {
  montant: { amount: '12.50', currency: 'EUR' },
  fournisseur: { system: 'd365fo', id: 'V-0042', label: 'Contoso' },
  lignes: [{ poste: 'Toiture', prix: { amount: '1000.00', currency: 'EUR' } }],
}

export async function mockRichDossier(page: Page, canEdit = true) {
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

// ---- TODO §80 (D1, règles) : champs calculés, visibilité, obligation conditionnelle -------------------------------------

export const RULES_TYPE = {
  ...RICH_TYPE,
  fieldDefinitions: [
    { name: 'pays', label: 'Pays', type: 'STRING', required: false, options: [], group: null, sensitive: false },
    { name: 'ht', label: 'Montant HT', type: 'NUMBER', required: false, options: [], group: null, sensitive: false },
    { name: 'ttc', label: 'Montant TTC', type: 'NUMBER', required: false, options: [], group: null, sensitive: false, formula: 'dossier.fields.ht * 1.2' },
    { name: 'siret', label: 'SIRET', type: 'STRING', required: true, options: [], group: null, sensitive: false, visibleWhen: "dossier.fields.pays = 'FR'" },
    { name: 'justificatif', label: 'Justificatif', type: 'STRING', required: false, options: [], group: null, sensitive: false, requiredWhen: 'dossier.fields.ht > 1000' },
  ],
}

/** TODO §126 — type CLAIM avec un champ géré par le workflow et un champ saisissable. */
export const MANAGED_TYPE = {
  ...CLAIM_TYPE,
  fieldDefinitions: [
    { name: 'nom', label: 'Nom', type: 'STRING', required: true, options: [], group: null, sensitive: false },
    {
      name: 'identifiantIam',
      label: 'IAM',
      type: 'STRING',
      required: false,
      options: [],
      group: null,
      sensitive: false,
      managedByWorkflow: true,
    },
  ],
}

export async function mockManagedDossier(page: Page) {
  await page.route('**/api/dossier-types/CLAIM', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MANAGED_TYPE) }),
  )
  await page.route(`**/api/dossiers/${DOSSIER_ID}`, async (route) => {
    if (route.request().method() !== 'GET') return route.fallback()
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(
        dossierBody({ fields: { nom: 'Dupont', identifiantIam: 'iam-42' }, canEdit: true }),
      ),
    })
  })
}

export async function mockRulesDossier(page: Page) {
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

export const BPMN_FOR_OTHER_WORKFLOW = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="http://myrmacta/e2e">
  <process id="otherWorkflow" isExecutable="true">
    <startEvent id="start"/>
    <userTask id="wait" name="Wait"/>
    <endEvent id="end"/>
    <sequenceFlow id="flow1" sourceRef="start" targetRef="wait"/>
    <sequenceFlow id="flow2" sourceRef="wait" targetRef="end"/>
  </process>
</definitions>`

// The editor renders with a real bpmn-js Modeler (unlike the read-only
// preview in BpmnUploadPreview.vue), which requires
// bpmndi:BPMNDiagram/DI layout information to import successfully.
export const BPMN_IMPORTED = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
             xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
             xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
             targetNamespace="http://myrmacta/e2e">
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
export const BPMN_FOR_OTHER_WORKFLOW_EDITABLE = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
             xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
             xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
             targetNamespace="http://myrmacta/e2e">
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
export async function openBpmnEditor(page: Page) {
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

export const BPMN_WITH_SERVICE_TASK = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
             xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
             xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://myrmacta/e2e">
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

export async function openDmnEditor(page: Page) {
  await page.getByRole('link', { name: 'Tables de décision' }).click()
  await expect(page.getByRole('heading', { name: 'Éditeur de table de décision DMN' })).toBeVisible()
  // Mirrors openBpmnEditor's .djs-element wait: the dmn-js Modeler is
  // constructed via dynamic import, then importXML runs asynchronously --
  // wait for the decision table's own rendered content (its name field),
  // otherwise saveXML() races ahead of the import and returns blank content.
  await expect(page.getByRole('textbox', { name: 'Decision name' })).toBeVisible()
}

export const DMN_VERSIONS = [
  { decisionKey: 'Decision_1', version: 1, deploymentId: 'dep-1', deployedAt: '2026-09-01T00:00:00Z' },
  { decisionKey: 'Decision_1', version: 2, deploymentId: 'dep-2', deployedAt: '2026-09-02T00:00:00Z' },
]

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
export const BPMN_FOR_OTHER_WORKFLOW_WITH_FLOWABLE_NS = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
             xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
             xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
             targetNamespace="http://myrmacta/e2e">
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

// Must stay equal to SecurityHeadersCustomizer.CONTENT_SECURITY_POLICY (D-CSP-STRING A).
export const A4_CSP =
  "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; frame-src 'self' blob:; worker-src 'self' blob:; frame-ancestors 'none'; object-src 'none'; base-uri 'self'"

export async function injectDocumentCsp(page: Page) {
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

export async function attachCspProbe(page: Page) {
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

// ---------------------------------------------------------------------------------------------
// TODO §68 (portail-externe) — page publique /portal/ (build Vite séparé) et panneau instructeur.
// ---------------------------------------------------------------------------------------------

export const PORTAL_TOKEN = 'E2ePortalTokenAbcdefghijklmnopqrstuvwxyz0123'

export const PORTAL_TASK = {
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

// ---------------------------------------------------------------------------------------------------------------
// TODO §73 (palette-metier-bpmn) — briques métier dans l'éditeur BPMN (S-PW-1 à S-PW-5).
// Catalogue simulé : sous-ensemble des marqueurs du catalogue serveur (BuildingBlockCatalog).
// ---------------------------------------------------------------------------------------------------------------
export function blockParam(over: Record<string, unknown> & { name: string; label: string }) {
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

export const BLOCK_CATALOG = {
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
      id: 'search-dossiers',
      label: 'Rechercher des dossiers',
      description: 'Compte les dossiers en cours du même type qui partagent des valeurs.',
      category: 'DOSSIER',
      element: 'SERVICE_TASK',
      marker: { delegate: 'dossierSearchTask', serviceTaskType: null, formKeyPrefix: null, portalRecipient: false },
      parameters: [
        blockParam({
          name: 'searchCriteria',
          label: 'Critères',
          required: true,
          maxLength: 1000,
          multiline: true,
          help: 'Une ligne : champ = dossierField:<champ> ou champ = variable:<nom>.',
        }),
        blockParam({ name: 'searchCountResult', label: 'Variable du nombre trouvé', required: true, maxLength: 60 }),
        blockParam({ name: 'searchFirstReferenceResult', label: 'Variable de la référence', required: true, maxLength: 60 }),
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
      id: 'notify',
      label: 'Notifier',
      description: 'Envoie une notification par e-mail, webhook ou navigateur.',
      category: 'INTEGRATION',
      element: 'SERVICE_TASK',
      marker: { delegate: 'notificationDispatchTask', serviceTaskType: null, formKeyPrefix: null, portalRecipient: false },
      parameters: [
        blockParam({
          name: 'notificationChannels',
          label: 'Canaux',
          type: 'ENUM_MULTI',
          required: true,
          enumValues: ['EMAIL', 'WEBHOOK', 'BROWSER'],
        }),
        blockParam({ name: 'notificationTo', label: 'Destinataire e-mail', maxLength: 254 }),
        blockParam({ name: 'notificationAttachment', label: 'Pièce jointe (e-mail)', type: 'SOURCE_REF' }),
        blockParam({ name: 'notificationAttachmentName', label: 'Nom du fichier joint', maxLength: 100 }),
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

export function editableBpmn(processKey: string, tasks: string, taskIds: string[]): string {
  const shapes = taskIds
    .map(
      (id, i) => `<bpmndi:BPMNShape id="${id}_di" bpmnElement="${id}"><dc:Bounds x="${250 + i * 140}" y="80" width="100" height="80"/></bpmndi:BPMNShape>`,
    )
    .join('')
  return `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn"
             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
             targetNamespace="http://myrmacta/e2e">
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

export async function mockBlockApis(page: Page, opts: { catalog?: boolean; templates?: Array<{ value: string; label: string }> } = {}) {
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

export async function importBpmn(page: Page, xml: string, anchorId: string) {
  await page.locator('input[type="file"]').setInputFiles({
    name: 'blocks.bpmn20.xml',
    mimeType: 'application/xml',
    buffer: Buffer.from(xml),
  })
  await expect(page.locator(`[data-element-id="${anchorId}"]`)).toBeVisible()
}
