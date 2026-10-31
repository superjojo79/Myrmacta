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
  await expect.poll(() => saved).toEqual({ fieldName: 'echeance', reminderDays: 5, escalationGroup: firstGroup })
})
