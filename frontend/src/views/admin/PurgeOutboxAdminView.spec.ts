import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import PurgeOutboxAdminView from './PurgeOutboxAdminView.vue'
import * as api from '@/api/purgeOutbox'

vi.mock('@/api/purgeOutbox')

describe('PurgeOutboxAdminView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('renders counts and queue without a force button', async () => {
    vi.mocked(api.listPurgeOutbox).mockResolvedValue({
      items: [
        {
          id: '1',
          dossierId: 'd1',
          status: 'FAILED',
          origin: 'RETENTION',
          eventType: 'DOSSIER_PURGE_REQUESTED',
          createdAt: '2026-08-19T12:00:00Z',
          lastError: 'S3 down',
        },
      ],
      counts: { pending: 0, processing: 0, failed: 1, completed: 4 },
    })
    const wrapper = mount(PurgeOutboxAdminView)
    await flushPromises()
    expect(wrapper.text()).toContain('File de purge')
    expect(wrapper.text()).toContain('FAILED : 1')
    expect(wrapper.text()).toContain('COMPLETED : 4')
    expect(wrapper.text()).toContain('d1')
    expect(wrapper.text()).toContain('S3 down')
    expect(wrapper.text()).not.toContain('Forcer')
    expect(wrapper.text()).not.toContain('Relancer')
  })
})
