import axios from 'axios'
import type { Page } from './page'

export interface UserNotificationDto {
  id: string
  payload: Record<string, unknown>
  createdAt: string
  readAt: string | null
  read: boolean
}

export async function listNotifications(
  params: { page?: number; size?: number } = {},
): Promise<Page<UserNotificationDto>> {
  const { data } = await axios.get<Page<UserNotificationDto>>('/api/notifications', { params })
  return data
}

// TODO §66 F3 (Phase 10 adversarial review): server-truth unread count, not a client estimate
// derived from however many items happen to be loaded in the store.
export async function getUnreadCount(): Promise<number> {
  const { data } = await axios.get<{ count: number }>('/api/notifications/unread-count')
  return data.count
}

export async function markNotificationRead(id: string): Promise<void> {
  await axios.patch(`/api/notifications/${id}/read`)
}

export async function markAllNotificationsRead(): Promise<void> {
  await axios.post('/api/notifications/read-all')
}
