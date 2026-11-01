// E2E « pilotage » — Digital Mail Room : écran « Déposer un courrier » (TODO §113, D-MAILROOM-DEPOSIT).
import { expect, test } from '@playwright/test'
import { mockSession, openApp } from './support/pilotage'

test('E2E-MAILROOM-DEPOSIT-1 an agent deposits two PDF files and sees the mail created for each, or why it was refused', async ({
  page,
}) => {
  let posted: { files: number; sender: string | null } | null = null
  await mockSession(page)
  await page.route('**/api/mail-deposit-points', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([{ id: 'p1', name: 'Courrier papier (accueil)', dossierTypeCode: 'COURRIER' }]),
    })
  })
  await page.route('**/api/mail-deposit-points/p1/deposits', async (route) => {
    const body = route.request().postDataBuffer()?.toString('latin1') ?? ''
    posted = {
      files: (body.match(/name="files"/g) ?? []).length,
      sender: /name="expediteur"\r\n\r\n([^\r]*)/.exec(body)?.[1] ?? null,
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        results: [
          { filename: 'lettre.pdf', dossierId: 'd1', reference: 'DOS-COURRIER-2026-000007', refusal: null },
          { filename: 'photo.pdf', dossierId: null, reference: null, refusal: 'pas un PDF' },
        ],
      }),
    })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Déposer un courrier' }).click()
  await expect(page.getByRole('heading', { name: 'Déposer un courrier' })).toBeVisible()
  await expect(page.getByTestId('mail-deposit-point')).toHaveValue('p1')
  await page.getByTestId('mail-deposit-files').setInputFiles([
    { name: 'lettre.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4 lettre') },
    { name: 'photo.pdf', mimeType: 'application/pdf', buffer: Buffer.from('JFIF') },
  ])
  await page.getByTestId('mail-deposit-sender').fill('avocat@cabinet.example')
  await page.getByTestId('mail-deposit-send').click()
  const rows = page.getByTestId('mail-deposit-result')
  await expect(rows).toHaveCount(2)
  await expect(rows.nth(0).getByRole('link', { name: 'DOS-COURRIER-2026-000007' })).toHaveAttribute('href', '/dossiers/d1')
  await expect(rows.nth(1)).toContainText('Refusé : pas un PDF')
  await expect.poll(() => posted).toEqual({ files: 2, sender: 'avocat@cabinet.example' })
})

test('E2E-MAILROOM-DEPOSIT-2 without a deposit point the screen says who can open one', async ({ page }) => {
  await mockSession(page)
  await page.route('**/api/mail-deposit-points', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }),
  )
  await openApp(page)
  await page.getByRole('link', { name: 'Déposer un courrier' }).click()
  await expect(page.getByTestId('mail-deposit-no-point')).toContainText('Aucun point de dépôt')
})
