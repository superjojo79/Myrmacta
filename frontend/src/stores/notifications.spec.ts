import { describe, expect, it, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useNotificationsStore } from './notifications'
import * as notificationsApi from '@/api/notifications'
import { asPage } from '@/api/page'

vi.mock('@/api/notifications')

class FakeEventSource {
  listeners = new Map<string, (event: MessageEvent) => void>()
  onmessage: ((event: MessageEvent) => void) | null = null
  onopen: (() => void) | null = null
  onerror: (() => void) | null = null
  close = vi.fn()
  constructor() {
    FakeEventSource.latest = this
  }
  addEventListener(type: string, listener: (event: MessageEvent) => void) {
    this.listeners.set(type, listener)
  }
  static latest: FakeEventSource | null = null
}

describe('notifications store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
    vi.mocked(notificationsApi.listNotifications).mockResolvedValue(asPage([]))
    vi.mocked(notificationsApi.markNotificationRead).mockResolvedValue(undefined)
    vi.mocked(notificationsApi.markAllNotificationsRead).mockResolvedValue(undefined)
    vi.mocked(notificationsApi.getUnreadCount).mockResolvedValue(0)
  })

  it('backfills from REST on connect, before any SSE event arrives', async () => {
    // E2E-NOTIF-1 (unit-level counterpart) — the whole point of persistence: notifications
    // emitted while disconnected must still show up once the drawer is reopened.
    vi.mocked(notificationsApi.listNotifications).mockResolvedValue(
      asPage([
        { id: 'n1', payload: { title: 'Missed', body: 'while offline' }, createdAt: 't', readAt: null, read: false },
      ]),
    )
    vi.mocked(notificationsApi.getUnreadCount).mockResolvedValue(1)
    vi.stubGlobal('EventSource', FakeEventSource)

    const store = useNotificationsStore()
    store.connect()
    await Promise.resolve()
    await Promise.resolve()

    expect(store.items).toHaveLength(1)
    expect(store.items[0]?.title).toBe('Missed')
    expect(store.items[0]?.read).toBe(false)
    expect(notificationsApi.listNotifications).toHaveBeenCalledWith({ page: 0, size: 20 })
    // TODO §66 F3 — unreadCount comes from GET /api/notifications/unread-count, not from
    // counting whatever happens to be loaded.
    expect(store.unreadCount).toBe(1)
    vi.unstubAllGlobals()
  })

  it('does not regress the unread badge below what a live notification already added', async () => {
    // Same race class as the items merge below, applied to the count: the unread-count snapshot
    // can be requested before a live SSE item's row is reflected in it, and must never overwrite
    // a higher count already known from a live arrival.
    let resolveCount: (count: number) => void = () => {}
    vi.mocked(notificationsApi.getUnreadCount).mockReturnValue(
      new Promise((resolve) => {
        resolveCount = resolve
      }),
    )
    vi.stubGlobal('EventSource', FakeEventSource)

    const store = useNotificationsStore()
    store.connect()
    await Promise.resolve()

    FakeEventSource.latest?.listeners.get('notification')?.(
      new MessageEvent('notification', {
        data: JSON.stringify({ id: 'live-1', payload: { title: 'Live', body: 'x' } }),
      }),
    )
    expect(store.unreadCount).toBe(1)

    resolveCount(0) // stale server snapshot, taken before the live row was visible to it
    await Promise.resolve()
    await Promise.resolve()

    expect(store.unreadCount).toBe(1)
    vi.unstubAllGlobals()
  })

  it('does not lose a live notification that arrives before the backfill request resolves', async () => {
    // Regression: backfill() used to do `items.value = page.content.map(...)`, a full replace.
    // connect() fires backfill() without awaiting it, then opens the SSE stream immediately —
    // a live event can land and get unshifted into `items` before the backfill promise settles.
    // A full replace would silently discard it. Reproduced here by controlling resolution order
    // directly: resolve the backfill fetch only after a live SSE event has already been ingested.
    let resolveBackfill: (page: ReturnType<typeof asPage>) => void = () => {}
    vi.mocked(notificationsApi.listNotifications).mockReturnValue(
      new Promise((resolve) => {
        resolveBackfill = resolve
      }),
    )
    vi.stubGlobal('EventSource', FakeEventSource)

    const store = useNotificationsStore()
    store.connect()
    await Promise.resolve()

    FakeEventSource.latest?.listeners.get('notification')?.(
      new MessageEvent('notification', {
        data: JSON.stringify({ id: 'live-1', payload: { title: 'Live', body: 'arrived first' } }),
      }),
    )
    expect(store.items).toHaveLength(1)

    resolveBackfill(
      asPage([{ id: 'old-1', payload: { title: 'Old', body: 'from history' }, createdAt: 't', readAt: null, read: false }]),
    )
    await Promise.resolve()
    await Promise.resolve()

    expect(store.items.map((n) => n.id)).toEqual(expect.arrayContaining(['live-1', 'old-1']))
    expect(store.items).toHaveLength(2)
    vi.unstubAllGlobals()
  })

  it('does not duplicate a notification already present from backfill when it also arrives live', async () => {
    vi.mocked(notificationsApi.listNotifications).mockResolvedValue(
      asPage([{ id: 'n1', payload: { title: 'X', body: 'y' }, createdAt: 't', readAt: null, read: false }]),
    )
    vi.stubGlobal('EventSource', FakeEventSource)
    const store = useNotificationsStore()
    store.connect()
    await Promise.resolve()
    await Promise.resolve()

    FakeEventSource.latest?.listeners.get('notification')?.(
      new MessageEvent('notification', {
        data: JSON.stringify({ id: 'n1', payload: { title: 'X', body: 'y' } }),
      }),
    )

    expect(store.items).toHaveLength(1)
    vi.unstubAllGlobals()
  })

  it('re-backfills after an SSE reconnect, merging with what is already present', async () => {
    // F2 fix: a dropped connection (hourly SSE timeout, network blip) leaves a gap where the
    // relay marks events PROCESSED whether or not anyone was listening — only a fresh REST
    // backfill on reconnect recovers what was missed. Before the fix, `backfilled` stayed true
    // forever and connect(true) never re-fetched.
    vi.useFakeTimers()
    vi.mocked(notificationsApi.listNotifications)
      .mockResolvedValueOnce(
        asPage([{ id: 'n1', payload: { title: 'Old', body: 'x' }, createdAt: 't', readAt: null, read: false }]),
      )
      .mockResolvedValueOnce(
        asPage([
          { id: 'n1', payload: { title: 'Old', body: 'x' }, createdAt: 't', readAt: null, read: false },
          {
            id: 'n2',
            payload: { title: 'Missed during gap', body: 'y' },
            createdAt: 't2',
            readAt: null,
            read: false,
          },
        ]),
      )
    vi.stubGlobal('EventSource', FakeEventSource)

    const store = useNotificationsStore()
    store.connect()
    await Promise.resolve()
    await Promise.resolve()
    expect(store.items).toHaveLength(1)

    FakeEventSource.latest?.onerror?.()
    await vi.advanceTimersByTimeAsync(5000)
    await Promise.resolve()
    await Promise.resolve()

    expect(notificationsApi.listNotifications).toHaveBeenCalledTimes(2)
    expect(store.items.map((n) => n.id)).toEqual(expect.arrayContaining(['n1', 'n2']))
    expect(store.items).toHaveLength(2)
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('marks read optimistically then confirms with the server', async () => {
    const store = useNotificationsStore()
    store.items = [
      { id: '1', title: 'A', body: 'b', createdAt: '', read: false },
      { id: '2', title: 'B', body: 'b', createdAt: '', read: false },
    ]
    store.unreadCount = 2
    await store.markRead('1')
    expect(store.items[0].read).toBe(true)
    expect(store.unreadCount).toBe(1)
    expect(notificationsApi.markNotificationRead).toHaveBeenCalledWith('1')
    await store.markAllRead()
    expect(store.unreadCount).toBe(0)
    expect(notificationsApi.markAllNotificationsRead).toHaveBeenCalled()
  })

  it('rolls back the optimistic read state when the server call fails', async () => {
    vi.mocked(notificationsApi.markNotificationRead).mockRejectedValue(new Error('down'))
    const store = useNotificationsStore()
    store.items = [{ id: '1', title: 'A', body: 'b', createdAt: '', read: false }]
    store.unreadCount = 1

    await store.markRead('1')

    expect(store.items[0].read).toBe(false)
    expect(store.unreadCount).toBe(1)
  })

  it('rolls back the unread count when markAllRead fails on the server', async () => {
    vi.mocked(notificationsApi.markAllNotificationsRead).mockRejectedValue(new Error('down'))
    const store = useNotificationsStore()
    store.items = [{ id: '1', title: 'A', body: 'b', createdAt: '', read: false }]
    store.unreadCount = 1

    await store.markAllRead()

    expect(store.items[0].read).toBe(false)
    expect(store.unreadCount).toBe(1)
  })

  it('markAllRead still calls the server when unread items exist beyond what is loaded', async () => {
    // F3 fix: previously guarded on `previouslyUnread.length === 0` (loaded items only), which
    // silently skipped the server call whenever every *loaded* item was already read even though
    // the server-truth unreadCount showed more unread beyond the last page fetched.
    const store = useNotificationsStore()
    store.items = [{ id: '1', title: 'A', body: 'b', createdAt: '', read: true }]
    store.unreadCount = 3

    await store.markAllRead()

    expect(notificationsApi.markAllNotificationsRead).toHaveBeenCalled()
    expect(store.unreadCount).toBe(0)
  })

  it('reads title and body from a named notification event', async () => {
    vi.stubGlobal('EventSource', FakeEventSource)
    const store = useNotificationsStore()
    store.connect()
    await Promise.resolve()
    FakeEventSource.latest?.listeners.get('notification')?.(
      new MessageEvent('notification', {
        data: JSON.stringify({
          id: 'n1',
          at: '2026-08-01T00:00:00Z',
          payload: { title: 'SLA AT_RISK — DOS-1', body: 'Le dossier DOS-1 est passé au statut AT_RISK.' },
        }),
      }),
    )
    expect(store.items[0]?.title).toBe('SLA AT_RISK — DOS-1')
    expect(store.items[0]?.body).toContain('DOS-1')
    store.disconnect()
    vi.unstubAllGlobals()
  })

  it('fail-soft when EventSource construction throws', () => {
    vi.stubGlobal(
      'EventSource',
      class {
        constructor() {
          throw new Error('boom')
        }
      },
    )
    const store = useNotificationsStore()
    store.connect()
    expect(store.failed).toBe(true)
    expect(store.connected).toBe(false)
    vi.unstubAllGlobals()
  })

  it('fail-soft when the backfill request itself fails', async () => {
    vi.mocked(notificationsApi.listNotifications).mockRejectedValue(new Error('down'))
    vi.stubGlobal('EventSource', FakeEventSource)
    const store = useNotificationsStore()

    expect(() => store.connect()).not.toThrow()
    await Promise.resolve()
    await Promise.resolve()

    expect(store.items).toEqual([])
    vi.unstubAllGlobals()
  })
})
