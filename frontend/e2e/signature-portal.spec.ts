import { expect, test, type Page } from '@playwright/test'

// TODO §72 — écran signataire externe du portail (build `dist/portal`, servi par `vite preview`).
// L'API est simulée : la logique serveur est couverte par les tests d'intégration Java.
const TOKEN = 'k'.repeat(43)
const LINK = `/portal/index.html#signature:${TOKEN}`

const CONTEXT = {
  dossierReference: 'DOS-2026-0042',
  documentLabel: 'Contrat',
  signerName: 'Alice',
  required: true,
  deadlineAt: '2026-10-30T10:00:00Z',
  simulated: true,
  hosted: false,
  signingUrl: null as string | null,
  signedUpload: false,
}

async function mockSignatureApi(
  page: Page,
  opts: {
    denied?: boolean
    context?: typeof CONTEXT
    /** Issue #10 — réponse au téléversement du PDF signé (200 SIGNED par défaut). */
    upload?: { status: number; body: unknown; contentType?: string }
  } = {},
) {
  const calls: string[] = []
  await page.route('**/api/portal/signature**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (opts.denied) {
      await route.fulfill({ status: 404, contentType: 'application/json', body: '{"detail":"secret internal detail"}' })
      return
    }
    expect(request.headers()['x-portal-token']).toBe(TOKEN)
    if (path.endsWith('/document')) {
      await route.fulfill({ status: 200, contentType: 'application/pdf', body: Buffer.from('%PDF-1.4 stub') })
    } else if (path.endsWith('/sign')) {
      calls.push('sign')
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{"status":"SIGNED"}' })
    } else if (path.endsWith('/signed-document')) {
      const body = request.postDataBuffer()?.toString('utf8') ?? ''
      calls.push(`upload:${/name="file"; filename="([^"]*)"/.exec(body)?.[1] ?? '?'}`)
      const reply = opts.upload ?? { status: 200, body: { status: 'SIGNED' } }
      await route.fulfill({
        status: reply.status,
        contentType: reply.contentType ?? 'application/json',
        body: JSON.stringify(reply.body),
      })
    } else if (path.endsWith('/decline')) {
      calls.push(`decline:${(request.postDataJSON() as { reason: string }).reason}`)
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{"status":"DECLINED"}' })
    } else {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(opts.context ?? CONTEXT),
      })
    }
  })
  return calls
}

test('E2E-SIG-4 external signer opens the link and signs (S-PW-4)', async ({ page }) => {
  const calls = await mockSignatureApi(page)
  await page.goto(LINK)

  await expect(page.getByTestId('signature-simulation')).toContainText('SIMULATION')
  await expect(page.getByRole('heading', { name: /Contrat/ })).toBeVisible()
  await expect(page.getByTestId('signature-document')).toHaveAttribute('href', /^blob:/)
  // Le jeton a quitté la barre d'adresse (fragment effacé).
  await expect.poll(() => new URL(page.url()).hash).toBe('')

  await page.getByRole('button', { name: 'Signer' }).click()
  await expect(page.getByTestId('signature-signed')).toBeVisible()
  expect(calls).toEqual(['sign'])
})

test('E2E-SIG-5 external signer declines with a reason (S-PW-5)', async ({ page }) => {
  const calls = await mockSignatureApi(page)
  await page.goto(LINK)

  await page.getByRole('button', { name: 'Refuser' }).click()
  await page.getByLabel('Motif du refus (facultatif)').fill('Clause inacceptable')
  await page.getByRole('button', { name: 'Confirmer le refus' }).click()
  await expect(page.getByTestId('signature-declined')).toBeVisible()
  expect(calls).toEqual(['decline:Clause inacceptable'])
})

test('E2E-SIG-6 invalid link shows a neutral message (S-PW-6)', async ({ page }) => {
  await mockSignatureApi(page, { denied: true })
  await page.goto(LINK)

  await expect(page.getByTestId('portal-denied')).toBeVisible()
  await expect(page.locator('body')).not.toContainText('secret internal detail')
})

