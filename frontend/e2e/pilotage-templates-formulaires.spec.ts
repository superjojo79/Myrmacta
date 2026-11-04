// E2E « pilotage » — Modèles de documents, formulaires de tâche, questions utilisateur, concepteur de formulaires (TODO §95 : découpage de pilotage.spec.ts par écran).
import { expect, test } from '@playwright/test'
import {
  DOSSIER_ID,
  LINK_ID,
  envelope,
  CLAIM_TYPE,
  dossierBody,
  mockSession,
  openApp,
  openBpmnEditor,
  BPMN_FOR_OTHER_WORKFLOW_WITH_FLOWABLE_NS,
} from './support/pilotage'

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

test('E2E-UNIQUE-4 a dossier-fields task shows UNIQUE under the field and not the raw server message', async ({ page }) => {
  await mockSession(page)
  await page.route('**/api/workflow-tasks/mine*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(
        envelope([
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
        ]),
      ),
    })
  })
  await page.route(`**/api/dossiers/${DOSSIER_ID}/fields`, async (route) => {
    if (route.request().method() !== 'PATCH') {
      await route.fallback()
      return
    }
    await route.fulfill({
      status: 400,
      contentType: 'application/json',
      body: JSON.stringify({
        title: 'Dossier field validation failed',
        status: 400,
        detail: 'priority UNIQUE Value already used by dossier DOS-9 SECRET-PLAQUE',
        errors: [{ field: 'priority', code: 'UNIQUE', message: 'Value already used by dossier DOS-9 SECRET-PLAQUE', reference: 'DOS-9' }],
      }),
    })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Tâches' }).click()
  const priority = page.locator('.dossier-fields-task-form input')
  await expect(priority).toHaveValue('NORMAL')
  await priority.fill('HIGH')
  await page.locator('.dossier-fields-task-form button').click()
  const alert = page.getByTestId('field-error')
  await expect(alert).toContainText('Déjà utilisé par le dossier DOS-9')
  await expect(page.getByText('SECRET-PLAQUE')).toHaveCount(0)
  await expect(page.getByText('Value already used')).toHaveCount(0)
  await expect(page.getByText('Valider')).toBeVisible()
})

test('E2E-UNIQUE-6 two dossier-fields tasks keep their own off-screen banners', async ({ page }) => {
  await mockSession(page)
  await page.route('**/api/workflow-tasks/mine*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(
        envelope([
          {
            taskId: 'task-1',
            name: 'Première',
            assigneeId: 'user-1',
            createdAt: '2026-08-12T00:00:00Z',
            processInstanceId: 'pi-1',
            dossierId: DOSSIER_ID,
            dossierReference: 'DOS-1',
            linkId: LINK_ID,
            formKey: 'dossier-fields',
          },
          {
            taskId: 'task-2',
            name: 'Seconde',
            assigneeId: 'user-1',
            createdAt: '2026-08-12T00:00:00Z',
            processInstanceId: 'pi-2',
            dossierId: DOSSIER_ID,
            dossierReference: 'DOS-1',
            linkId: LINK_ID,
            formKey: 'dossier-fields',
          },
        ]),
      ),
    })
  })
  let fieldsPatch = 0
  await page.route(`**/api/dossiers/${DOSSIER_ID}/fields`, async (route) => {
    if (route.request().method() !== 'PATCH') {
      await route.fallback()
      return
    }
    fieldsPatch += 1
    const offScreen = fieldsPatch === 1
    await route.fulfill({
      status: 400,
      contentType: 'application/json',
      body: JSON.stringify(
        offScreen
          ? {
              detail: 'absent UNIQUE Value already used by dossier DOS-9',
              errors: [{ field: 'absent', code: 'UNIQUE', message: 'Value already used by dossier DOS-9', reference: 'DOS-9' }],
            }
          : {
              detail: 'priority UNIQUE Value already used by dossier DOS-2',
              errors: [{ field: 'priority', code: 'UNIQUE', message: 'Value already used by dossier DOS-2', reference: 'DOS-2' }],
            },
      ),
    })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Tâches' }).click()
  const forms = page.locator('.dossier-fields-task-form')
  await expect(forms).toHaveCount(2)
  await forms.nth(0).locator('input').fill('HIGH')
  await forms.nth(0).locator('button').click()
  await forms.nth(1).locator('input').fill('LOW')
  await forms.nth(1).locator('button').click()
  const first = page.locator('.tasks-view__list li', { hasText: 'Première' })
  const second = page.locator('.tasks-view__list li', { hasText: 'Seconde' })
  await expect(first.getByTestId('task-form-banner')).toContainText('DOS-9')
  await expect(second.getByTestId('field-error')).toContainText('DOS-2')
  await expect(second).not.toContainText('DOS-9')
})

