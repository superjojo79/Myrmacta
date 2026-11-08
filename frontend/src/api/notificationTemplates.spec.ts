import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import {
  activateNotificationTemplate,
  archiveNotificationTemplate,
  createNotificationTemplate,
  getNotificationTemplate,
  listNotificationTemplates,
  newNotificationTemplateVersion,
  previewNotificationTemplate,
  updateNotificationTemplate,
} from './notificationTemplates'
import { asPage } from './page'

vi.mock('axios')

describe('notificationTemplates api', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('lists with page params only when given', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: asPage([]) })
    await listNotificationTemplates({ page: 1, size: 20 })
    expect(axios.get).toHaveBeenCalledWith('/api/notification-templates', { params: { page: 1, size: 20 } })
    await listNotificationTemplates()
    expect(axios.get).toHaveBeenLastCalledWith('/api/notification-templates', { params: {} })
  })

  it('reads one template', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: { id: 't1' } })
    expect(await getNotificationTemplate('t1')).toEqual({ id: 't1' })
    expect(axios.get).toHaveBeenCalledWith('/api/notification-templates/t1')
  })

  it('creates with POST and updates with PUT', async () => {
    vi.mocked(axios.post).mockResolvedValue({ data: { id: 't1' } })
    vi.mocked(axios.put).mockResolvedValue({ data: { id: 't1' } })
    const payload = { templateKey: 'k', label: 'L', subject: 'S', textBody: 'T', htmlBody: null }
    await createNotificationTemplate(payload)
    expect(axios.post).toHaveBeenCalledWith('/api/notification-templates', payload)
    await updateNotificationTemplate('t1', { label: 'L2', htmlBody: '' })
    expect(axios.put).toHaveBeenCalledWith('/api/notification-templates/t1', { label: 'L2', htmlBody: '' })
  })

  it('posts the lifecycle actions', async () => {
    vi.mocked(axios.post).mockResolvedValue({ data: { id: 't1' } })
    await activateNotificationTemplate('t1')
    expect(axios.post).toHaveBeenLastCalledWith('/api/notification-templates/t1/activate')
    await archiveNotificationTemplate('t1')
    expect(axios.post).toHaveBeenLastCalledWith('/api/notification-templates/t1/archive')
    await newNotificationTemplateVersion('t1')
    expect(axios.post).toHaveBeenLastCalledWith('/api/notification-templates/t1/new-version')
  })

  it('previews by id or by content', async () => {
    vi.mocked(axios.post).mockResolvedValue({ data: { subject: 's', textBody: 't', htmlBody: null } })
    const byId = { id: 't1', model: { a: 1 } }
    expect(await previewNotificationTemplate(byId)).toEqual({ subject: 's', textBody: 't', htmlBody: null })
    expect(axios.post).toHaveBeenCalledWith('/api/notification-templates/preview', byId)
    const byContent = { template: { subject: 's', textBody: 't' }, model: {} }
    await previewNotificationTemplate(byContent)
    expect(axios.post).toHaveBeenLastCalledWith('/api/notification-templates/preview', byContent)
  })
})
