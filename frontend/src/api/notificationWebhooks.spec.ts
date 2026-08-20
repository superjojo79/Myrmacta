import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import {
  createNotificationWebhook,
  deleteNotificationWebhook,
  listNotificationWebhooks,
  updateNotificationWebhook,
} from './notificationWebhooks'
import { asPage } from './page'

vi.mock('axios')

describe('notificationWebhooks api', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('lists with envelope params', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: asPage([]) })
    await listNotificationWebhooks({ page: 1, size: 20 })
    expect(axios.get).toHaveBeenCalledWith('/api/notification-webhooks', {
      params: { page: 1, size: 20 },
    })
  })

  it('creates updates and deletes', async () => {
    vi.mocked(axios.post).mockResolvedValue({ data: { id: 'w1' } })
    vi.mocked(axios.patch).mockResolvedValue({ data: { id: 'w1' } })
    vi.mocked(axios.delete).mockResolvedValue({})
    await createNotificationWebhook({ name: 'n', targetUrl: 'https://example.com', secret: 's' })
    await updateNotificationWebhook('w1', { enabled: false })
    await deleteNotificationWebhook('w1')
    expect(axios.post).toHaveBeenCalled()
    expect(axios.patch).toHaveBeenCalled()
    expect(axios.delete).toHaveBeenCalledWith('/api/notification-webhooks/w1')
  })
})
