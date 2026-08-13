import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import SupervisionView from './SupervisionView.vue'
import { routes } from '@/app/routes'
import * as dossiersApi from '@/api/dossiers'
import * as dossierTypesApi from '@/api/dossierTypes'

vi.mock('@/stores/dossier', () => ({
  useDossierStore: () => ({
    dossier: { id: 'd1' },
    loadById: vi.fn().mockResolvedValue(undefined),
    loadByRef: vi.fn().mockResolvedValue(undefined),
  }),
}))

vi.mock('@/api/dossiers')
vi.mock('@/api/dossierTypes')

describe('SupervisionView', () => {
  beforeEach(() => {
    vi.mocked(dossiersApi.listDossiers).mockReset()
    vi.mocked(dossierTypesApi.listDossierTypes).mockReset()
    vi.mocked(dossiersApi.listDossiers).mockResolvedValue({
      content: [
        {
          id: 'd1',
          reference: 'DOS-FAC-1',
          label: 'Facture 1',
          dossierTypeCode: 'FAC',
          dossierTypeLabel: 'Facture',
          status: 'OUVERT',
          createdAt: '2026-08-01T00:00:00Z',
          updatedAt: '2026-08-02T00:00:00Z',
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
    vi.mocked(dossierTypesApi.listDossierTypes).mockResolvedValue([
      {
        id: 't1',
        tenantId: 'tenant_dev',
        code: 'FAC',
        label: 'Facture',
        workflowKey: 'wf',
        active: true,
        allowUnknownFields: false,
        fieldDefinitions: [],
      },
    ])
  })

  it('renders list without mock KPIs and keeps open form', async () => {
    setActivePinia(createPinia())
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/supervision')
    await router.isReady()
    const wrapper = mount(SupervisionView, { global: { plugins: [router] } })
    await flushPromises()
    expect(wrapper.text()).toContain('Supervision')
    expect(wrapper.text()).toContain('DOS-FAC-1')
    expect(wrapper.text()).toContain('Créer un dossier')
    expect(wrapper.text()).toContain('Ouverture rapide')
    expect(wrapper.text()).not.toContain('KPI')
    expect(wrapper.text()).not.toContain('SLA')
    expect(dossiersApi.listDossiers).toHaveBeenCalled()
  })

  it('hydrates filters from URL and syncs on filter submit', async () => {
    setActivePinia(createPinia())
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/supervision?status=OUVERT&dossierTypeCode=FAC&reference=DOS&page=0')
    await router.isReady()
    const wrapper = mount(SupervisionView, { global: { plugins: [router] } })
    await flushPromises()
    expect(dossiersApi.listDossiers).toHaveBeenCalledWith(
      expect.objectContaining({
        status: 'OUVERT',
        dossierTypeCode: 'FAC',
        reference: 'DOS',
      }),
    )

    await wrapper.find('input[placeholder="préfixe DOS-…"]').setValue('DOS-FAC')
    await wrapper.find('.supervision__filters').trigger('submit')
    await flushPromises()
    expect(router.currentRoute.value.query.reference).toBe('DOS-FAC')
  })

  it('navigates to dossier detail on row click', async () => {
    setActivePinia(createPinia())
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/supervision')
    await router.isReady()
    const push = vi.spyOn(router, 'push')
    const wrapper = mount(SupervisionView, { global: { plugins: [router] } })
    await flushPromises()
    await wrapper.find('.supervision__row').trigger('click')
    expect(push).toHaveBeenCalledWith('/dossiers/d1')
  })
})
