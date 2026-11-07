import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import CalendarSourcesPanel from './CalendarSourcesPanel.vue'
import * as calendarApi from '@/api/tenantCalendar'

vi.mock('@/api/tenantCalendar')

const source = (over: Partial<calendarApi.CalendarSource> = {}): calendarApi.CalendarSource => ({
  id: 'src-1',
  name: 'Écoles',
  kind: 'ICS_FILE',
  maskedUrl: null,
  entryCount: 12,
  loadedAt: '2026-11-07T10:00:00Z',
  loadedBy: 'admin',
  lastError: null,
  entries: [],
  ...over,
})

const belgian = source({ id: 'src-be', name: 'Fériés fédéraux belges', kind: 'BE_FEDERAL', entryCount: 20 })

const calendar = (sources: calendarApi.CalendarSource[]): calendarApi.TenantCalendar => ({
  tenantId: 't',
  zoneId: 'UTC',
  weekend: 'SATURDAY_SUNDAY',
  entries: [],
  sources,
  holidays: [],
  updatedAt: null,
  updatedBy: null,
})

const preview = (over: Partial<calendarApi.SourcePreview> = {}): calendarApi.SourcePreview => ({
  sourceId: 'src-new',
  name: 'Écoles',
  kind: 'ICS_FILE',
  added: [{ start: '2026-12-25', end: '2026-12-25' }],
  removed: [],
  masked: [],
  ignored: {},
  sha256: 'sha-1',
  periodCount: 1,
  ...over,
})

const ics = () => new File(['BEGIN:VCALENDAR'], 'ecoles.ics', { type: 'text/calendar' })

async function pickFile(wrapper: VueWrapper, testid: string, file: File) {
  const input = wrapper.get(`[data-testid="${testid}"]`)
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  await input.trigger('change')
}

function ensureProblem(status: number) {
  return Object.assign(new Error('failed'), { response: { status, data: { status, detail: 'server detail' } } })
}