test('E2E-SIG-7 hosted signer is sent to the provider', async ({ page }) => {
  await page.route('https://yousign.app/**', async (route) => {
    await route.fulfill({ status: 200, contentType: 'text/html', body: '<html><body>fournisseur</body></html>' })
  })
  const calls = await mockSignatureApi(page, {
    context: { ...CONTEXT, simulated: false, hosted: true, signingUrl: 'https://yousign.app/signatures/abc' },
  })
  await page.goto(LINK)

  await expect(page).toHaveURL(/yousign\.app\/signatures\/abc/)
  expect(calls).toEqual([])
})

test('E2E-SIG-8 hosted signer without a link waits and can still decline', async ({ page }) => {
  await mockSignatureApi(page, {
    context: { ...CONTEXT, simulated: false, hosted: true, signingUrl: null },
  })
  await page.goto(LINK)

  await expect(page.getByTestId('signature-waiting')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Signer' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Refuser' })).toBeVisible()
})

const UPLOAD_CONTEXT = { ...CONTEXT, simulated: false, signedUpload: true }
const SIGNED_PDF = { name: 'Contrat signé.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.7 signed') }

test('E2E-SIG-11 external signer downloads, uploads the signed PDF and gets the confirmation', async ({ page }) => {
  const calls = await mockSignatureApi(page, { context: UPLOAD_CONTEXT })
  await page.goto(LINK)

  await expect(page.getByRole('button', { name: 'Signer', exact: true })).toHaveCount(0)
  const send = page.getByRole('button', { name: 'Envoyer le document signé' })
  await expect(send).toBeDisabled()

  const link = page.getByRole('link', { name: 'Télécharger le document à signer' })
  await expect(link).toHaveAttribute('href', /^blob:/)
  const [download] = await Promise.all([page.waitForEvent('download'), link.click()])
  expect(download.suggestedFilename()).toBe('Contrat.pdf')

  await page.getByLabel('Document signé (PDF)').setInputFiles(SIGNED_PDF)
  await expect(send).toBeEnabled()
  await send.click()

  await expect(page.getByTestId('signature-signed')).toBeVisible()
  expect(calls).toEqual(['upload:Contrat signé.pdf'])
})

test('E2E-SIG-12 external signer sees why the signed PDF was refused and can try again', async ({ page }) => {
  const calls = await mockSignatureApi(page, {
    context: UPLOAD_CONTEXT,
    upload: {
      status: 422,
      contentType: 'application/problem+json',
      body: { type: 'about:blank', status: 422, title: 'Unprocessable', code: 'NO_NEW_SIGNATURE' },
    },
  })
  await page.goto(LINK)

  await page.getByLabel('Document signé (PDF)').setInputFiles(SIGNED_PDF)
  await page.getByRole('button', { name: 'Envoyer le document signé' }).click()

  await expect(page.getByRole('alert')).toContainText('ne contient pas votre signature')
  await expect(page.getByTestId('signature-signed')).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Envoyer le document signé' })).toBeEnabled()
  expect(calls).toEqual(['upload:Contrat signé.pdf'])
})

test('E2E-SIG-13 external signer shows the generic refusal for an unknown code and the size message for a 413', async ({ page }) => {
  await mockSignatureApi(page, {
    context: UPLOAD_CONTEXT,
    upload: { status: 422, contentType: 'application/problem+json', body: { code: 'SOMETHING_NEW' } },
  })
  await page.goto(LINK)
  await page.getByLabel('Document signé (PDF)').setInputFiles(SIGNED_PDF)
  await page.getByRole('button', { name: 'Envoyer le document signé' }).click()
  await expect(page.getByRole('alert')).toContainText('Le document signé a été refusé')

  await page.unroute('**/api/portal/signature**')
  await mockSignatureApi(page, { context: UPLOAD_CONTEXT, upload: { status: 413, body: { detail: 'too big' } } })
  await page.getByRole('button', { name: 'Envoyer le document signé' }).click()
  await expect(page.getByRole('alert')).toHaveText('Le fichier dépasse la taille autorisée.')
})

test('E2E-SIG-14 external signer can still decline in the signed-upload mode', async ({ page }) => {
  const calls = await mockSignatureApi(page, { context: UPLOAD_CONTEXT })
  await page.goto(LINK)

  await page.getByRole('button', { name: 'Refuser' }).click()
  await page.getByRole('button', { name: 'Confirmer le refus' }).click()
  await expect(page.getByTestId('signature-declined')).toBeVisible()
  expect(calls).toEqual(['decline:null'])
})
