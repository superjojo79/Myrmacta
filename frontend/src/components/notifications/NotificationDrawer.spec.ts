import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { mount, flushPromises } from '@vue/test-utils'
import NotificationDrawer from './NotificationDrawer.vue'
import { useNotificationsStore } from '@/stores/notifications'

describe('NotificationDrawer', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.stubGlobal(
      'EventSource',
      class {
        onopen: ((ev: Event) => void) | null = null
        onerror: ((ev: Event) => void) | null = null
        onmessage: ((ev: MessageEvent) => void) | null = null
        close = vi.fn()
        constructor() {
          queueMicrotask(() => this.onopen?.(new Event('open')))
        }
      },
    )
  })

  it('opens drawer and marks read', async () => {
    const store = useNotificationsStore()
    store.items = [{ id: '1', title: 'Hi', body: 'body', createdAt: '', read: false }]
    const wrapper = mount(NotificationDrawer)
    await flushPromises()
    await wrapper.get('.notif__bell').trigger('click')
    expect(wrapper.text()).toContain('Hi')
    await wrapper.get('.notif__drawer li button').trigger('click')
    expect(store.items[0].read).toBe(true)
  })
})
