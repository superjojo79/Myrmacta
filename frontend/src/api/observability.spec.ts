import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import { getObservabilitySnapshot } from './observability'

vi.mock('axios')

describe('observability api', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('gets snapshot', async () => {
    vi.mocked(axios.get).mockResolvedValue({
      data: { tenantId: 'tenant_dev', triggerOutboxOldestPendingAgeSeconds: 1 },
    })
    const snap = await getObservabilitySnapshot()
    expect(axios.get).toHaveBeenCalledWith('/api/observability')
    expect(snap.tenantId).toBe('tenant_dev')
  })
})
