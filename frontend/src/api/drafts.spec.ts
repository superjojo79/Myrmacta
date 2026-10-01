import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import { discardDraft, listPendingDrafts, listPublications, publishDraft } from './drafts'

vi.mock('axios')

describe('drafts api (TODO §82, D6)', () => {
  beforeEach(() => vi.resetAllMocks())

  it('lists pending drafts and publications', async () => {
    vi.mocked(axios.get).mockResolvedValueOnce({ data: [{ key: 'a' }] }).mockResolvedValueOnce({ data: [] })
    expect(await listPendingDrafts()).toEqual([{ key: 'a' }])
    expect(axios.get).toHaveBeenCalledWith('/api/admin/drafts')
    await listPublications(5)
    expect(axios.get).toHaveBeenLastCalledWith('/api/admin/drafts/publications', { params: { limit: 5 } })
  })

  it('publishes exactly the reviewed draft with its timestamp and encodes the key', async () => {
    vi.mocked(axios.post).mockResolvedValue({ data: { id: 'p' } })
    await publishDraft('BPMN', 'a b/c', '2026-10-01T10:00:00.123456Z')
    expect(axios.post).toHaveBeenCalledWith('/api/admin/drafts/BPMN/a%20b%2Fc/publish', {
      expectedUpdatedAt: '2026-10-01T10:00:00.123456Z',
    })
  })

  it('discards a draft', async () => {
    vi.mocked(axios.delete).mockResolvedValue({})
    await discardDraft('DMN', 'table', '2026-10-01T10:00:00Z')
    expect(axios.delete).toHaveBeenCalledWith('/api/admin/drafts/DMN/table', {
      params: { expectedUpdatedAt: '2026-10-01T10:00:00Z' },
    })
  })
})
