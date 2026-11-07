// E2E « pilotage » — Administration des types de dossier : SLA, pagination, rétention, schéma (TODO §95 : découpage de pilotage.spec.ts par écran).
import { expect, test } from '@playwright/test'
import {
  envelope,
  CLAIM_TYPE,
  mockSession,
  openApp,
} from './support/pilotage'

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

test('E2E-CAL-1 admin adds a day and a period to the tenant calendar and saves them', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'Types' }).click()
  await expect(page.getByRole('heading', { name: 'Calendrier' })).toBeVisible()
  await page.getByTestId('calendar-zone').fill('Europe/Brussels')

  await page.getByTestId('entry-start').fill('2026-12-24')
  await page.getByTestId('entry-end').fill('2026-12-31')
  await page.getByTestId('entry-label').fill('Congé collectif')
  await page.getByTestId('entry-add').click()
  await page.getByTestId('entry-start').fill('2026-11-14')
  await page.getByTestId('entry-nature').selectOption('OPEN')
  await page.getByTestId('entry-label').fill('Samedi travaillé')
  await page.getByTestId('entry-add').click()
  await expect(page.getByTestId('entry-row-manual')).toHaveCount(2)
  await expect(page.getByTestId('calendar-entries')).toContainText('2026-12-24 → 2026-12-31')

  const put = page.waitForRequest((request) => request.url().endsWith('/api/tenant/calendar') && request.method() === 'PUT')
  await page.getByTestId('calendar-save').click()
  expect((await put).postDataJSON()).toEqual({
    zoneId: 'Europe/Brussels',
    weekend: 'SATURDAY_SUNDAY',
    entries: [
      { start: '2026-12-24', end: '2026-12-31', nature: 'CLOSED', label: 'Congé collectif' },
      { start: '2026-11-14', end: '2026-11-14', nature: 'OPEN', label: 'Samedi travaillé' },
    ],
  })
  await expect(page.getByTestId('calendar-saved')).toBeVisible()
})

test('E2E-CAL-2 admin loads the Belgian holidays and a forced working day overrides one of them', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'Types' }).click()
  await expect(page.getByTestId('calendar-sources-empty')).toBeVisible()

  await page.getByTestId('calendar-belgian').click()
  await expect(page.getByTestId('calendar-source')).toContainText('Fériés fédéraux belges')
  await expect(page.getByTestId('entry-row-source')).toHaveCount(2)
  await expect(page.getByTestId('entry-overridden')).toHaveCount(0)

  await page.getByTestId('entry-start').fill('2026-12-25')
  await page.getByTestId('entry-nature').selectOption('OPEN')
  await page.getByTestId('entry-label').fill('On travaille')
  await page.getByTestId('entry-add').click()
  await expect(page.getByTestId('entry-overridden')).toHaveCount(1)
  await expect(page.getByTestId('entry-row-source').first()).toContainText('Surchargé par un jour ouvré forcé')
})

test('E2E-CAL-3 the entry form refuses an end before the start and sends nothing', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'Types' }).click()

  await page.getByTestId('entry-start').fill('2026-12-31')
  await page.getByTestId('entry-end').fill('2026-12-24')
  await page.getByTestId('entry-add').click()

  await expect(page.getByTestId('entry-error')).toContainText('précède')
  await expect(page.getByTestId('entry-row-manual')).toHaveCount(0)
})

const ICS = Buffer.from('BEGIN:VCALENDAR\r\nVERSION:2.0\r\nEND:VCALENDAR\r\n')

async function openCalendar(page: import('@playwright/test').Page) {
  await mockSession(page)
  await openApp(page)
  await page.getByRole('link', { name: 'Types' }).click()
  await expect(page.getByRole('heading', { name: 'Calendrier' })).toBeVisible()
}

async function importIcs(page: import('@playwright/test').Page, name: string) {
  await page.getByTestId('source-name').fill(name)
  await page.getByTestId('source-file').setInputFiles({ name: 'ecoles.ics', mimeType: 'text/calendar', buffer: ICS })
  await page.getByTestId('source-preview').click()
}

test('E2E-CAL-4 admin previews an ICS file, then confirms it with the preview sha', async ({ page }) => {
  await openCalendar(page)

  await importIcs(page, 'Écoles')
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()
  await expect(dialog).toContainText('2026-12-28 → 2026-12-31')
  await expect(dialog).toContainText('1 événement(s) horaire(s)')
  await expect(page.getByTestId('calendar-source')).toHaveCount(0)

  const created = page.waitForRequest((request) => request.url().endsWith('/api/tenant/calendar/sources') && request.method() === 'POST')
  await page.getByTestId('import-confirm').click()
  const body = (await created).postData() ?? ''
  expect(body).toContain('name="expectedSha256"')
  expect(body).toContain('sha-new')

  await expect(dialog).toBeHidden()
  await expect(page.getByTestId('calendar-source')).toContainText('Écoles')
  await expect(page.getByTestId('entry-row-source')).toContainText('Congé')
})

