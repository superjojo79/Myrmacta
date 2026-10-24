import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import SupervisionView from '@/views/pilotage/SupervisionView.vue'
import ReportsView from '@/views/pilotage/RapportsView.vue'
import DraftsAdminView from '@/views/admin/DraftsAdminView.vue'
import { validateFieldDefinitions } from '@/dossier/validateFieldDefinitions'
import { relationLabel } from '@/components/dossier/relationLabels'
import { setLocale } from '@/i18n'
import { formatDate } from './format'

vi.mock('@/api/dossierTypes', () => ({ listAllDossierTypes: vi.fn().mockResolvedValue([]) }))
vi.mock('@/api/dossiers', () => ({ listDossiers: vi.fn().mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 }) }))
vi.mock('@/api/slaReport', () => ({ getSlaReport: vi.fn().mockResolvedValue({
  from: '2026-09-01T00:00:00Z', to: '2026-10-01T00:00:00Z', compliancePercent: 90, complianceTargetPercent: 95, overdueCount: 2,
  meanProcessingDays: 3, meanDeltaVsPreviousDays: null, medianProcessingDays: null, typeShares: [], stepDurationsHours: [], managerLoads: [], types: [] }),
  downloadSlaReportCsv: vi.fn() }))
vi.mock('@/api/operations', () => ({ getOperationalCounts: vi.fn().mockResolvedValue({ deadLetterJobCount: 0, unassignedOpenTaskCount: 1 }) }))
vi.mock('@/api/biExports', () => ({ listBiExports: vi.fn().mockResolvedValue({ content: [] }), downloadBiExport: vi.fn() }))
vi.mock('@/api/drafts', () => ({ listPendingDrafts: vi.fn().mockResolvedValue([]), listPublications: vi.fn().mockResolvedValue([]), publishDraft: vi.fn(), discardDraft: vi.fn() }))

const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:p(.*)*', component: { template: '<i/>' } }] })

/** TODO §90 (D7) — l'interface, les contrôles côté client et les formats suivent la langue choisie. */
describe('English UI (D7)', () => {
  afterEach(() => setLocale('fr'))

  it('supervision shows its filters, table headers and empty state in English', async () => {
    setActivePinia(createPinia())
    setLocale('en')
    const wrapper = mount(SupervisionView, { global: { plugins: [router] } })
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('Filter')
    expect(text).toContain('Reference')
    expect(text).toContain('No dossier')
    expect(text).toContain('Quick open')
    expect(text).not.toContain('Filtrer')
  })

  it('reports format the period with the English day-first date and use English units', async () => {
    setLocale('en')
    const wrapper = mount(ReportsView, { global: { plugins: [router] } })
    await flushPromises()
    expect(wrapper.text()).toContain('Period: 01/09/2026 → 01/10/2026')
    expect(wrapper.text()).toContain('90%')
    expect(wrapper.text()).toContain('3 d')
  })

  it('drafts admin shows its empty state in English', async () => {
    setLocale('en')
    const wrapper = mount(DraftsAdminView, { global: { plugins: [router] } })
    await flushPromises()
    expect(wrapper.get('[data-testid="drafts-empty"]').text()).toBe('No draft pending.')
  })

  it('client-side field validation speaks English', () => {
    setLocale('en')
    const errors = validateFieldDefinitions([{ name: 'a', label: '', type: 'SELECT', required: false, options: [] }] as never)
    expect(errors).toContain('The label of field “a” is required')
    expect(errors).toContain('The SELECT field “a” needs at least one option')
    const unique = validateFieldDefinitions([{ name: 'a', label: 'A', type: 'BOOLEAN', required: false, unique: true }] as never)
    expect(unique.join(' ')).toContain('cannot be unique')
  })

  it('relation labels and dates follow the language', () => {
    setLocale('en')
    expect(relationLabel('PARENT_CHILD', 'OUTGOING')).toBe('Child')
    expect(formatDate('2026-10-01T10:00:00Z', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' })).toBe('1 October 2026')
    setLocale('fr')
    expect(relationLabel('PARENT_CHILD', 'OUTGOING')).toBe('Enfant')
  })
})
