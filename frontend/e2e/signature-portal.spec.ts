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
}

async function mockSignatureApi(page: Page, opts: { denied?: boolean } = {}) {
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
    } else if (path.endsWith('/decline')) {
      calls.push(`decline:${(request.postDataJSON() as { reason: string }).reason}`)
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{"status":"DECLINED"}' })
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(CONTEXT) })
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
