import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import NotificationWebhooksAdminView from './NotificationWebhooksAdminView.vue'
import * as api from '@/api/notificationWebhooks'
import { routes } from '@/app/routes'
import { asPage } from '@/api/page'

vi.mock('@/api/notificationWebhooks')

describe('NotificationWebhooksAdminView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listNotificationWebhooks).mockResolvedValue(
      asPage([
        {
          id: 'w1',
          tenantId: 't',
          name: 'Claims',
          targetUrl: 'https://example.com/hook',
          secret: '********',
          secretConfigured: true,
          enabled: true,
          createdAt: '',
          updatedAt: '',
        },
      ]),
    )
  })

  it('lists webhooks without showing a real secret and can create', async () => {
    vi.mocked(api.createNotificationWebhook).mockResolvedValue({
      id: 'w2',
      tenantId: 't',
      name: 'New',
      targetUrl: 'https://example.com/new',
      secret: '********',
      secretConfigured: true,
      enabled: true,
      createdAt: '',
      updatedAt: '',
    })
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/admin/notification-webhooks')
    await router.isReady()
    const wrapper = mount(NotificationWebhooksAdminView, { global: { plugins: [router] } })
    await flushPromises()
    expect(wrapper.text()).toContain('Claims')
    expect(wrapper.text()).toContain('secret configuré')
    expect(wrapper.text()).not.toContain('plain-secret')
    await wrapper.get('[data-testid="webhook-name"]').setValue('Ops')
    await wrapper.get('[data-testid="webhook-url"]').setValue('https://example.com/ops')
    await wrapper.get('[data-testid="webhook-secret"]').setValue('plain-secret')
    await wrapper.get('[data-testid="webhook-create-form"]').trigger('submit.prevent')
    await flushPromises()
    expect(api.createNotificationWebhook).toHaveBeenCalledWith({
      name: 'Ops',
      targetUrl: 'https://example.com/ops',
      secret: 'plain-secret',
      enabled: true,
    })
    expect((wrapper.get('[data-testid="webhook-secret"]').element as HTMLInputElement).value).toBe('')
  })

  it('shows ProblemDetail on create error', async () => {
    vi.mocked(api.createNotificationWebhook).mockRejectedValue({
      response: { status: 400, data: { detail: 'targetUrl is blocked', status: 400 } },
    })
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/admin/notification-webhooks')
    await router.isReady()
    const wrapper = mount(NotificationWebhooksAdminView, { global: { plugins: [router] } })
    await flushPromises()
    await wrapper.get('[data-testid="webhook-name"]').setValue('Bad')
    await wrapper.get('[data-testid="webhook-url"]').setValue('http://localhost/hook')
    await wrapper.get('[data-testid="webhook-create-form"]').trigger('submit.prevent')
    await flushPromises()
    expect(wrapper.get('[data-testid="webhook-error"]').text()).toContain('targetUrl is blocked')
  })
})