test('E2E-CAL-5 Escape closes the preview and imports nothing', async ({ page }) => {
  await openCalendar(page)
  await importIcs(page, 'Écoles')
  await expect(page.getByRole('dialog')).toBeVisible()

  await page.keyboard.press('Escape')

  await expect(page.getByRole('dialog')).toBeHidden()
  await expect(page.getByTestId('calendar-sources-empty')).toBeVisible()
})

test('E2E-CAL-6 admin reloads a file source and the manual entries stay', async ({ page }) => {
  await openCalendar(page)
  await page.getByTestId('entry-start').fill('2026-12-24')
  await page.getByTestId('entry-label').fill('Pont')
  await page.getByTestId('entry-add').click()
  await page.getByTestId('calendar-save').click()
  await expect(page.getByTestId('calendar-saved')).toBeVisible()
  await importIcs(page, 'Écoles')
  await page.getByTestId('import-confirm').click()
  await expect(page.getByTestId('entry-row-source')).toContainText('Congé')

  await page.getByTestId('source-reload').click()
  await page.getByTestId('source-reload-file').setInputFiles({ name: 'ecoles-2027.ics', mimeType: 'text/calendar', buffer: ICS })
  const dialog = page.getByRole('dialog')
  await expect(dialog).toContainText('2027-01-04')
  await expect(page.getByTestId('import-removed')).toContainText('2026-12-28 → 2026-12-31')
  await page.getByTestId('import-confirm').click()

  await expect(dialog).toBeHidden()
  await expect(page.getByTestId('entry-row-source')).toContainText('Rentrée')
  await expect(page.getByTestId('entry-row-source')).not.toContainText('Congé')
  await expect(page.getByTestId('entry-row-manual')).toContainText('Pont')
})

test('E2E-CAL-7 a reload whose content changed since the preview asks to preview again', async ({ page }) => {
  await openCalendar(page)
  await importIcs(page, 'Écoles')
  await page.getByTestId('import-confirm').click()
  await expect(page.getByTestId('calendar-source')).toBeVisible()

  await page.getByTestId('source-reload').click()
  await page.getByTestId('source-reload-file').setInputFiles({ name: 'stale.ics', mimeType: 'text/calendar', buffer: ICS })
  await page.getByTestId('import-confirm').click()

  await expect(page.getByTestId('import-error')).toContainText("relancez l'aperçu")
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.getByTestId('import-cancel').click()
  await expect(page.getByTestId('entry-row-source')).toContainText('Congé')
})

test('E2E-CAL-8 admin deletes a source after confirming', async ({ page }) => {
  await openCalendar(page)
  await importIcs(page, 'Écoles')
  await page.getByTestId('import-confirm').click()
  await expect(page.getByTestId('calendar-source')).toHaveCount(1)

  await page.getByTestId('source-delete').click()
  await expect(page.getByTestId('calendar-source')).toContainText('Supprimer la source « Écoles »')
  await page.getByTestId('source-delete-cancel').click()
  await expect(page.getByTestId('calendar-source')).toHaveCount(1)

  await page.getByTestId('source-delete').click()
  await page.getByTestId('source-delete-confirm').click()

  await expect(page.getByTestId('calendar-sources-empty')).toBeVisible()
  await expect(page.getByTestId('entry-row-source')).toHaveCount(0)
})

const SECRET_URL = 'https://calendar.example.org/ical/TOPSECRET-TOKEN/basic.ics'

async function importUrl(page: import('@playwright/test').Page, name: string, url: string) {
  await page.getByTestId('source-mode').selectOption('url')
  await page.getByTestId('source-name').fill(name)
  await page.getByTestId('source-url-input').fill(url)
  await page.getByTestId('source-preview').click()
}

test('E2E-CAL-9 admin adds an URL source: the secret URL goes out once and is never shown again', async ({ page }) => {
  await openCalendar(page)

  await importUrl(page, 'Agenda', SECRET_URL)
  const dialog = page.getByRole('dialog')
  await expect(dialog).toContainText('2026-12-24 → 2026-12-26')
  await expect(page.locator('body')).not.toContainText('TOPSECRET')

  const created = page.waitForRequest((request) => request.url().endsWith('/api/tenant/calendar/sources') && request.method() === 'POST')
  await page.getByTestId('import-confirm').click()
  expect((await created).postDataJSON()).toEqual({ name: 'Agenda', url: SECRET_URL, expectedSha256: 'sha-url' })

  await expect(dialog).toBeHidden()
  await expect(page.getByTestId('source-url')).toHaveText('https://calendar.example.org/…')
  await expect(page.getByTestId('entry-row-source')).toContainText('Agenda')
  await expect(page.getByTestId('source-url-input')).toHaveValue('')
  await expect(page.locator('body')).not.toContainText('TOPSECRET')
  await expect(page.locator('body')).not.toContainText('basic.ics')
})

