// E2E « pilotage » — En-têtes de sécurité, portail externe, signature, langue (TODO §95 : découpage de pilotage.spec.ts par écran).
import { expect, test } from '@playwright/test'
import {
  DOSSIER_ID,
  mockSession,
  openApp,
  createAndOpenDossier,
  openBpmnEditor,
  openDmnEditor,
  BPMN_FOR_OTHER_WORKFLOW_WITH_FLOWABLE_NS,
  injectDocumentCsp,
  attachCspProbe,
  PORTAL_TOKEN,
  PORTAL_TASK,
} from './support/pilotage'

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

// TODO §103 (issue #210) — authentification forte d'un tiers : vue réduite, départ vers le fournisseur, retour avec la
// preuve d'identité (D-BEAUTH-PROOF), soumission avec la preuve à la place du jeton, refus neutre.
const IDENTITY_PROOF = 'p'.repeat(43)

test('E2E-PORTAL-6 strong authentication: reduced view, provider round trip, submission with the identity proof', async ({ page }) => {
  const seen: { path: string; token?: string; proof?: string }[] = []
  let started: unknown = null
  let submitted: unknown = null
  await page.route('**/api/portal/identity/start', async (route) => {
    const headers = route.request().headers()
    seen.push({ path: 'start', token: headers['x-portal-token'], proof: headers['x-portal-identity'] })
    started = route.request().postDataJSON()
    // Le fournisseur simulé renvoie aussitôt vers l'instance, comme le ferait le callback serveur.
    const back = new URL('?retour=1', page.url())
    back.hash = `identity:${IDENTITY_PROOF}`
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ authorizationUrl: back.toString() }) })
  })
  await page.route('**/api/portal/task/submit', async (route) => {
    const headers = route.request().headers()
    seen.push({ path: 'submit', token: headers['x-portal-token'], proof: headers['x-portal-identity'] })
    submitted = route.request().postDataJSON()
    await route.fulfill({ status: 204, body: '' })
  })
  await page.route('**/api/portal/task', async (route) => {
    const headers = route.request().headers()
    seen.push({ path: 'task', token: headers['x-portal-token'], proof: headers['x-portal-identity'] })
    const verified = headers['x-portal-identity'] === IDENTITY_PROOF
    const body = verified
      ? { ...PORTAL_TASK, identity: { verified: true, name: 'Alice Martin', providers: [] } }
      : {
          ...PORTAL_TASK,
          instructions: null,
          form: { label: null, fields: [] },
          attachments: [],
          identity: { verified: false, name: null, providers: [{ id: 'fas', label: 'eID, itsme et autres (FAS)' }, { id: 'itsme', label: 'itsme' }] },
        }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
  })

  await page.goto(`./portal/#${PORTAL_TOKEN}`)

  const screen = page.getByTestId('portal-identity-required')
  await expect(screen.getByRole('heading', { name: 'Avis du tiers' })).toBeVisible()
  await expect(page.getByText('Dossier DOS-2026-0042')).toBeVisible()
  await expect(page.locator('#portal-file')).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Envoyer ma réponse' })).toHaveCount(0)

  await page.getByRole('button', { name: 'itsme', exact: true }).click()

  await expect(page.getByTestId('portal-identity-verified')).toContainText('Alice Martin')
  // La preuve a quitté la barre d'adresse, comme le jeton (D-PORTAL-CSRF-SESSION).
  await expect(page).not.toHaveURL(new RegExp(IDENTITY_PROOF))
  await page.locator('#field-disponible').check()
  await page.getByRole('button', { name: 'Envoyer ma réponse' }).click()
  await expect(page.getByTestId('portal-done')).toBeVisible()

  expect(started).toEqual({ provider: 'itsme' })
  expect(submitted).toEqual({ answers: { disponible: true } })
  expect(seen.map((r) => r.path)).toEqual(['task', 'start', 'task', 'submit'])
  expect(seen[0]).toMatchObject({ token: PORTAL_TOKEN, proof: undefined })
  expect(seen[1]).toMatchObject({ token: PORTAL_TOKEN, proof: undefined })
  expect(seen[2]).toMatchObject({ token: undefined, proof: IDENTITY_PROOF })
  expect(seen[3]).toMatchObject({ token: undefined, proof: IDENTITY_PROOF })
})

test('E2E-PORTAL-7 a refused identity shows the neutral screen without any API call', async ({ page }) => {
  const portalCalls: string[] = []
  page.on('request', (request) => {
    if (request.url().includes('/api/')) portalCalls.push(request.url())
  })

  await page.goto('./portal/#identity-refused')

  await expect(page.getByTestId('portal-identity-refused')).toContainText('Identification non reconnue')
  await expect(page).toHaveURL(/\/portal\/$/)
  expect(portalCalls).toEqual([])
})
