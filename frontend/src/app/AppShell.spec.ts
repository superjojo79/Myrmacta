import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import AppShell from './AppShell.vue'
import { routes } from '@/app/routes'
import { useAuthStore } from '@/stores/auth'
import { SHELL_NAV_NARROW_MQ } from '@/composables/useShellNavCollapse'

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

type Listener = (event: MediaQueryListEvent) => void

function stubViewport(narrow: boolean) {
  const listeners = new Set<Listener>()
  const mql = {
    matches: narrow,
    media: SHELL_NAV_NARROW_MQ,
    onchange: null,
    addEventListener: (_event: string, listener: Listener) => {
      listeners.add(listener)
    },
    removeEventListener: (_event: string, listener: Listener) => {
      listeners.delete(listener)
    },
    addListener: () => undefined,
    removeListener: () => undefined,
    dispatchEvent: () => true,
  }
  vi.stubGlobal(
    'matchMedia',
    vi.fn(() => mql),
  )
}

async function mountShell() {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push('/supervision')
  await router.isReady()
  return mount(AppShell, { global: { plugins: [router] } })
}

describe('AppShell', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    stubViewport(false)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renders the Myrmacta brand and pilotage nav without Rapports', async () => {
    const wrapper = await mountShell()

    expect(wrapper.get('img').attributes('alt')).toBe('Myrmacta')
    expect(wrapper.get('img').attributes('src')).toContain('myrmacta-logo-dark')
    expect(wrapper.text()).toContain("L'intelligence collective")
    expect(wrapper.text()).toContain('Supervision')
    expect(wrapper.text()).toContain('Tâches')
    expect(wrapper.text()).not.toContain('Rapports')
  })

  it('starts open on a wide viewport and collapses to a rail with the burger (D-SHELL-NAV)', async () => {
    const wrapper = await mountShell()
    const shell = wrapper.get('[data-testid="app-shell"]')
    expect(shell.classes()).not.toContain('app-shell--collapsed')
    expect(wrapper.get('[data-testid="shell-nav-body"]').attributes('style') ?? '').not.toContain(
      'display: none',
    )

    await wrapper.get('[data-testid="shell-nav-toggle"]').trigger('click')
    expect(shell.classes()).toContain('app-shell--collapsed')
    expect(wrapper.get('[data-testid="shell-nav-body"]').attributes('style')).toContain(
      'display: none',
    )
    expect(wrapper.get('[data-testid="shell-nav-toggle"]').attributes('aria-expanded')).toBe('false')
  })

  it('starts collapsed on a narrow viewport and opens as a drawer (D-SHELL-NAV)', async () => {
    stubViewport(true)
    const wrapper = await mountShell()
    const shell = wrapper.get('[data-testid="app-shell"]')
    expect(shell.classes()).toContain('app-shell--collapsed')
    expect(shell.classes()).toContain('app-shell--narrow')
    expect(wrapper.find('[data-testid="shell-nav-backdrop"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="shell-nav-body"]').attributes('style')).toContain(
      'display: none',
    )

    await wrapper.get('[data-testid="shell-nav-toggle"]').trigger('click')
    expect(shell.classes()).toContain('app-shell--drawer-open')
    expect(wrapper.get('[data-testid="shell-nav-body"]').attributes('style') ?? '').not.toContain(
      'display: none',
    )
    expect(wrapper.get('[data-testid="shell-nav-backdrop"]').exists()).toBe(true)
  })

  it('closes the narrow drawer on backdrop click and on a nav link click (D-SHELL-NAV)', async () => {
    stubViewport(true)
    const wrapper = await mountShell()
    await wrapper.get('[data-testid="shell-nav-toggle"]').trigger('click')
    expect(wrapper.get('[data-testid="app-shell"]').classes()).toContain('app-shell--drawer-open')

    await wrapper.get('[data-testid="shell-nav-backdrop"]').trigger('click')
    expect(wrapper.get('[data-testid="app-shell"]').classes()).toContain('app-shell--collapsed')

    await wrapper.get('[data-testid="shell-nav-toggle"]').trigger('click')
    await wrapper.get('a[href="/tasks"]').trigger('click')
    expect(wrapper.get('[data-testid="app-shell"]').classes()).toContain('app-shell--collapsed')
  })

  it('shows Rapports for tenant admin', async () => {
    const auth = useAuthStore()
    auth.user = { id: 'a', name: 'Admin', email: null, roles: ['TENANT_ADMIN'] }
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/supervision')
    await router.isReady()
    const wrapper = mount(AppShell, { global: { plugins: [router] } })
    expect(wrapper.text()).toContain('Rapports')
  })

  it('shows Rapports for dossier manager', async () => {
    const auth = useAuthStore()
    auth.user = { id: 'm', name: 'Mgr', email: null, roles: ['DOSSIER_MANAGER'] }
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/supervision')
    await router.isReady()
    const wrapper = mount(AppShell, { global: { plugins: [router] } })
    expect(wrapper.text()).toContain('Rapports')
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
    expect(wrapper.text()).toContain('Observabilité')
    expect(wrapper.text()).toContain('Webhooks')
  })

  it('hides observability link for workflow admin', async () => {
    const auth = useAuthStore()
    auth.user = { id: 'w', name: 'Wf', email: null, roles: ['WORKFLOW_ADMIN'] }
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/supervision')
    await router.isReady()
    const wrapper = mount(AppShell, { global: { plugins: [router] } })
    expect(wrapper.text()).toContain('Workflows')
    expect(wrapper.text()).not.toContain('Observabilité')
  })

  it('hides observability link for dossier viewer', async () => {
    const auth = useAuthStore()
    auth.user = { id: 'v', name: 'Viewer', email: null, roles: ['DOSSIER_VIEWER'] }
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/supervision')
    await router.isReady()
    const wrapper = mount(AppShell, { global: { plugins: [router] } })
    expect(wrapper.text()).not.toContain('Observabilité')
    expect(wrapper.text()).not.toContain('Rapports')
  })
})
