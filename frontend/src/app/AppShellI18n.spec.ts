import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import AppShell from './AppShell.vue'
import { routes } from '@/app/routes'
import { useAuthStore } from '@/stores/auth'
import { applyTenantDefault, currentLocale, setLocale } from '@/i18n'

vi.mock('@/stores/notifications', () => ({
  useNotificationsStore: () => ({
    items: [], unreadCount: 0, failed: false, connected: false,
    connect: vi.fn(), disconnect: vi.fn(), markRead: vi.fn(), markAllRead: vi.fn(),
  }),
}))

async function mountShell() {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push('/supervision')
  await router.isReady()
  return mount(AppShell, { global: { plugins: [router] } })
}

/** TODO §90 (D7) — le chrome de l'application suit la langue et son sélecteur la change sans recharger. */
describe('AppShell language (D7)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    const auth = useAuthStore()
    auth.user = { id: 'a', name: 'Admin', email: null, roles: ['TENANT_ADMIN'] }
  })

  it('renders the navigation in English when the language is English', async () => {
    setLocale('en')
    const wrapper = await mountShell()
    expect(wrapper.text()).toContain('Tasks')
    expect(wrapper.text()).toContain('New dossier')
    expect(wrapper.text()).toContain('Decision tables')
    expect(wrapper.text()).toContain('Log out')
    expect(wrapper.text()).not.toContain('Tâches')
    expect(wrapper.text()).not.toContain('Déconnexion')
  })

  it('the switcher re-renders the whole shell in place and remembers the choice', async () => {
    const wrapper = await mountShell()
    expect(wrapper.text()).toContain('Déconnexion')
    await wrapper.get('[data-testid="locale-switcher"]').setValue('en')
    expect(wrapper.text()).toContain('Log out')
    expect(currentLocale()).toBe('en')
    expect(window.localStorage.getItem('myrmacta.locale')).toBe('en')
    await wrapper.get('[data-testid="locale-switcher"]').setValue('fr')
    expect(wrapper.text()).toContain('Déconnexion')
  })

  it('an English tenant default applies at login, but never over the user choice', async () => {
    applyTenantDefault('en')
    expect((await mountShell()).text()).toContain('Log out')
    setLocale('fr')
    applyTenantDefault('en')
    expect((await mountShell()).text()).toContain('Déconnexion')
  })
})
