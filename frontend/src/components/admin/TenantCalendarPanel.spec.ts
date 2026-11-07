import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import TenantCalendarPanel from './TenantCalendarPanel.vue'
import * as calendarApi from '@/api/tenantCalendar'

vi.mock('@/api/tenantCalendar')

const stored: calendarApi.TenantCalendar = {
  tenantId: 'tenant_dev',
  zoneId: 'Europe/Brussels',
  weekend: 'SATURDAY_SUNDAY',
  entries: [{ start: '2026-11-11', end: '2026-11-11', nature: 'CLOSED', label: 'Armistice' }],
  sources: [],
  holidays: ['2026-11-11'],
  updatedAt: '2026-11-06T00:00:00Z',
  updatedBy: 'admin',
}

describe('TenantCalendarPanel (TODO §120, §121)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(calendarApi.getTenantCalendar).mockResolvedValue(stored)
  })

  it('shows the stored calendar, adds a period and a forced working day, and saves them with the settings', async () => {
    vi.mocked(calendarApi.replaceTenantCalendar).mockImplementation(async (payload) => ({
      ...stored,
      entries: payload.entries,
    }))
    const wrapper = mount(TenantCalendarPanel)
    await flushPromises()
    expect((wrapper.get('[data-testid="calendar-zone"]').element as HTMLInputElement).value).toBe('Europe/Brussels')
    expect(wrapper.findAll('[data-testid="entry-row-manual"]')).toHaveLength(1)

    await wrapper.get('[data-testid="entry-start"]').setValue('2026-12-24')
    await wrapper.get('[data-testid="entry-end"]').setValue('2026-12-31')
    await wrapper.get('[data-testid="entry-label"]').setValue('Congé collectif')
    await wrapper.get('[data-testid="entry-add"]').trigger('click')
    await wrapper.get('[data-testid="entry-start"]').setValue('2026-11-14')
    await wrapper.get('[data-testid="entry-nature"]').setValue('OPEN')
    await wrapper.get('[data-testid="entry-add"]').trigger('click')
    expect(wrapper.findAll('[data-testid="entry-row-manual"]')).toHaveLength(3)

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(calendarApi.replaceTenantCalendar).toHaveBeenCalledWith({
      zoneId: 'Europe/Brussels',
      weekend: 'SATURDAY_SUNDAY',
      entries: [
        { start: '2026-11-11', end: '2026-11-11', nature: 'CLOSED', label: 'Armistice' },
        { start: '2026-12-24', end: '2026-12-31', nature: 'CLOSED', label: 'Congé collectif' },
        { start: '2026-11-14', end: '2026-11-14', nature: 'OPEN', label: null },
      ],
    })
    expect(wrapper.find('[data-testid="calendar-saved"]').exists()).toBe(true)
  })

  it('removes a manual entry before saving', async () => {
    vi.mocked(calendarApi.replaceTenantCalendar).mockImplementation(async (payload) => ({ ...stored, entries: payload.entries }))
    const wrapper = mount(TenantCalendarPanel)
    await flushPromises()

    await wrapper.get('[data-testid="entry-remove"]').trigger('click')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(calendarApi.replaceTenantCalendar).toHaveBeenCalledWith(expect.objectContaining({ entries: [] }))
  })

  it('shows the load error', async () => {
    vi.mocked(calendarApi.getTenantCalendar).mockRejectedValue(new Error('boom'))
    const wrapper = mount(TenantCalendarPanel)
    await flushPromises()
    expect(wrapper.get('[data-testid="calendar-error"]').text()).not.toBe('')
  })

  it('keeps the unsaved manual entries when the Belgian source is loaded', async () => {
    const source: calendarApi.CalendarSource = {
      id: 'src-1',
      name: 'Fériés fédéraux belges',
      kind: 'BE_FEDERAL',
      maskedUrl: null,
      entryCount: 20,
      loadedAt: '2026-11-07T10:00:00Z',
      loadedBy: 'admin',
      lastError: null,
      entries: [{ start: '2026-12-25', end: '2026-12-25', nature: 'CLOSED', label: 'Noël' }],
    }
    vi.mocked(calendarApi.addBelgianFederalHolidays).mockResolvedValue({ ...stored, sources: [source] })
    const wrapper = mount(TenantCalendarPanel)
    await flushPromises()
    await wrapper.get('[data-testid="entry-start"]').setValue('2026-12-24')
    await wrapper.get('[data-testid="entry-add"]').trigger('click')

    await wrapper.get('[data-testid="calendar-belgian"]').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('[data-testid="entry-row-manual"]')).toHaveLength(2)
    expect(wrapper.findAll('[data-testid="entry-row-source"]')).toHaveLength(1)
    expect(wrapper.findAll('[data-testid="calendar-source"]')).toHaveLength(1)
  })
})
