// E2E « pilotage » — Connecteurs (TODO §95 : découpage de pilotage.spec.ts par écran).
import { expect, test } from '@playwright/test'
import {
  envelope,
  mockSession,
  openApp,
} from './support/pilotage'

test('E2E-CONN-1 admin creates http-rest connector from generated form', async ({ page }) => {
  let createdBody: Record<string, unknown> | null = null
  let connectors: Array<Record<string, unknown>> = []
  await mockSession(page)
  await page.route(/\/api\/connectors(?:\?|$)/, async (route) => {
    if (route.request().method() === 'POST') {
      createdBody = route.request().postDataJSON() as Record<string, unknown>
      const created = {
        id: 'c-new',
        tenantId: 'tenant_dev',
        name: createdBody.name,
        connectorType: createdBody.connectorType,
        enabled: true,
        config: createdBody.config,
        secretConfigured: { webhookSecret: true },
      }
      connectors = [...connectors, created]
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(created),
      })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope(connectors)),
    })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Connecteurs' }).click()
  const form = page.getByTestId('connector-create-form')
  await form.getByPlaceholder('nom').fill('Hook prod')
  await form.getByTestId('connector-type').selectOption('http-rest')
  await form.getByTestId('connector-config-field-webhookSecret').fill('a-very-long-secret')
  await form.getByTestId('connector-config-field-processKey').fill('signal-smoke')
  await form.getByTestId('connector-config-field-dossierTypeCode').fill('SINISTRE')
  await expect(form.locator('textarea')).toHaveCount(0)
  await form.getByRole('button', { name: 'Créer' }).click()
  await expect.poll(() => createdBody?.connectorType).toBe('http-rest')
  await expect
    .poll(() => (createdBody?.config as Record<string, unknown> | undefined)?.webhookAuthType)
    .toBe('HMAC_SHA256')
  await expect
    .poll(() => (createdBody?.config as Record<string, unknown> | undefined)?.webhookSecret)
    .toBe('a-very-long-secret')
  await expect(page.getByText('Hook prod')).toBeVisible()
  await expect(page.getByTestId('connector-ingress-url')).toHaveText(
    /POST http:\/\/127\.0\.0\.1:\d+\/api\/webhooks\/c-new/,
  )
})

test('E2E-CONN-2 admin creates smtp connector with empty config', async ({ page }) => {
  let createdBody: Record<string, unknown> | null = null
  let connectors: Array<Record<string, unknown>> = []
  await mockSession(page)
  await page.route(/\/api\/connectors(?:\?|$)/, async (route) => {
    if (route.request().method() === 'POST') {
      createdBody = route.request().postDataJSON() as Record<string, unknown>
      const created = {
        id: 'c-smtp',
        tenantId: 'tenant_dev',
        name: createdBody.name,
        connectorType: createdBody.connectorType,
        enabled: true,
        config: createdBody.config ?? {},
        secretConfigured: {},
      }
      connectors = [...connectors, created]
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(created),
      })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope(connectors)),
    })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Connecteurs' }).click()
  const form = page.getByTestId('connector-create-form')
  await form.getByTestId('connector-type').selectOption('smtp')
  await expect(form.getByTestId('connector-config-empty')).toBeVisible()
  await expect(form.locator('textarea')).toHaveCount(0)
  await form.getByPlaceholder('nom').fill('Mailer')
  await form.getByRole('button', { name: 'Créer' }).click()
  await expect.poll(() => createdBody?.connectorType).toBe('smtp')
  await expect.poll(() => createdBody?.config).toEqual({})
  await expect(page.getByText('Mailer')).toBeVisible()
  await expect(page.getByTestId('connector-list').getByTestId('connector-ingress')).toHaveCount(0)
})

