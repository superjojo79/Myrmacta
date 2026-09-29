import { expect, test } from '@playwright/test'

// Une exécution = une action d'un signataire externe sur son vrai lien d'invitation.
//   SIGNATURE_LINK : lien reçu par e-mail ; SIGNATURE_MODE : sign | decline | denied
const link = process.env.SIGNATURE_LINK ?? ''
const mode = process.env.SIGNATURE_MODE ?? ''

test.skip(!link || !mode, 'lancé par SignatureBrowserE2eTest')

test(`signataire externe : ${mode}`, async ({ page }) => {
  await page.goto(link)

  if (mode === 'denied') {
    await expect(page.getByTestId('portal-denied')).toBeVisible()
    return
  }

  await expect(page.getByTestId('signature-simulation')).toContainText('SIMULATION')
  await expect(page.getByTestId('signature-document')).toHaveAttribute('href', /^blob:/)
  // Le jeton a quitté la barre d'adresse.
  await expect.poll(() => new URL(page.url()).hash).toBe('')

  if (mode === 'sign') {
    await page.getByRole('button', { name: 'Signer' }).click()
    await expect(page.getByTestId('signature-signed')).toBeVisible()
  } else {
    await page.getByRole('button', { name: 'Refuser' }).click()
    await page.getByLabel('Motif du refus (facultatif)').fill('Clause inacceptable')
    await page.getByRole('button', { name: 'Confirmer le refus' }).click()
    await expect(page.getByTestId('signature-declined')).toBeVisible()
  }

  // Le même lien est désormais mort, avec l'écran neutre.
  const again = await page.context().newPage()
  await again.goto(link)
  await expect(again.getByTestId('portal-denied')).toBeVisible()
})
