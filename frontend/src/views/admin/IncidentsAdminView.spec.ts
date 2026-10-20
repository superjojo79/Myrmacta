import { describe, expect, it, vi, beforeEach } from 'vitest'
import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils'
import IncidentsAdminView from './IncidentsAdminView.vue'
import * as incidentsApi from '@/api/incidents'
import type { TenantIncidentsPage } from '@/api/types'

vi.mock('@/api/incidents')

const page = (overrides: Partial<TenantIncidentsPage> = {}): TenantIncidentsPage => ({
  content: [
    {
      jobId: 'job-1',
      activityId: 'mailRefus',
      activityName: 'E-mail : refus',
      state: 'INCIDENT',
      since: '2026-10-18T18:51:26Z',
      retriesLeft: 0,
      message: 'is not a single e-mail address',
      linkId: 'link-1',
      dossierId: 'd-7',
      dossierReference: 'DOS-7',
      processDefinitionKey: 'autorisationRiverain',
    },
    {
      jobId: 'job-2',
      activityId: 'svc',
      activityName: 'Appel ERP',
      state: 'RETRYING',
      since: '2026-10-18T18:50:00Z',
      retriesLeft: 2,
      linkId: 'link-2',
      dossierId: 'd-8',
      dossierReference: 'DOS-8',
      processDefinitionKey: 'claim',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 2,
  totalPages: 1,
  truncated: false,
  ...overrides,
})

function mountView() {
  return mount(IncidentsAdminView, { global: { stubs: { RouterLink: RouterLinkStub } } })
}

describe('IncidentsAdminView (TODO §94)', () => {
  beforeEach(() => {
    vi.mocked(incidentsApi.listTenantIncidents).mockReset()
    vi.mocked(incidentsApi.retryIncident).mockReset()
  })

  it('liste les échecs du tenant avec un lien vers chaque dossier', async () => {
    vi.mocked(incidentsApi.listTenantIncidents).mockResolvedValue(page())
    const wrapper = mountView()
    await flushPromises()

    const rows = wrapper.findAll('[data-testid="incident-admin-row"]')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('DOS-7')
    expect(rows[0].text()).toContain('E-mail : refus')
    expect(rows[0].text()).toContain('is not a single e-mail address')
    expect(wrapper.findAllComponents(RouterLinkStub)[0].props('to')).toBe('/dossiers/d-7')
    expect(incidentsApi.listTenantIncidents).toHaveBeenCalledWith({ page: 0, size: 20 })
  })

  it('rejoue un incident puis recharge la liste', async () => {
    vi.mocked(incidentsApi.listTenantIncidents)
      .mockResolvedValueOnce(page())
      .mockResolvedValue(page({ content: [] }))
    vi.mocked(incidentsApi.retryIncident).mockResolvedValue(undefined)
    const wrapper = mountView()
    await flushPromises()

    const buttons = wrapper.findAll('[data-testid="incident-admin-retry"]')
    expect(buttons).toHaveLength(1)
    await buttons[0].trigger('click')
    await flushPromises()

    expect(incidentsApi.retryIncident).toHaveBeenCalledWith('link-1', 'job-1')
    expect(incidentsApi.listTenantIncidents).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('Aucune étape en échec')
  })

  it('revient à la page précédente quand le rejeu vide la dernière page', async () => {
    vi.mocked(incidentsApi.listTenantIncidents)
      .mockResolvedValueOnce(page({ page: 0, totalPages: 2 }))
      .mockResolvedValueOnce(page({ page: 1, totalPages: 2 }))
      .mockResolvedValueOnce(page({ content: [], page: 1, totalPages: 1 }))
      .mockResolvedValue(page({ page: 0, totalPages: 1 }))
    vi.mocked(incidentsApi.retryIncident).mockResolvedValue(undefined)
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="admin-pager-next"]').trigger('click')
    await flushPromises()

    await wrapper.findAll('[data-testid="incident-admin-retry"]')[0].trigger('click')
    await flushPromises()

    expect(vi.mocked(incidentsApi.listTenantIncidents).mock.lastCall?.[0]).toEqual({ page: 0, size: 20 })
    expect(wrapper.findAll('[data-testid="incident-admin-row"]')).toHaveLength(2)
  })

  it('traduit un 409 de rejeu', async () => {
    vi.mocked(incidentsApi.listTenantIncidents).mockResolvedValue(page())
    vi.mocked(incidentsApi.retryIncident).mockRejectedValue({ response: { status: 409, data: { detail: 'technical' } } })
    const wrapper = mountView()
    await flushPromises()
    await wrapper.findAll('[data-testid="incident-admin-retry"]')[0].trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('Cet incident a déjà été rejoué')
  })

  it('signale une liste tronquée et une erreur de chargement', async () => {
    vi.mocked(incidentsApi.listTenantIncidents).mockResolvedValue(page({ truncated: true }))
    const truncated = mountView()
    await flushPromises()
    expect(truncated.find('[data-testid="incident-admin-truncated"]').exists()).toBe(true)

    vi.mocked(incidentsApi.listTenantIncidents).mockRejectedValue({
      response: { status: 403, data: { detail: 'denied' } },
    })
    const failed = mountView()
    await flushPromises()
    expect(failed.find('[role="alert"]').text()).toContain('denied')
  })
})
