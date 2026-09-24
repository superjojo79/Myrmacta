import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { mount, flushPromises } from '@vue/test-utils'
import PrimeVue from 'primevue/config'
import Aura from '@primevue/themes/aura'
import NotificationDrawer from './NotificationDrawer.vue'
import { useNotificationsStore } from '@/stores/notifications'
import * as notificationsApi from '@/api/notifications'
import { asPage } from '@/api/page'

vi.mock('@/api/notifications')

describe('NotificationDrawer', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
    vi.mocked(notificationsApi.listNotifications).mockResolvedValue(asPage([]))
    vi.mocked(notificationsApi.markNotificationRead).mockResolvedValue(undefined)
    vi.mocked(notificationsApi.markAllNotificationsRead).mockResolvedValue(undefined)
    vi.mocked(notificationsApi.getUnreadCount).mockResolvedValue(0)
    vi.stubGlobal(
      'EventSource',
      class {
        onopen: ((ev: Event) => void) | null = null
        onerror: ((ev: Event) => void) | null = null
        onmessage: ((ev: MessageEvent) => void) | null = null
        close = vi.fn()
        addEventListener = vi.fn()
        constructor() {
          queueMicrotask(() => this.onopen?.(new Event('open')))
        }
      },
    )
  })

  function mountDrawer() {
    return mount(NotificationDrawer, {
      global: { plugins: [[PrimeVue, { theme: { preset: Aura } }]] },
    })
  }

  it('backfills from the server on mount', async () => {
    vi.mocked(notificationsApi.listNotifications).mockResolvedValue(
      asPage([{ id: '1', payload: { title: 'Hi', body: 'body' }, createdAt: '', readAt: null, read: false }]),
    )
    mountDrawer()
    await flushPromises()

    expect(notificationsApi.listNotifications).toHaveBeenCalledWith({ page: 0, size: 20 })
  })

  it('opens the popover and marks a notification read', async () => {
    vi.mocked(notificationsApi.listNotifications).mockResolvedValue(
      asPage([{ id: '1', payload: { title: 'Hi', body: 'body' }, createdAt: '', readAt: null, read: false }]),
    )
    const wrapper = mountDrawer()
    await flushPromises()

    await wrapper.get('[data-testid="notification-bell"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Hi')

    await wrapper.get('[data-testid="notification-item"]').trigger('click')
    await flushPromises()

    const store = useNotificationsStore()
    expect(store.items[0]?.read).toBe(true)
    expect(notificationsApi.markNotificationRead).toHaveBeenCalledWith('1')
  })

  it('shows an empty state when there are no notifications', async () => {
    const wrapper = mountDrawer()
    await flushPromises()

    await wrapper.get('[data-testid="notification-bell"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="notification-empty"]').text()).toContain('Aucune notification')
  })

  it('mark-all-read button is disabled when nothing is unread', async () => {
    const wrapper = mountDrawer()
    await flushPromises()
    await wrapper.get('[data-testid="notification-bell"]').trigger('click')
    await flushPromises()

    const button = wrapper.get('[data-testid="mark-all-read"]')
    expect(button.attributes('disabled')).toBeDefined()
  })

  it('shows a load-more affordance when more history exists, and fetches the next page', async () => {
    // F3 — history beyond BACKFILL_SIZE was previously unreachable from the drawer.
    vi.mocked(notificationsApi.listNotifications)
      .mockResolvedValueOnce(
        asPage([{ id: '1', payload: { title: 'Recent', body: 'x' }, createdAt: 't', readAt: null, read: false }], {
          totalPages: 2,
          totalElements: 21,
        }),
      )
      .mockResolvedValueOnce(
        asPage([{ id: '2', payload: { title: 'Older', body: 'y' }, createdAt: 't0', readAt: null, read: false }], {
          page: 1,
          totalPages: 2,
          totalElements: 21,
        }),
      )
    const wrapper = mountDrawer()
    await flushPromises()
    await wrapper.get('[data-testid="notification-bell"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="notification-load-more"]').exists()).toBe(true)

    await wrapper.get('[data-testid="notification-load-more"]').trigger('click')
    await flushPromises()

    expect(notificationsApi.listNotifications).toHaveBeenCalledWith({ page: 1, size: 20 })
    expect(wrapper.text()).toContain('Older')
    expect(wrapper.find('[data-testid="notification-load-more"]').exists()).toBe(false)
  })

  it('renders portal notifications with their title and body (V-INT-3, TODO §68)', async () => {
    vi.mocked(notificationsApi.listNotifications).mockResolvedValue(
      asPage([
        {
          id: 'p1',
          payload: {
            type: 'PORTAL_TASK_SUBMITTED',
            title: "Réponse d'un tiers externe",
            body: 'Le tiers (a***@example.org) a répondu à la tâche qui lui était adressée.',
            dossierId: 'd1',
          },
          createdAt: '',
          readAt: null,
          read: false,
        },
      ]),
    )
    vi.mocked(notificationsApi.getUnreadCount).mockResolvedValue(1)
    const wrapper = mountDrawer()
    await flushPromises()
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(document.body.textContent + wrapper.text()).toContain("Réponse d'un tiers externe")
  })
})
