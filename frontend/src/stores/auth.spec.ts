import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import axios from 'axios'
import { useAuthStore } from './auth'

vi.mock('axios')
vi.mock('@/stores/notifications', () => ({
  useNotificationsStore: () => ({ disconnect: vi.fn() }),
}))

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(axios.get).mockReset()
    document.body.innerHTML = ''
    document.cookie = 'XSRF-TOKEN=test-csrf'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('loads the current user from /api/auth/me including id', async () => {
    vi.mocked(axios.get).mockResolvedValue({
      data: {
        id: 'user-1',
        name: 'Ada Lovelace',
        email: 'ada@example.com',
        roles: ['WORKFLOW_ADMIN'],
      },
    })

    const store = useAuthStore()
    await store.init()

    expect(store.authenticated).toBe(true)
    expect(store.user?.id).toBe('user-1')
    expect(store.isWorkflowAdmin).toBe(true)
    expect(axios.get).toHaveBeenCalledWith('/api/auth/me')
  })

  it('redirects to OIDC authorization only when /me returns 401', async () => {
    vi.mocked(axios.get).mockRejectedValue({ response: { status: 401 } })
    const location = { href: '' }
    vi.stubGlobal('window', { location, document })

    const store = useAuthStore()
    await store.init()

    expect(store.authenticated).toBe(false)
    expect(location.href).toContain('/oauth2/authorization/oidc')
  })

  it('does not redirect on non-auth errors', async () => {
    vi.mocked(axios.get).mockRejectedValue({ response: { status: 500 }, message: 'boom' })
    const location = { href: '' }
    vi.stubGlobal('window', { location, document })

    const store = useAuthStore()
    await store.init()

    expect(store.bootError).toBeTruthy()
    expect(location.href).toBe('')
  })

  it('logout submits a full-page form to /api/auth/logout with CSRF', () => {
    const store = useAuthStore()
    store.user = { id: 'u', name: 'Ada', email: null, roles: [] }
    store.logout()
    const form = document.querySelector('form') as HTMLFormElement
    expect(form).toBeTruthy()
    expect(form.method.toLowerCase()).toBe('post')
    expect(form.action).toContain('/api/auth/logout')
    expect((form.querySelector('input[name="_csrf"]') as HTMLInputElement).value).toBe('test-csrf')
    expect(store.user).toBeNull()
  })
})
