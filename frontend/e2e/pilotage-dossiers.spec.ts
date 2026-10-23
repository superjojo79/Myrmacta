// E2E « pilotage » — Dossiers : création, statut, démarrage de workflow, détail (relations, éléments, champs) (TODO §95 : découpage de pilotage.spec.ts par écran).
import { expect, test } from '@playwright/test'
import {
  RICH_TYPE,
  DOSSIER_ID,
  dossierBody,
  mockSession,
  openApp,
  createAndOpenDossier,
  mockRichDossier,
  mockRulesDossier,
} from './support/pilotage'

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
