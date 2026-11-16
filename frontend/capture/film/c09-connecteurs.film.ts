// TODO §140 (D-VIDEO-SHORT, issue #58) — capsule courte C9 « Brancher vos applications », sur la démo complète. Aucun
// connecteur n'est créé (un connecteur entrant se mettrait à relever) : on parcourt les formulaires de quelques types,
// puis une instance de la démo est suspendue et réactivée.
import { expect, test } from '@playwright/test'
import { opening, plan, scrollTo, shortCapsule, underTheHood } from './short'

const capsule = shortCapsule('c09-connecteurs')
const TOGGLED = /^(Courrier papier|Paper mail)/

test('ouverture', async ({ page }) => {
  await opening(capsule, page)
})

test('catalogue', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'catalogue')
  await d.goto('/admin/connectors')
  await d.beat(2)
  const type = page.getByTestId('connector-type')
  for (const value of ['http-rest', 'imap-inbound', 'sftp-pdf-inbound', 'kafka']) {
    await type.selectOption(value)
    await scrollTo(page, type, 'start')
    await d.beat(1.8)
  }
  // Une API protégée : l'authentification OAuth2 client credentials du connecteur REST.
  await type.selectOption('http-rest')
  const auth = page.getByTestId('connector-config-field-authMode')
  await scrollTo(page, auth, 'center')
  await auth.selectOption('OAUTH2_CLIENT_CREDENTIALS')
  await d.beat(1)
  await d.holdForVoice(0.3)
  await d.wrap(order)
})

test('instances', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'instances')
  await d.goto('/admin/connectors')
  const row = page.locator('li').filter({ hasText: TOGGLED })
  await scrollTo(page, page.locator('li').filter({ hasText: /\(http-rest\)/ }).first(), 'center')
  await d.beat(3)
  const toggle = row.getByRole('button', { name: /^(Basculer|Toggle)$/ })
  await toggle.click()
  await expect(row).toContainText(/inactif|disabled/)
  await d.beat(1.8)
  await toggle.click()
  await expect(row).not.toContainText(/inactif|disabled/)
  await d.holdForVoice(0.3)
  await d.wrap(order)
})

test('sous-le-capot', async ({ page }) => {
  await underTheHood(capsule, page)
})
