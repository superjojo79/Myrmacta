import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import { listPortalLinks, revokePortalLink, sendPortalLink } from './portalLinks'

vi.mock('axios')

describe('portalLinks api (TODO §68)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('lists the links of a dossier, encoding the id', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: { canManage: true, links: [] } })

    await expect(listPortalLinks('d 1')).resolves.toEqual({ canManage: true, links: [] })
    expect(axios.get).toHaveBeenCalledWith('/api/dossiers/d%201/portal-links')
  })

  it('sends a link and accepts 409 (NOT_SENT) as a normal outcome', async () => {
    vi.mocked(axios.post).mockResolvedValue({ data: { id: 'l1', status: 'NOT_SENT' } })

    await sendPortalLink('d1', 'task/7')

    const [url, body, config] = vi.mocked(axios.post).mock.calls[0]!
    expect(url).toBe('/api/dossiers/d1/portal-links/tasks/task%2F7/send')
    expect(body).toBeUndefined()
    const validate = (config as { validateStatus: (s: number) => boolean }).validateStatus
    expect([201, 409].map(validate)).toEqual([true, true])
    expect([400, 403, 500].map(validate)).toEqual([false, false, false])
  })

  it('revokes a link', async () => {
    vi.mocked(axios.post).mockResolvedValue({ data: { id: 'l1', status: 'REVOKED' } })

    await expect(revokePortalLink('d1', 'l1')).resolves.toMatchObject({ status: 'REVOKED' })
    expect(axios.post).toHaveBeenCalledWith('/api/dossiers/d1/portal-links/l1/revoke')
  })
})
