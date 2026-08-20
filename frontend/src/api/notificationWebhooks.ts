import axios from 'axios'
import type { AdminListParams } from './dossierTypes'
import { type Page } from './page'

export interface NotificationWebhook {
  id: string
  tenantId: string
  name: string
  targetUrl: string
  secret: string | null
  secretConfigured: boolean
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export async function listNotificationWebhooks(
  params: AdminListParams = {},
): Promise<Page<NotificationWebhook>> {
  const query: Record<string, number> = {}
  if (params.page != null) query.page = params.page
  if (params.size != null) query.size = params.size
  const { data } = await axios.get<Page<NotificationWebhook>>('/api/notification-webhooks', {
    params: query,
  })
  return data
}

export async function createNotificationWebhook(payload: {
  name: string
  targetUrl: string
  secret?: string
  enabled?: boolean
}): Promise<NotificationWebhook> {
  const { data } = await axios.post<NotificationWebhook>('/api/notification-webhooks', payload)
  return data
}

export async function updateNotificationWebhook(
  id: string,
  payload: {
    name?: string
    targetUrl?: string
    secret?: string
    enabled?: boolean
  },
): Promise<NotificationWebhook> {
  const { data } = await axios.patch<NotificationWebhook>(`/api/notification-webhooks/${id}`, payload)
  return data
}

export async function deleteNotificationWebhook(id: string): Promise<void> {
  await axios.delete(`/api/notification-webhooks/${id}`)
}