test('E2E-UNIQUE-8 a successful patch that clears a hidden field drops its banner', async ({ page }) => {
  await mockSession(page)
  await page.route('**/api/workflow-tasks/mine*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(
        envelope([
          {
            taskId: 'task-1',
            name: 'Valider',
            assigneeId: 'user-1',
            createdAt: '2026-08-12T00:00:00Z',
            processInstanceId: 'pi-1',
            dossierId: DOSSIER_ID,
            dossierReference: 'DOS-1',
            linkId: LINK_ID,
            formKey: 'dossier-fields',
          },
        ]),
      ),
    })
  })
  await page.route('**/api/dossier-types/CLAIM', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        ...CLAIM_TYPE,
        fieldDefinitions: [
          ...CLAIM_TYPE.fieldDefinitions,
          {
            name: 'siret',
            label: 'SIRET',
            type: 'STRING',
            required: false,
            options: [],
            group: null,
            sensitive: false,
            visibleWhen: "dossier.fields.priority = 'FR'",
          },
        ],
      }),
    })
  })
  await page.route(`**/api/dossiers/${DOSSIER_ID}`, async (route) => {
    if (route.request().method() !== 'GET') {
      await route.fallback()
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(dossierBody({ fields: { priority: 'NORMAL', siret: '12345678901234' }, version: 3 })),
    })
  })
  await page.route('**/api/dossier-types/CLAIM/evaluate-rules', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ computed: {}, hidden: ['siret'], requiredByRule: [], errors: [] }),
    })
  })
  let fieldsPatch = 0
  await page.route(`**/api/dossiers/${DOSSIER_ID}/fields`, async (route) => {
    if (route.request().method() !== 'PATCH') {
      await route.fallback()
      return
    }
    fieldsPatch += 1
    const posted = route.request().postDataJSON() as { fields: Record<string, unknown> }
    expect(posted.fields.priority).toBe('LOW')
    expect(posted.fields.siret).toBeNull()
    if (fieldsPatch === 1) {
      await route.fulfill({
        status: 400,
        contentType: 'application/json',
        body: JSON.stringify({
          detail: 'siret UNIQUE Value already used by dossier DOS-9',
          errors: [{ field: 'siret', code: 'UNIQUE', message: 'Value already used by dossier DOS-9', reference: 'DOS-9' }],
        }),
      })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(dossierBody({ fields: { priority: 'LOW' }, version: 4 })),
    })
  })
  await page.route('**/api/workflow-tasks/*/complete', async (route) => {
    await route.fulfill({
      status: 500,
      contentType: 'application/json',
      body: JSON.stringify({ detail: 'Le moteur a refusé la tâche' }),
    })
  })
  const rulesReady = page.waitForResponse((response) => response.url().includes('/evaluate-rules') && response.ok())
  await openApp(page)
  await page.getByRole('link', { name: 'Tâches' }).click()
  await rulesReady
  const form = page.locator('.dossier-fields-task-form')
  await expect(form.getByLabel('SIRET')).toHaveCount(0)
  await form.locator('input').fill('LOW')
  await form.locator('button').click()
  await expect(form.getByTestId('task-form-banner')).toContainText('DOS-9')
  await form.locator('button').click()
  await expect(page.locator('.tasks-view > .error')).toContainText('Le moteur a refusé la tâche')
  await expect(form.getByTestId('task-form-banner')).toHaveCount(0)
  await expect(form).not.toContainText('DOS-9')
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
  // names on the way in. Polled: the click only starts the request (TODO §95).
  await expect.poll(() => completedWith).toEqual({
    variables: { documentType: 'invoice', documentVariant: 'premium', documentLocale: 'fr' },
  })
})

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
  // Le PUT part après le clic : attendre qu'il soit intercepté (même motif que E2E-PORTAL-4 dans
  // pilotage-securite-portail.spec.ts) au lieu de lire savedXml tout de suite.
  await expect.poll(() => savedXml).toContain('flowable:formKey="document-generation"')
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
  await expect
    .poll(() => (updatedPayload as { documentTemplateKeys?: string[] } | null)?.documentTemplateKeys)
    .toEqual(['invoice'])
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
  // Polled: the click only starts the request, the route handler records it asynchronously (TODO §95).
  await expect.poll(() => completedWith).toEqual({ variables: { decision: 'approve' } })
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
