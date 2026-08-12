import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import axios from 'axios'
import { useAuthStore } from './auth'

vi.mock('axios')

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(axios.get).mockReset()
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

  it('redirects to Keycloak authorization when /me returns 401', async () => {
    vi.mocked(axios.get).mockRejectedValue({ response: { status: 401 } })
    const location = { href: '' }
    vi.stubGlobal('window', { location })

    const store = useAuthStore()
    await store.init()

    expect(store.authenticated).toBe(false)
    expect(location.href).toBe('/oauth2/authorization/oidc')
  })
})
