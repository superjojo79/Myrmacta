// E2E « pilotage » — Gabarits de notification (TODO §123, issue #19, CS1) : créer, aperçu, activer.
import { expect, test, type Page } from '@playwright/test'
import { mockSession, openApp } from './support/pilotage'
import { mockNotificationTemplates } from './support/notificationTemplates'

async function openScreen(page: Page) {
  await openApp(page)
  await page.getByRole('link', { name: 'Gabarits de notification' }).click()
  await expect(page.getByRole('heading', { name: 'Gabarits de notification' })).toBeVisible()
}

async function fillMinimal(page: Page) {
  const form = page.getByTestId('nt-form')
  await form.getByLabel('Clé du gabarit').fill('k')
  await form.getByLabel('Libellé').fill('L')
  await form.getByLabel('Objet').fill('S')
  await form.getByLabel('Corps texte').fill('T')
}

test('E2E-NT-1 create a notification template, preview it, then activate it', async ({ page }) => {
  await mockSession(page)
  const mock = await mockNotificationTemplates(page)
  await openScreen(page)

  const form = page.getByTestId('nt-form')
  await form.getByLabel('Clé du gabarit').fill('accuse-reception')
  await form.getByLabel('Libellé').fill('Accusé de réception')
  await form.getByLabel('Objet').fill('Accusé : [[${dossier.reference}]]')
  await form.getByLabel('Corps texte').fill('Votre dossier [[${dossier.reference}]] est reçu.')
  await form.getByLabel('Corps HTML (facultatif)').fill('<p>Dossier <b>[[${dossier.reference}]]</b></p>')

  await page.getByTestId('nt-preview-form').click()
  await expect(page.getByTestId('nt-preview-subject')).toHaveText('Accusé : DOS-1')
  await expect(page.getByTestId('nt-preview-text')).toHaveText('Votre dossier DOS-1 est reçu.')
  const frame = page.locator('iframe[title="Aperçu du corps HTML"]')
  await expect(frame).toBeVisible()
  await expect(frame).toHaveAttribute('sandbox', '')
  await expect(page.frameLocator('iframe[title="Aperçu du corps HTML"]').locator('b')).toHaveText('DOS-1')

  await page.getByTestId('nt-save').click()
  const item = page.locator('li', { hasText: 'accuse-reception' })
  await expect(item).toContainText('DRAFT')
  expect(mock.store).toHaveLength(1)
  expect(mock.store[0].htmlBody).toContain('<b>')

  await item.getByTestId('nt-activate').click()
  await expect(item).toContainText('ACTIVE')
  await expect(item.getByTestId('nt-activate')).toHaveCount(0)
})

test('E2E-NT-2 a refused activation shows the violated rule', async ({ page }) => {
  await mockSession(page)
  await mockNotificationTemplates(page, { activateRefusal: { status: 400, detail: 'htmlBody: tag <img> is not allowed' } })
  await openScreen(page)
  await fillMinimal(page)
  await page.getByTestId('nt-save').click()
  await page.getByTestId('nt-activate').click()
  await expect(page.getByTestId('nt-error')).toContainText('Gabarit refusé : htmlBody: tag <img> is not allowed')
})

test('E2E-NT-3 a conflict on activation is shown readably', async ({ page }) => {
  await mockSession(page)
  await mockNotificationTemplates(page, { activateRefusal: { status: 409, detail: 'Only a draft can be activated' } })
  await openScreen(page)
  await fillMinimal(page)
  await page.getByTestId('nt-save').click()
  await page.getByTestId('nt-activate').click()
  await expect(page.getByTestId('nt-error')).toContainText('Only a draft can be activated')
})
