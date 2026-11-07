import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import CalendarEntryForm from './CalendarEntryForm.vue'

async function fill(wrapper: ReturnType<typeof mount>, values: { start?: string; end?: string; nature?: string; label?: string }) {
  if (values.start !== undefined) await wrapper.get('[data-testid="entry-start"]').setValue(values.start)
  if (values.end !== undefined) await wrapper.get('[data-testid="entry-end"]').setValue(values.end)
  if (values.nature !== undefined) await wrapper.get('[data-testid="entry-nature"]').setValue(values.nature)
  if (values.label !== undefined) await wrapper.get('[data-testid="entry-label"]').setValue(values.label)
  await wrapper.get('[data-testid="entry-add"]').trigger('click')
}

describe('CalendarEntryForm (TODO §121, D-CAL-ENTRY)', () => {
  it('emits a single day when the end is left empty, and resets the form', async () => {
    const wrapper = mount(CalendarEntryForm)

    await fill(wrapper, { start: '2026-12-24', label: '  Veille de Noël  ' })

    expect(wrapper.emitted('add')).toEqual([[{ start: '2026-12-24', end: '2026-12-24', nature: 'CLOSED', label: 'Veille de Noël' }]])
    expect((wrapper.get('[data-testid="entry-start"]').element as HTMLInputElement).value).toBe('')
    expect(wrapper.find('[data-testid="entry-error"]').exists()).toBe(false)
  })

  it('emits a period with a forced working day and a blank label as null', async () => {
    const wrapper = mount(CalendarEntryForm)

    await fill(wrapper, { start: '2026-11-14', end: '2026-11-15', nature: 'OPEN', label: '   ' })

    expect(wrapper.emitted('add')).toEqual([[{ start: '2026-11-14', end: '2026-11-15', nature: 'OPEN', label: null }]])
  })

  it('refuses a missing start, an end before the start, a period over 366 days and a long label', async () => {
    const wrapper = mount(CalendarEntryForm)

    await fill(wrapper, {})
    expect(wrapper.get('[data-testid="entry-error"]').text()).toContain('obligatoire')

    await fill(wrapper, { start: '2026-12-31', end: '2026-12-24' })
    expect(wrapper.get('[data-testid="entry-error"]').text()).toContain('précède')

    await fill(wrapper, { start: '2026-01-01', end: '2027-01-02' })
    expect(wrapper.get('[data-testid="entry-error"]').text()).toContain('366')

    await fill(wrapper, { start: '2026-01-01', end: '2026-12-31', label: 'x'.repeat(121) })
    expect(wrapper.get('[data-testid="entry-error"]').text()).toContain('120')

    expect(wrapper.emitted('add')).toBeUndefined()
  })

  it('accepts a period of exactly 366 days', async () => {
    const wrapper = mount(CalendarEntryForm)

    await fill(wrapper, { start: '2024-01-01', end: '2024-12-31' })

    expect(wrapper.emitted('add')).toHaveLength(1)
  })

  it('the end cannot go before the start in the date picker', async () => {
    const wrapper = mount(CalendarEntryForm)
    await wrapper.get('[data-testid="entry-start"]').setValue('2026-12-24')
    expect(wrapper.get('[data-testid="entry-end"]').attributes('min')).toBe('2026-12-24')
  })
})
