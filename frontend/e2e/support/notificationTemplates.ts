// Mock de l'API des gabarits de notification (TODO §123, issue #19) : état en mémoire calqué sur /api/notification-templates.
import type { Page } from '@playwright/test'
import { envelope } from './pilotage'

export type MockNotificationTemplate = {
  id: string
  templateKey: string
  variantKey: string | null
  locale: string | null
  label: string
  version: number
  status: 'DRAFT' | 'ACTIVE' | 'ARCHIVED'
  subject: string
  textBody: string
  htmlBody: string | null
  createdAt: string
  updatedAt: string
  updatedBy: string | null
}

const VARIABLE = '[[${dossier.reference}]]'

export async function mockNotificationTemplates(
  page: Page,
  opts: { activateRefusal?: { status: number; detail: string } } = {},
) {
  const store: MockNotificationTemplate[] = []
  const previews: Array<Record<string, unknown>> = []
  const json = (body: unknown, status = 200) => ({ status, contentType: 'application/json', body: JSON.stringify(body) })
  await page.route(/\/api\/notification-templates(?:\/.*)?(?:\?.*)?$/, async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api/notification-templates', '')
    const method = request.method()
    if (method === 'GET' && path === '') return route.fulfill(json(envelope(store)))
    if (method === 'POST' && path === '') {
      const body = request.postDataJSON() as Partial<MockNotificationTemplate>
      const created: MockNotificationTemplate = {
        id: `nt-${store.length + 1}`,
        templateKey: body.templateKey ?? '',
        variantKey: body.variantKey ?? null,
        locale: body.locale ?? null,
        label: body.label ?? '',
        version: 1,
        status: 'DRAFT',
        subject: body.subject ?? '',
        textBody: body.textBody ?? '',
        htmlBody: body.htmlBody ?? null,
        createdAt: '2026-11-07T10:00:00Z',
        updatedAt: '2026-11-07T10:00:00Z',
        updatedBy: 'ada',
      }
      store.push(created)
      return route.fulfill(json(created, 201))
    }
    if (method === 'POST' && path === '/preview') {
      const body = request.postDataJSON() as { template?: { subject: string; textBody: string; htmlBody: string | null } }
      previews.push(body)
      const t = body.template
      return route.fulfill(
        json({
          subject: (t?.subject ?? '').replace(VARIABLE, 'DOS-1'),
          textBody: (t?.textBody ?? '').replace(VARIABLE, 'DOS-1'),
          htmlBody: t?.htmlBody ? t.htmlBody.replace(VARIABLE, 'DOS-1') : null,
        }),
      )
    }
    const activate = path.match(/^\/([^/]+)\/activate$/)
    if (method === 'POST' && activate) {
      if (opts.activateRefusal) {
        return route.fulfill(
          json({ title: 'Refused', status: opts.activateRefusal.status, detail: opts.activateRefusal.detail }, opts.activateRefusal.status),
        )
      }
      const found = store.find((tpl) => tpl.id === activate[1])
      if (found) found.status = 'ACTIVE'
      return route.fulfill(json(found))
    }
    return route.fulfill({ status: 405, body: '' })
  })
  return { store, previews }
}
