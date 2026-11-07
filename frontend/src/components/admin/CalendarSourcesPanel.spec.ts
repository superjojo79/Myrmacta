import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import CalendarSourcesPanel from './CalendarSourcesPanel.vue'
import * as calendarApi from '@/api/tenantCalendar'

vi.mock('@/api/tenantCalendar')

const belgian: calendarApi.CalendarSource = {
  id: 'src-1',
  name: 'Fériés fédéraux belges',
  kind: 'BE_FEDERAL',
  maskedUrl: null,
  entryCount: 20,
  loadedAt: '2026-11-07T10:00:00Z',
  loadedBy: 'admin',
  lastError: null,
  entries: [],
}

describe('CalendarSourcesPanel (TODO §121, D-CAL-SOURCE, D-CAL-BE-SOURCE)', () => {
  beforeEach(() => vi.resetAllMocks())

  it('lists the sources with their kind and size, or says there is none', () => {
    const empty = mount(CalendarSourcesPanel, { props: { sources: [] } })
    expect(empty.find('[data-testid="calendar-sources-empty"]').exists()).toBe(true)

    const filled = mount(CalendarSourcesPanel, { props: { sources: [belgian] } })
    const text = filled.get('[data-testid="calendar-source"]').text()
    expect(text).toContain('Fériés fédéraux belges')
    expect(text).toContain('20 période(s)')
    expect(text).toContain('admin')
  })

  it('shows the last load error of a source', () => {
    const wrapper = mount(CalendarSourcesPanel, { props: { sources: [{ ...belgian, lastError: 'fichier illisible' }] } })
    expect(wrapper.get('[data-testid="source-error"]').text()).toBe('fichier illisible')
  })

  it('loads the Belgian holidays and hands the sources back', async () => {
    vi.mocked(calendarApi.addBelgianFederalHolidays).mockResolvedValue({
      tenantId: 't',
      zoneId: 'UTC',
      weekend: 'SATURDAY_SUNDAY',
      entries: [],
      sources: [belgian],
      holidays: [],
      updatedAt: null,
      updatedBy: null,
    })
    const wrapper = mount(CalendarSourcesPanel, { props: { sources: [] } })

    await wrapper.get('[data-testid="calendar-belgian"]').trigger('click')
    await flushPromises()

    expect(wrapper.emitted('changed')).toEqual([[[belgian]]])
  })

  it('shows the error and emits nothing when the load fails', async () => {
    vi.mocked(calendarApi.addBelgianFederalHolidays).mockRejectedValue(new Error('boom'))
    const wrapper = mount(CalendarSourcesPanel, { props: { sources: [] } })

    await wrapper.get('[data-testid="calendar-belgian"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="sources-error"]').text()).not.toBe('')
    expect(wrapper.emitted('changed')).toBeUndefined()
    expect(wrapper.get('[data-testid="calendar-belgian"]').attributes('disabled')).toBeUndefined()
  })
})
