import { afterEach, describe, expect, it } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import CalendarImportPreview from './CalendarImportPreview.vue'
import type { SourcePreview } from '@/api/tenantCalendar'

const range = (start: string, end = start) => ({ start, end })

const preview = (over: Partial<SourcePreview> = {}): SourcePreview => ({
  sourceId: 'src-1',
  name: 'Écoles',
  kind: 'ICS_FILE',
  added: [range('2026-12-25'), range('2026-12-29', '2026-12-31')],
  removed: [range('2026-07-01', '2026-08-31')],
  masked: [range('2026-12-25')],
  ignored: { TIMED: 2, CANCELLED: 1, TRANSPARENT: 0 },
  sha256: 'abc',
  periodCount: 3,
  ...over,
})

let wrapper: VueWrapper | null = null

function show(props: { preview: SourcePreview; busy?: boolean; error?: string | null }) {
  wrapper = mount(CalendarImportPreview, { props, attachTo: document.body })
  return wrapper
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  document.body.innerHTML = ''
})

describe('CalendarImportPreview (TODO §121, D-CAL-ICS-PREVIEW)', () => {
  it('summarises what the import adds, removes, masks and ignores', () => {
    const dialog = show({ preview: preview() })

    expect(dialog.get('#calendar-import-title').text()).toContain('Écoles')
    expect(dialog.get('[data-testid="import-summary"]').text()).toContain('3')
    expect(dialog.get('[data-testid="import-added"]').text()).toContain('2026-12-25')
    expect(dialog.get('[data-testid="import-added"]').text()).toContain('2026-12-29 → 2026-12-31')
    expect(dialog.get('[data-testid="import-removed"]').text()).toContain('2026-07-01 → 2026-08-31')
    expect(dialog.get('[data-testid="import-masked"]').text()).toContain('resteront ouvrés')
    const ignored = dialog.get('[data-testid="import-ignored"]').text()
    expect(ignored).toContain('2 événement(s) horaire(s)')
    expect(ignored).toContain('1 événement(s) annulé(s)')
    expect(ignored).not.toContain('transparent')
  })

  it('hides the sections that are empty, and says so when nothing changes', () => {
    const dialog = show({ preview: preview({ added: [], removed: [], masked: [], ignored: {} }) })

    expect(dialog.find('[data-testid="import-nothing"]').exists()).toBe(true)
    expect(dialog.find('[data-testid="import-added"]').exists()).toBe(false)
    expect(dialog.find('[data-testid="import-removed"]').exists()).toBe(false)
    expect(dialog.find('[data-testid="import-masked"]').exists()).toBe(false)
    expect(dialog.find('[data-testid="import-ignored"]').exists()).toBe(false)
  })

  it('shows at most 30 ranges and counts the rest', () => {
    const days = Array.from({ length: 35 }, (_, i) => range(new Date(Date.UTC(2026, 0, i * 2 + 1)).toISOString().slice(0, 10)))
    const dialog = show({ preview: preview({ added: days }) })

    expect(dialog.findAll('[data-testid="import-added"] li')).toHaveLength(31)
    expect(dialog.get('[data-testid="import-added"]').text()).toContain('… et 5 autre(s)')
  })

  it('emits confirm and cancel, and disables both while busy', async () => {
    const dialog = show({ preview: preview() })
    await dialog.get('[data-testid="import-confirm"]').trigger('click')
    await dialog.get('[data-testid="import-cancel"]').trigger('click')
    expect(dialog.emitted('confirm')).toHaveLength(1)
    expect(dialog.emitted('cancel')).toHaveLength(1)

    await dialog.setProps({ busy: true })
    expect(dialog.get('[data-testid="import-confirm"]').attributes('disabled')).toBeDefined()
    expect(dialog.get('[data-testid="import-cancel"]').attributes('disabled')).toBeDefined()
  })

  it('shows an error as an alert', () => {
    const dialog = show({ preview: preview(), error: 'Le contenu a changé' })
    expect(dialog.get('[role="alert"]').text()).toBe('Le contenu a changé')
  })

  it('is a modal dialog that takes the focus, and Escape cancels it', async () => {
    const dialog = show({ preview: preview() })
    await dialog.vm.$nextTick()

    const root = dialog.get('[role="dialog"]')
    expect(root.attributes('aria-modal')).toBe('true')
    expect(root.attributes('aria-labelledby')).toBe('calendar-import-title')
    expect(document.activeElement).toBe(root.element)

    await root.trigger('keydown', { key: 'Escape' })
    expect(dialog.emitted('cancel')).toHaveLength(1)
  })

  it('keeps Tab and Shift+Tab inside the dialog', async () => {
    const dialog = show({ preview: preview() })
    await dialog.vm.$nextTick()
    const root = dialog.get('[role="dialog"]')
    const confirm = dialog.get('[data-testid="import-confirm"]').element as HTMLElement
    const cancel = dialog.get('[data-testid="import-cancel"]').element as HTMLElement

    cancel.focus()
    await root.trigger('keydown', { key: 'Tab' })
    expect(document.activeElement).toBe(confirm)

    await root.trigger('keydown', { key: 'Tab', shiftKey: true })
    expect(document.activeElement).toBe(cancel)

    ;(root.element as HTMLElement).focus()
    await root.trigger('keydown', { key: 'Tab', shiftKey: true })
    expect(document.activeElement).toBe(cancel)
  })

  it('gives the focus back to the element that opened it', async () => {
    const opener = document.createElement('button')
    document.body.appendChild(opener)
    opener.focus()
    const dialog = show({ preview: preview() })
    await dialog.vm.$nextTick()
    expect(document.activeElement).not.toBe(opener)

    dialog.unmount()
    wrapper = null

    expect(document.activeElement).toBe(opener)
  })
})
