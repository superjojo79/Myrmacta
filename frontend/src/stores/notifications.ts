import { defineStore } from 'pinia'
import { ref } from 'vue'
import { resolveApiBaseUrl } from '@/api/http'
import {
  getUnreadCount,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  type UserNotificationDto,
} from '@/api/notifications'

export type NotificationItem = {
  id: string
  title: string
  body: string
  createdAt: string
  read: boolean
  raw?: unknown
}

const BACKFILL_SIZE = 20

function fromDto(dto: UserNotificationDto): NotificationItem {
  const payload = dto.payload ?? {}
  return {
    id: dto.id,
    title: String(payload.title ?? 'Notification'),
    body: String(payload.body ?? ''),
    createdAt: dto.createdAt,
    read: dto.read,
    raw: dto,
  }
}

export const useNotificationsStore = defineStore('notifications', () => {
  const items = ref<NotificationItem[]>([])
  const connected = ref(false)
  const failed = ref(false)
  let source: EventSource | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let stopped = false
  let backfilled = false
  let nextPage = 0

  // TODO §66 F3 (Phase 10 adversarial review): server-truth count, not a client estimate derived
  // from however many items happen to be loaded — the requirements explicitly call for a count
  // consistent with the server, covering unread items beyond the last BACKFILL_SIZE loaded too.
  const unreadCount = ref(0)
  // History beyond the last BACKFILL_SIZE loaded was otherwise unreachable from the UI (F3).
  const hasMore = ref(false)

  function scheduleReconnect(): void {
    if (stopped || reconnectTimer) return
    failed.value = true
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      connect(true)
    }, 5000)
  }

  // D-NOTIF-SOURCE-VERITE (TODO §66): REST backfill hydrates history missed while disconnected
  // (the whole point of persisting user_notification) — SSE below only ever appends what arrives
  // live from this point on, it never carries history.
  async function backfill(): Promise<void> {
    if (backfilled) return
    backfilled = true
    try {
      const [page, count] = await Promise.all([
        listNotifications({ page: 0, size: BACKFILL_SIZE }),
        getUnreadCount(),
      ])
      // Merge, never replace: connect() opens the SSE stream right after firing this request
      // (not awaited), so a live event can already have been unshifted into `items` by the time
      // this resolves — `items.value = ...` here would silently drop it. Keep whatever arrived
      // live (it's newer than anything backfilled) and only append backfill rows not already
      // present.
      const existingIds = new Set(items.value.map((n) => n.id))
      const fresh = page.content.map(fromDto).filter((n) => !existingIds.has(n.id))
      items.value = [...items.value, ...fresh]
      // Same race as the items merge above, applied to the count: a live SSE item can already be
      // unshifted (and counted) before this server snapshot — possibly taken before that item's
      // row was committed — resolves. Never let it regress the badge below what's already
      // visibly unread; the server total still wins when it's higher (unread items beyond the
      // last BACKFILL_SIZE loaded here).
      unreadCount.value = Math.max(count, items.value.filter((n) => !n.read).length)
      nextPage = 1
      hasMore.value = page.totalPages > 1
    } catch {
      // Backfill failure is not fatal — SSE still delivers new events; a reload will retry.
      backfilled = false
    }
  }

  // F3: makes the >20-day / >20-item history actually reachable from the drawer instead of only
  // via a full page reload against a paginated backend endpoint that already supported it.
  async function loadMore(): Promise<void> {
    if (!hasMore.value) return
    try {
      const page = await listNotifications({ page: nextPage, size: BACKFILL_SIZE })
      const existingIds = new Set(items.value.map((n) => n.id))
      const fresh = page.content.map(fromDto).filter((n) => !existingIds.has(n.id))
      items.value = [...items.value, ...fresh]
      nextPage += 1
      hasMore.value = nextPage < page.totalPages
    } catch {
      // Fail-soft — the "load more" affordance just stays available to retry.
    }
  }

  function connect(force = false): void {
    void backfill()
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
        // A dropped connection (hourly SSE timeout, network blip) can miss events the relay
        // marks PROCESSED regardless of delivery — re-arm backfill so reconnecting catches up
        // via REST, merged (never replacing) exactly like the initial connect.
        backfilled = false
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
        const id = String(payload.id ?? crypto.randomUUID())
        if (items.value.some((n) => n.id === id)) {
          // Already present from the REST backfill (send() persists before the SSE push
          // reaches a connected tab) — never show the same notification twice.
          return
        }
        items.value.unshift({
          id,
          title,
          body,
          createdAt: String(payload.at ?? payload.createdAt ?? new Date().toISOString()),
          read: false,
          raw: payload,
        })
        unreadCount.value += 1
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

  async function markRead(id: string): Promise<void> {
    const item = items.value.find((n) => n.id === id)
    if (!item || item.read) return
    item.read = true
    unreadCount.value = Math.max(0, unreadCount.value - 1)
    try {
      await markNotificationRead(id)
    } catch {
      item.read = false
      unreadCount.value += 1
    }
  }

  async function markAllRead(): Promise<void> {
    // Guard on the server-truth count, not `previouslyUnread.length` — that would silently skip
    // the server call when every *loaded* item is already read but unread ones exist beyond the
    // last BACKFILL_SIZE fetched (exactly the gap F3 exists to close).
    if (unreadCount.value === 0) return
    const previouslyUnread = items.value.filter((n) => !n.read)
    const previousCount = unreadCount.value
    previouslyUnread.forEach((n) => {
      n.read = true
    })
    unreadCount.value = 0
    try {
      await markAllNotificationsRead()
    } catch {
      previouslyUnread.forEach((n) => {
        n.read = false
      })
      unreadCount.value = previousCount
    }
  }

  return {
    items,
    connected,
    failed,
    unreadCount,
    hasMore,
    connect,
    disconnect,
    markRead,
    markAllRead,
    loadMore,
  }
})
