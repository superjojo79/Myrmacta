import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import AppShell from './AppShell.vue'
import { routes } from '@/app/routes'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/stores/notifications', () => ({
  useNotificationsStore: () => ({
    items: [],
    unreadCount: 0,
    failed: false,
    connected: false,
    connect: vi.fn(),
    disconnect: vi.fn(),
    markRead: vi.fn(),
    markAllRead: vi.fn(),
  }),
}))

describe('AppShell', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders FlowForge brand and pilotage nav without Rapports', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    router.push('/supervision')
    await router.isReady()

    const wrapper = mount(AppShell, {
      global: { plugins: [router] },
    })

    expect(wrapper.text()).toContain('FlowForge')
    expect(wrapper.text()).toContain('Supervision')
    expect(wrapper.text()).toContain('Tâches')
    expect(wrapper.text()).not.toContain('Rapports')
  })

  it('shows admin links for tenant admin', async () => {
    const auth = useAuthStore()
    auth.user = { id: 'a', name: 'Admin', email: null, roles: ['TENANT_ADMIN'] }
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/supervision')
    await router.isReady()
    const wrapper = mount(AppShell, { global: { plugins: [router] } })
    expect(wrapper.text()).toContain('Types')
    expect(wrapper.text()).toContain('Connecteurs')
    expect(wrapper.text()).toContain('File purge')
    expect(wrapper.text()).toContain('Webhooks')
  })
})
