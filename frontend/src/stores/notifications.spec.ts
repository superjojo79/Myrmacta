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
