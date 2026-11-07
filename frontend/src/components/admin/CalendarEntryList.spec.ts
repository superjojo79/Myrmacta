import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import CalendarEntryList from './CalendarEntryList.vue'
import type { CalendarEntry, CalendarSource } from '@/api/tenantCalendar'

const day = (date: string, nature: CalendarEntry['nature'], label: string | null = null): CalendarEntry => ({
  start: date,
  end: date,
  nature,
  label,
})

const source = (entries: CalendarEntry[]): CalendarSource => ({
  id: 'src-1',
  name: 'Fériés',
  kind: 'ICS_FILE',
  maskedUrl: null,
  entryCount: entries.length,
  loadedAt: null,
  loadedBy: null,
  lastError: null,
  entries,
})

describe('CalendarEntryList (TODO §121, D-CAL-OVERRIDE)', () => {
  it('says so when there is nothing', () => {
    const wrapper = mount(CalendarEntryList, { props: { manual: [], sources: [] } })
    expect(wrapper.find('[data-testid="calendar-entries-empty"]').exists()).toBe(true)
    expect(wrapper.find('table').exists()).toBe(false)
  })

  it('sorts manual and source rows by start, shows periods with an arrow, and names the origin', () => {
    const wrapper = mount(CalendarEntryList, {
      props: {
        manual: [{ start: '2026-12-24', end: '2026-12-31', nature: 'CLOSED', label: 'Congé' }, day('2026-01-02', 'OPEN')],
        sources: [source([day('2026-11-11', 'CLOSED', 'Armistice')])],
      },
    })

    const cells = wrapper.findAll('tbody tr').map((row) => row.findAll('td').map((cell) => cell.text()))
    expect(cells.map((row) => row[0])).toEqual(['2026-01-02', '2026-11-11', '2026-12-24 → 2026-12-31'])
    expect(cells[0][3]).toBe('Saisie')
    expect(cells[1][3]).toBe('Fériés')
  })

  it('only manual rows can be removed, and the emitted index is the manual one', async () => {
    const wrapper = mount(CalendarEntryList, {
      props: {
        manual: [day('2026-12-24', 'CLOSED'), day('2026-01-02', 'CLOSED')],
        sources: [source([day('2026-06-01', 'CLOSED')])],
      },
    })
    expect(wrapper.findAll('[data-testid="entry-row-source"] [data-testid="entry-remove"]')).toHaveLength(0)

    await wrapper.findAll('[data-testid="entry-row-manual"]')[0].get('[data-testid="entry-remove"]').trigger('click')

    // la ligne triée en premier est le 2 janvier, deuxième saisie manuelle
    expect(wrapper.emitted('remove')).toEqual([[1]])
  })

  it('flags a source day covered by a forced working day, but not a manual closed day', () => {
    const wrapper = mount(CalendarEntryList, {
      props: {
        manual: [{ start: '2026-11-10', end: '2026-11-12', nature: 'OPEN', label: null }, day('2026-12-25', 'CLOSED')],
        sources: [source([day('2026-11-11', 'CLOSED'), day('2026-12-25', 'CLOSED')])],
      },
    })

    const flagged = wrapper.findAll('[data-testid="entry-row-source"]').map((row) => row.find('[data-testid="entry-overridden"]').exists())
    expect(flagged).toEqual([true, false])
  })
})
