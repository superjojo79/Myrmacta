import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import { getRetentionSettings, upsertRetentionSettings } from './retentionSettings'

vi.mock('axios')

describe('retentionSettings api', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('gets settings', async () => {
    vi.mocked(axios.get).mockResolvedValue({
      data: {
        tenantId: 't',
        defaultRetentionDays: 30,
        updatedAt: '2026-01-01T00:00:00Z',
        updatedBy: 'admin',
      },
    })
    const settings = await getRetentionSettings()
    expect(axios.get).toHaveBeenCalledWith('/api/tenant/retention-settings')
    expect(settings.defaultRetentionDays).toBe(30)
  })

  it('puts settings', async () => {
    vi.mocked(axios.put).mockResolvedValue({
      data: {
        tenantId: 't',
        defaultRetentionDays: 90,
        updatedAt: '2026-01-01T00:00:00Z',
        updatedBy: 'admin',
      },
    })
    await upsertRetentionSettings(90)
    expect(axios.put).toHaveBeenCalledWith('/api/tenant/retention-settings', {
      defaultRetentionDays: 90,
    })
  })
})