test('E2E-CONN-3 admin sees callback URL after creating flowable-callback', async ({ page }) => {
  let createdBody: Record<string, unknown> | null = null
  let connectors: Array<Record<string, unknown>> = []
  await mockSession(page)
  await page.route(/\/api\/connectors(?:\?|$)/, async (route) => {
    if (route.request().method() === 'POST') {
      createdBody = route.request().postDataJSON() as Record<string, unknown>
      const created = {
        id: 'c-cb',
        tenantId: 'tenant_dev',
        name: createdBody.name,
        connectorType: createdBody.connectorType,
        enabled: true,
        config: createdBody.config,
        secretConfigured: { webhookSecret: true },
      }
      connectors = [...connectors, created]
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(created),
      })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope(connectors)),
    })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Connecteurs' }).click()
  const form = page.getByTestId('connector-create-form')
  await form.getByPlaceholder('nom').fill('Async hook')
  await form.getByTestId('connector-type').selectOption('flowable-callback')
  await form.getByTestId('connector-config-field-webhookSecret').fill('a-very-long-secret')
  await form.getByTestId('connector-config-field-messageName').fill('orderReceived')
  await form.getByTestId('connector-config-field-correlationVariableName').fill('orderId')
  await form.getByTestId('connector-config-field-correlationValuePath').fill('$.orderId')
  await form.getByRole('button', { name: 'Créer' }).click()
  await expect.poll(() => createdBody?.connectorType).toBe('flowable-callback')
  await expect(page.getByText('Async hook')).toBeVisible()
  await expect(page.getByTestId('connector-ingress')).toContainText('URL callback')
  await expect(page.getByTestId('connector-ingress-url')).toHaveText(
    /POST http:\/\/127\.0\.0\.1:\d+\/api\/callbacks\/c-cb/,
  )
})

test('E2E-CONN-BATCH-IMPORT-KEY admin configures batch-import with a private key (textarea, not input)', async ({
  page,
}) => {
  // connecteurs-cible (lot B1), D-CONFIG-FIELD-SECRET-TEXTAREA : ConfigFieldType.PASSWORD rend un
  // <input type="password"> mono-ligne, incompatible avec une clé privée SSH PEM multi-lignes.
  let createdBody: Record<string, unknown> | null = null
  let connectors: Array<Record<string, unknown>> = []
  await mockSession(page)
  await page.route(/\/api\/connectors(?:\?|$)/, async (route) => {
    if (route.request().method() === 'POST') {
      createdBody = route.request().postDataJSON() as Record<string, unknown>
      const created = {
        id: 'c-batch',
        tenantId: 'tenant_dev',
        name: createdBody.name,
        connectorType: createdBody.connectorType,
        enabled: true,
        config: createdBody.config,
        secretConfigured: { sftpPrivateKey: true },
      }
      connectors = [...connectors, created]
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(created),
      })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope(connectors)),
    })
  })
  await openApp(page)
  await page.getByRole('link', { name: 'Connecteurs' }).click()
  const form = page.getByTestId('connector-create-form')
  await form.getByPlaceholder('nom').fill('Import nightly')
  await form.getByTestId('connector-type').selectOption('batch-import')
  await form.getByTestId('connector-config-field-sftpHost').fill('sftp.example.com')
  await form.getByTestId('connector-config-field-sftpUsername').fill('flowforge')
  await form.getByTestId('connector-config-field-sftpAuthType').selectOption('PRIVATE_KEY')

  const privateKeyField = form.getByTestId('connector-config-field-sftpPrivateKey')
  await expect(privateKeyField).toHaveJSProperty('tagName', 'TEXTAREA')
  const privateKey = '-----BEGIN OPENSSH PRIVATE KEY-----\nabc\ndef\n-----END OPENSSH PRIVATE KEY-----'
  await privateKeyField.fill(privateKey)

  await form.getByTestId('connector-config-field-sftpHostKeyFingerprint').fill('SHA256:abcdefgh')
  await form.getByTestId('connector-config-field-remoteDirectory').fill('/in')
  await form.getByTestId('connector-config-field-filenamePattern').fill('*.csv')
  await form.getByTestId('connector-config-field-archiveDirectory').fill('/archive')
  await form.getByTestId('connector-config-field-cronExpression').fill('0 0 * * * *')
  await form.getByTestId('connector-config-field-processKey').fill('importProcess')
  await form.getByTestId('connector-config-field-dossierTypeCode').fill('IMPORT')
  await form.getByRole('button', { name: 'Créer' }).click()

  await expect.poll(() => createdBody?.connectorType).toBe('batch-import')
  await expect
    .poll(() => (createdBody?.config as Record<string, unknown> | undefined)?.sftpPrivateKey)
    .toBe(privateKey)
  await expect(page.getByText('Import nightly')).toBeVisible()
})
