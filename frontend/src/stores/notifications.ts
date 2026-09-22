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
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let stopped = false

  const unreadCount = computed(() => items.value.filter((n) => !n.read).length)

  function scheduleReconnect(): void {
    if (stopped || reconnectTimer) return
    failed.value = true
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      connect(true)
    }, 5000)
  }

  function connect(force = false): void {
    if (source && !force) return
    stopped = false
    failed.value = false
    const prefix = resolveApiBaseUrl()
    try {
      source?.close()
      source = new EventSource(`${prefix}/api/notifications/stream`, { withCredentials: true })
      source.onopen = () => {
        connected.value = true
        failed.value = false
      }
      source.onerror = () => {
        connected.value = false
        source?.close()
        source = null
        scheduleReconnect()
      }
      const ingest = (data: string) => {
        let payload: Record<string, unknown> = {}
        try {
          payload = JSON.parse(data) as Record<string, unknown>
        } catch {
          payload = { body: data }
        }
        const nested =
          payload.payload && typeof payload.payload === 'object'
            ? (payload.payload as Record<string, unknown>)
            : null
        const title = String(nested?.title ?? payload.title ?? 'Notification')
        const body = String(nested?.body ?? payload.body ?? payload.message ?? data)
        items.value.unshift({
          id: String(payload.id ?? crypto.randomUUID()),
          title,
          body,
          createdAt: String(payload.at ?? payload.createdAt ?? new Date().toISOString()),
          read: false,
          raw: payload,
        })
        if (items.value.length > 100) {
          items.value = items.value.slice(0, 100)
        }
      }
      source.onmessage = (event) => ingest(event.data)
      source.addEventListener('notification', (event) => ingest((event as MessageEvent).data))
    } catch {
      failed.value = true
      connected.value = false
      scheduleReconnect()
    }
  }

  function disconnect(): void {
    stopped = true
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
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
