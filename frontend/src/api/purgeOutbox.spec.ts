import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import { enqueueDossierPurge, listPurgeOutbox } from './purgeOutbox'

vi.mock('axios')

describe('purgeOutbox api', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('lists purge outbox with optional status', async () => {
    vi.mocked(axios.get).mockResolvedValue({
      data: {
        content: [
          {
            id: '1',
            dossierId: 'd1',
            status: 'PENDING',
            origin: 'ADMIN',
            eventType: 'DOSSIER_PURGE_REQUESTED',
            createdAt: '2026-08-19T12:00:00Z',
            lastError: null,
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        counts: { pending: 1, processing: 0, failed: 0, completed: 2 },
      },
    })
    const page = await listPurgeOutbox({ status: 'PENDING', page: 0, size: 20 })
    expect(axios.get).toHaveBeenCalledWith('/api/purge-outbox', {
      params: { status: 'PENDING', page: 0, size: 20 },
    })
    expect(page.counts.pending).toBe(1)
    expect(page.content[0].origin).toBe('ADMIN')
  })

  it('enqueues a dossier purge request', async () => {
    vi.mocked(axios.post).mockResolvedValue({ status: 201 })
    await enqueueDossierPurge('d1')
    expect(axios.post).toHaveBeenCalledWith('/api/dossiers/d1/purge-requests')
  })
})