describe('CalendarSourcesPanel (TODO §121, D-CAL-SOURCE, D-CAL-ICS-PREVIEW, D-CAL-BE-SOURCE)', () => {
  beforeEach(() => vi.resetAllMocks())

  it('lists the sources with their kind and size, or says there is none', () => {
    expect(mount(CalendarSourcesPanel, { props: { sources: [] } }).find('[data-testid="calendar-sources-empty"]').exists()).toBe(true)

    const text = mount(CalendarSourcesPanel, { props: { sources: [belgian] } }).get('[data-testid="calendar-source"]').text()
    expect(text).toContain('Fériés fédéraux belges')
    expect(text).toContain('20 période(s)')
    expect(text).toContain('admin')
  })

  it('shows the last load error of a source', () => {
    const wrapper = mount(CalendarSourcesPanel, { props: { sources: [source({ lastError: 'fichier illisible' })] } })
    expect(wrapper.get('[data-testid="source-error"]').text()).toBe('fichier illisible')
  })

  it('offers a reload only for file sources', () => {
    const wrapper = mount(CalendarSourcesPanel, { props: { sources: [source(), belgian] } })
    const rows = wrapper.findAll('[data-testid="calendar-source"]')
    expect(rows[0].find('[data-testid="source-reload"]').exists()).toBe(true)
    expect(rows[1].find('[data-testid="source-reload"]').exists()).toBe(false)
  })

  it('loads the Belgian holidays and hands the sources back', async () => {
    vi.mocked(calendarApi.addBelgianFederalHolidays).mockResolvedValue(calendar([belgian]))
    const wrapper = mount(CalendarSourcesPanel, { props: { sources: [] } })

    await wrapper.get('[data-testid="calendar-belgian"]').trigger('click')
    await flushPromises()

    expect(wrapper.emitted('changed')).toEqual([[[belgian]]])
  })

  it('shows the error and emits nothing when the Belgian load fails', async () => {
    vi.mocked(calendarApi.addBelgianFederalHolidays).mockRejectedValue(new Error('boom'))
    const wrapper = mount(CalendarSourcesPanel, { props: { sources: [] } })

    await wrapper.get('[data-testid="calendar-belgian"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="sources-error"]').text()).not.toBe('')
    expect(wrapper.emitted('changed')).toBeUndefined()
    expect(wrapper.get('[data-testid="calendar-belgian"]').attributes('disabled')).toBeUndefined()
  })

  it('previews a new ICS file, then creates the source with the preview sha', async () => {
    vi.mocked(calendarApi.previewNewSource).mockResolvedValue(preview())
    vi.mocked(calendarApi.createSource).mockResolvedValue(calendar([source({ id: 'src-new' })]))
    const wrapper = mount(CalendarSourcesPanel, { props: { sources: [] } })
    const file = ics()

    await wrapper.get('[data-testid="source-name"]').setValue('  Écoles  ')
    await pickFile(wrapper, 'source-file', file)
    await wrapper.get('[data-testid="source-preview"]').trigger('click')
    await flushPromises()

    expect(calendarApi.previewNewSource).toHaveBeenCalledWith('Écoles', file)
    expect(wrapper.find('[data-testid="calendar-import-preview"]').exists()).toBe(true)
    expect(calendarApi.createSource).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="import-confirm"]').trigger('click')
    await flushPromises()

    expect(calendarApi.createSource).toHaveBeenCalledWith('Écoles', file, 'sha-1')
    expect(wrapper.find('[data-testid="calendar-import-preview"]').exists()).toBe(false)
    expect(wrapper.emitted('changed')).toEqual([[[expect.objectContaining({ id: 'src-new' })]]])
    expect((wrapper.get('[data-testid="source-name"]').element as HTMLInputElement).value).toBe('')
  })

  it('asks for a name and a file before calling the server', async () => {
    const wrapper = mount(CalendarSourcesPanel, { props: { sources: [] } })

    await wrapper.get('[data-testid="source-preview"]').trigger('click')
    await wrapper.get('[data-testid="source-name"]').setValue('Écoles')
    await wrapper.get('[data-testid="source-preview"]').trigger('click')

    expect(wrapper.get('[data-testid="sources-error"]').text()).toContain('obligatoires')
    expect(calendarApi.previewNewSource).not.toHaveBeenCalled()
  })

  it('shows the server message when the preview is refused, and opens no dialog', async () => {
    vi.mocked(calendarApi.previewNewSource).mockRejectedValue(ensureProblem(409))
    const wrapper = mount(CalendarSourcesPanel, { props: { sources: [] } })
    await wrapper.get('[data-testid="source-name"]').setValue('Écoles')
    await pickFile(wrapper, 'source-file', ics())

    await wrapper.get('[data-testid="source-preview"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="sources-error"]').text()).toBe('server detail')
    expect(wrapper.find('[data-testid="calendar-import-preview"]').exists()).toBe(false)
  })

  it('tells to preview again on a 409 at confirmation, keeping the dialog open', async () => {
    vi.mocked(calendarApi.previewNewSource).mockResolvedValue(preview())
    vi.mocked(calendarApi.createSource).mockRejectedValue(ensureProblem(409))
    const wrapper = mount(CalendarSourcesPanel, { props: { sources: [] } })
    await wrapper.get('[data-testid="source-name"]').setValue('Écoles')
    await pickFile(wrapper, 'source-file', ics())
    await wrapper.get('[data-testid="source-preview"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="import-confirm"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="import-error"]').text()).toContain("relancez l'aperçu")
    expect(wrapper.emitted('changed')).toBeUndefined()
    expect(wrapper.get('[data-testid="import-confirm"]').attributes('disabled')).toBeUndefined()
  })

  it('cancelling the preview creates nothing', async () => {
    vi.mocked(calendarApi.previewNewSource).mockResolvedValue(preview())
    const wrapper = mount(CalendarSourcesPanel, { props: { sources: [] } })
    await wrapper.get('[data-testid="source-name"]').setValue('Écoles')
    await pickFile(wrapper, 'source-file', ics())
    await wrapper.get('[data-testid="source-preview"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="import-cancel"]').trigger('click')

    expect(wrapper.find('[data-testid="calendar-import-preview"]').exists()).toBe(false)
    expect(calendarApi.createSource).not.toHaveBeenCalled()
  })

  it('reloads a file source: new file, preview, then reload with the preview sha', async () => {
    vi.mocked(calendarApi.previewReload).mockResolvedValue(preview({ sourceId: 'src-1', sha256: 'sha-2' }))
    vi.mocked(calendarApi.reloadSource).mockResolvedValue(calendar([source({ entryCount: 5 })]))
    const wrapper = mount(CalendarSourcesPanel, { props: { sources: [source()] } })
    const file = ics()

    await wrapper.get('[data-testid="source-reload"]').trigger('click')
    await pickFile(wrapper, 'source-reload-file', file)
    await flushPromises()

    expect(calendarApi.previewReload).toHaveBeenCalledWith('src-1', file)
    await wrapper.get('[data-testid="import-confirm"]').trigger('click')
    await flushPromises()

    expect(calendarApi.reloadSource).toHaveBeenCalledWith('src-1', file, 'sha-2')
    expect(wrapper.emitted('changed')).toEqual([[[expect.objectContaining({ entryCount: 5 })]]])
  })

  it('deletes a source only after a confirmation, and hands the remaining ones back', async () => {
    vi.mocked(calendarApi.deleteSource).mockResolvedValue(undefined)
    const wrapper = mount(CalendarSourcesPanel, { props: { sources: [source(), belgian] } })

    await wrapper.findAll('[data-testid="source-delete"]')[0].trigger('click')
    expect(calendarApi.deleteSource).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="calendar-source"]').text()).toContain('Supprimer la source « Écoles »')

    await wrapper.get('[data-testid="source-delete-confirm"]').trigger('click')
    await flushPromises()

    expect(calendarApi.deleteSource).toHaveBeenCalledWith('src-1')
    expect(wrapper.emitted('changed')).toEqual([[[belgian]]])
  })

  it('cancelling the deletion deletes nothing', async () => {
    const wrapper = mount(CalendarSourcesPanel, { props: { sources: [source()] } })

    await wrapper.get('[data-testid="source-delete"]').trigger('click')
    await wrapper.get('[data-testid="source-delete-cancel"]').trigger('click')

    expect(calendarApi.deleteSource).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="source-delete"]').exists()).toBe(true)
  })

  it('shows the error when the deletion fails', async () => {
    vi.mocked(calendarApi.deleteSource).mockRejectedValue(ensureProblem(404))
    const wrapper = mount(CalendarSourcesPanel, { props: { sources: [source()] } })

    await wrapper.get('[data-testid="source-delete"]').trigger('click')
    await wrapper.get('[data-testid="source-delete-confirm"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="sources-error"]').text()).toBe('server detail')
    expect(wrapper.emitted('changed')).toBeUndefined()
  })
})
