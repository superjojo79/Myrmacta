// E2E « pilotage » — Supervision, rapports, SLA, observabilité, purge (TODO §95 : découpage de pilotage.spec.ts par écran).
import { expect, test } from '@playwright/test'
import {
  CLAIM_TYPE,
  envelope,
  mockSession,
  openApp,
  createAndOpenDossier,
} from './support/pilotage'

test('E2E-9 supervision has no fake KPI', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'Supervision' }).click()
  await expect(page.getByRole('heading', { name: 'Supervision' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Filtrer' })).toBeVisible()
  await expect(page.getByText('DOS-1')).toBeVisible()
  await expect(page.getByText('KPI')).toHaveCount(0)
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
