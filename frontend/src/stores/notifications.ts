import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { resolveApiBaseUrl } from '@/api/http'

export type NotificationItem = {
  id: string
  title: string
  body: string
  createdAt: string
  read: boolean
  raw?: unknown
}

export const useNotificationsStore = defineStore('notifications', () => {
  const items = ref<NotificationItem[]>([])
  const connected = ref(false)
  const failed = ref(false)
  let source: EventSource | null = null

  const unreadCount = computed(() => items.value.filter((n) => !n.read).length)

  function connect(): void {
    if (source) return
    failed.value = false
    const prefix = resolveApiBaseUrl()
    try {
      source = new EventSource(`${prefix}/api/notifications/stream`, { withCredentials: true })
      source.onopen = () => {
        connected.value = true
        failed.value = false
      }
      source.onerror = () => {
        connected.value = false
        failed.value = true
        source?.close()
        source = null
      }
      source.onmessage = (event) => {
        let payload: Record<string, unknown> = {}
        try {
          payload = JSON.parse(event.data) as Record<string, unknown>
        } catch {
          payload = { body: event.data }
        }
        items.value.unshift({
          id: String(payload.id ?? crypto.randomUUID()),
          title: String(payload.title ?? 'Notification'),
          body: String(payload.body ?? payload.message ?? event.data),
          createdAt: String(payload.createdAt ?? new Date().toISOString()),
          read: false,
          raw: payload,
        })
      }
    } catch {
      failed.value = true
      connected.value = false
    }
  }

  function disconnect(): void {
    source?.close()
    source = null
    connected.value = false
  }

  function markRead(id: string): void {
    const item = items.value.find((n) => n.id === id)
    if (item) item.read = true
  }

  function markAllRead(): void {
    items.value.forEach((n) => {
      n.read = true
    })
  }

  return {
    items,
    connected,
    failed,
    unreadCount,
    connect,
    disconnect,
    markRead,
    markAllRead,
  }
})
