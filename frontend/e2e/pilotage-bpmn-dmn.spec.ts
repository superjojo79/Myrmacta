// E2E « pilotage » — Éditeurs BPMN et DMN, palette métier, parcours, brouillons, promotion, assistant (TODO §95 : découpage de pilotage.spec.ts par écran).
import { expect, test } from '@playwright/test'
import {
  envelope,
  mockSession,
  openApp,
  BPMN_FOR_OTHER_WORKFLOW,
  BPMN_IMPORTED,
  BPMN_FOR_OTHER_WORKFLOW_EDITABLE,
  openBpmnEditor,
  BPMN_WITH_SERVICE_TASK,
  openDmnEditor,
  DMN_VERSIONS,
  editableBpmn,
  mockBlockApis,
  importBpmn,
} from './support/pilotage'

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
