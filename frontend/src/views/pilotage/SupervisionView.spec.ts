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
    vi.mocked(dossierTypesApi.listAllDossierTypes).mockReset()
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
    vi.mocked(dossierTypesApi.listAllDossierTypes).mockResolvedValue([
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

  describe('filtres sur champs cherchables (TODO §81, D2)', () => {
    beforeEach(() => {
      vi.mocked(dossierTypesApi.listAllDossierTypes).mockResolvedValue([
        {
          id: 't1',
          tenantId: 'tenant_dev',
          code: 'SIN',
          label: 'Sinistre',
          workflowKey: 'wf',
          active: true,
          allowUnknownFields: false,
          fieldDefinitions: [
            { name: 'montant', label: 'Montant', type: 'NUMBER', required: false, searchable: true },
            { name: 'note', label: 'Note', type: 'STRING', required: false },
            { name: 'iban', label: 'IBAN', type: 'STRING', required: false, sensitive: true },
          ],
        },
      ])
    })

    it('shows inputs only for the searchable fields of the selected type, hydrates them from the URL and sends them', async () => {
      setActivePinia(createPinia())
      const router = createRouter({ history: createMemoryHistory(), routes })
      await router.push('/supervision?dossierTypeCode=SIN&f.montant.gte=5000')
      await router.isReady()
      const wrapper = mount(SupervisionView, { global: { plugins: [router] } })
      await flushPromises()
      expect(wrapper.find('[data-testid="field-filter-montant.gte"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="field-filter-montant.lte"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="field-filter-note"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="field-filter-iban"]').exists()).toBe(false)
      expect((wrapper.find('[data-testid="field-filter-montant.gte"]').element as HTMLInputElement).value).toBe('5000')
      expect(dossiersApi.listDossiers).toHaveBeenCalledWith(
        expect.objectContaining({ dossierTypeCode: 'SIN', fieldFilters: { 'montant.gte': '5000' } }),
      )

      await wrapper.find('[data-testid="field-filter-montant.lte"]').setValue('9000')
      await wrapper.find('.supervision__filters').trigger('submit')
      await flushPromises()
      expect(router.currentRoute.value.query['f.montant.lte']).toBe('9000')
      expect(dossiersApi.listDossiers).toHaveBeenLastCalledWith(
        expect.objectContaining({ fieldFilters: { 'montant.gte': '5000', 'montant.lte': '9000' } }),
      )
    })

    it('ignores f.* parameters when no type is selected (the server would refuse them)', async () => {
      setActivePinia(createPinia())
      const router = createRouter({ history: createMemoryHistory(), routes })
      await router.push('/supervision?f.montant.gte=5000')
      await router.isReady()
      mount(SupervisionView, { global: { plugins: [router] } })
      await flushPromises()
      expect(dossiersApi.listDossiers).toHaveBeenCalledWith(expect.objectContaining({ fieldFilters: undefined }))
    })
  })
})
