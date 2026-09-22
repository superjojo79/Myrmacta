import { describe, expect, it, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useNotificationsStore } from './notifications'

describe('notifications store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('marks notifications as read locally', () => {
    const store = useNotificationsStore()
    store.items = [
      { id: '1', title: 'A', body: 'b', createdAt: '', read: false },
      { id: '2', title: 'B', body: 'b', createdAt: '', read: false },
    ]
    store.markRead('1')
    expect(store.items[0].read).toBe(true)
    expect(store.unreadCount).toBe(1)
    store.markAllRead()
    expect(store.unreadCount).toBe(0)
  })

  it('reads title and body from a named notification event', () => {
    class FakeEventSource {
      listeners = new Map<string, (event: MessageEvent) => void>()
      onmessage: ((event: MessageEvent) => void) | null = null
      onopen: (() => void) | null = null
      onerror: (() => void) | null = null
      constructor() {
        FakeEventSource.latest = this
      }
      addEventListener(type: string, listener: (event: MessageEvent) => void) {
        this.listeners.set(type, listener)
      }
      close() {}
      static latest: FakeEventSource | null = null
    }
    vi.stubGlobal('EventSource', FakeEventSource)
    const store = useNotificationsStore()
    store.connect()
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
})