test('E2E-CAL-10 admin reloads an URL source from the stored URL, then changes the URL', async ({ page }) => {
  await openCalendar(page)
  await importUrl(page, 'Agenda', SECRET_URL)
  await page.getByTestId('import-confirm').click()
  await expect(page.getByTestId('calendar-source')).toBeVisible()

  const reload = page.waitForRequest((request) => /\/sources\/src-url\/reload$/.test(request.url()))
  await page.getByTestId('source-reload').click()
  await expect(page.getByRole('dialog')).toContainText('2027-01-11')
  await page.getByTestId('import-confirm').click()
  expect((await reload).postDataJSON()).toEqual({ url: null, expectedSha256: 'sha-url-2' })
  await expect(page.getByTestId('entry-row-source')).toContainText('Reprise')

  await page.getByTestId('source-url-change').click()
  await page.getByTestId('source-url-edit').fill('https://other.example.net:8443/feed/NEWSECRET.ics')
  await page.getByTestId('source-url-edit-preview').click()
  await page.getByTestId('import-confirm').click()
  await expect(page.getByTestId('source-url')).toHaveText('https://other.example.net:8443/…')
  await expect(page.locator('body')).not.toContainText('NEWSECRET')
})

test('E2E-CAL-11 an URL reload whose content changed asks to preview again', async ({ page }) => {
  await openCalendar(page)
  await importUrl(page, 'Agenda', SECRET_URL)
  await page.getByTestId('import-confirm').click()
  await expect(page.getByTestId('calendar-source')).toBeVisible()

  await page.getByTestId('source-url-change').click()
  await page.getByTestId('source-url-edit').fill('https://calendar.example.org/stale.ics')
  await page.getByTestId('source-url-edit-preview').click()
  await page.getByTestId('import-confirm').click()

  await expect(page.getByTestId('import-error')).toContainText("relancez l'aperçu")
  await page.getByTestId('import-cancel').click()
  await expect(page.getByTestId('source-url')).toHaveText('https://calendar.example.org/…')
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

test('E2E-UNIQUE-1 admin marks a field unique and sees why the checkbox is unavailable', async ({ page }) => {
  let createdBody: Record<string, unknown> | null = null
  await mockSession(page)
  await page.route(/\/api\/dossier-types(?:\?|$)/, async (route) => {
    if (route.request().method() === 'POST') {
      createdBody = route.request().postDataJSON() as Record<string, unknown>
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 't-unique',
          tenantId: 'tenant_dev',
          code: createdBody.code,
          label: createdBody.label,
          workflowKey: createdBody.workflowKey,
          active: true,
          allowUnknownFields: false,
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
  await createForm.getByPlaceholder('code').fill('RIVERAIN')
  await createForm.getByPlaceholder('libellé').fill('Riverain')
  await createForm.getByPlaceholder('workflowKey').fill('wf-riverain')
  await createForm.getByTestId('field-def-add').click()
  await createForm.getByTestId('field-def-name').fill('plaque')
  await createForm.getByTestId('field-def-label').fill('Plaque')
  const unique = createForm.getByRole('checkbox', { name: 'Valeur unique' })
  await expect(unique).toBeEnabled()
  await createForm.getByTestId('field-def-type').selectOption('BOOLEAN')
  await expect(unique).toBeDisabled()
  await expect(createForm.getByText('Réservé aux champs texte, nombre ou date')).toBeVisible()
  await createForm.getByTestId('field-def-type').selectOption('STRING')
  await unique.check()
  await createForm.getByRole('button', { name: 'Créer' }).click()
  await expect
    .poll(() => (createdBody?.fieldDefinitions as Array<Record<string, unknown>> | undefined)?.[0]?.unique)
    .toBe(true)
})

test('E2E-UNIQUE-2 a schema update refused for duplicates shows the values and the references', async ({ page }) => {
  await mockSession(page)
  await page.route('**/api/dossier-types/CLAIM', async (route) => {
    if (route.request().method() === 'PATCH') {
      await route.fulfill({
        status: 409,
        contentType: 'application/json',
        body: JSON.stringify({
          title: 'Dossier type schema incompatible',
          status: 409,
          detail: 'Cette modification rend 0 dossier(s) existant(s) incompatible(s) avec le nouveau schéma ; 1 valeur(s) d\'un champ unique sont partagées par des dossiers en cours',
          incompatibleCount: 0,
          sampleReferences: [],
          duplicates: [{ field: 'plaque', value: '1ABC123', references: ['DOS-1', 'DOS-2'] }],
        }),
      })
      return
    }
    await route.fallback()
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Types' }).click()
  await page.getByTestId('type-edit-fields').click()
  await page.getByTestId('type-edit-fields-save').click()
  const panel = page.getByTestId('type-edit-fields-panel')
  await expect(panel).toContainText('incompatible')
  await expect(panel).toContainText('plaque = 1ABC123 : DOS-1, DOS-2')
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

test('E2E-MAILROOM-REPLY-1 admin declares, then removes, the reply workflow of a type', async ({ page }) => {
  const calls: Array<{ method: string; body: unknown }> = []
  await mockSession(page)
  await page.route('**/api/dossier-types/CLAIM/reply-workflow', async (route) => {
    const method = route.request().method()
    if (method === 'GET') {
      await route.fulfill({ status: 204, body: '' })
      return
    }
    calls.push({ method, body: method === 'PUT' ? route.request().postDataJSON() : null })
    if (method === 'PUT') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ dossierTypeCode: 'CLAIM', ...(route.request().postDataJSON() as object) }),
      })
      return
    }
    await route.fulfill({ status: 204, body: '' })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Types' }).click()
  await page.getByTestId('type-edit-fields').click()
  const panel = page.getByTestId('reply-workflow-panel')
  await expect(panel.getByTestId('reply-workflow-mode')).toHaveValue('')
  await panel.getByTestId('reply-workflow-mode').selectOption('SUR_DOSSIER')
  await expect(panel.getByTestId('reply-workflow-child-type')).toHaveCount(0)
  await panel.getByTestId('reply-workflow-key').selectOption('otherWorkflow')
  await panel.getByTestId('reply-workflow-save').click()
  await expect(panel.getByTestId('reply-workflow-saved')).toBeVisible()
  await panel.getByTestId('reply-workflow-mode').selectOption('')
  await panel.getByTestId('reply-workflow-save').click()
  await expect
    .poll(() => calls)
    .toEqual([
      { method: 'PUT', body: { mode: 'SUR_DOSSIER', workflowKey: 'otherWorkflow', childTypeCode: null } },
      { method: 'DELETE', body: null },
    ])
})

test('E2E-MAILROOM-REPLY-2 a refused reply workflow shows the server message', async ({ page }) => {
  await mockSession(page)
  await page.route('**/api/dossier-types/CLAIM/reply-workflow', async (route) => {
    if (route.request().method() === 'PUT') {
      await route.fulfill({
        status: 400,
        contentType: 'application/json',
        body: JSON.stringify({ title: 'Bad Request', status: 400, detail: 'Unknown child dossier type: X' }),
      })
      return
    }
    await route.fulfill({ status: 204, body: '' })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Types' }).click()
  await page.getByTestId('type-edit-fields').click()
  const panel = page.getByTestId('reply-workflow-panel')
  await panel.getByTestId('reply-workflow-mode').selectOption('DOSSIER_ENFANT')
  await panel.getByTestId('reply-workflow-key').selectOption('otherWorkflow')
  await panel.getByTestId('reply-workflow-save').click()
  await expect(panel.getByTestId('reply-workflow-error')).toHaveText('Unknown child dossier type: X')
})

test('E2E-DEADLINE-1 admin designates a date field as the deadline, with reminder and escalation group', async ({ page }) => {
  let saved: unknown = null
  await mockSession(page)
  await page.route('**/api/dossier-types/CLAIM/deadline', async (route) => {
    if (route.request().method() === 'PUT') {
      saved = route.request().postDataJSON()
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ dossierTypeCode: 'CLAIM', ...(saved as object) }),
      })
      return
    }
    await route.fulfill({ status: 204, body: '' })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Types' }).click()
  await page.getByTestId('type-edit-fields').click()
  const editor = page.getByTestId('type-edit-fields-panel')
  const deadline = page.getByTestId('deadline-panel')
  await expect(deadline.getByTestId('deadline-no-date-field')).toBeVisible()
  await editor.getByTestId('field-def-add').click()
  await editor.getByTestId('field-def-name').nth(1).fill('echeance')
  await editor.getByTestId('field-def-label').nth(1).fill('Échéance')
  await editor.getByTestId('field-def-type').nth(1).selectOption('DATE')
  await deadline.getByTestId('deadline-field').selectOption('echeance')
  await deadline.getByTestId('deadline-reminder-days').fill('5')
  const group = deadline.getByTestId('deadline-escalation-group')
  const firstGroup = await group.locator('option').nth(1).getAttribute('value')
  await group.selectOption(firstGroup ?? '')
  await deadline.getByTestId('deadline-save').click()
  await expect(deadline.getByTestId('deadline-saved')).toBeVisible()
  await expect.poll(() => saved).toEqual({
    fieldName: 'echeance',
    reminderDays: 5,
    reminderDayCount: 'CALENDAR',
    escalationGroup: firstGroup,
  })
})
