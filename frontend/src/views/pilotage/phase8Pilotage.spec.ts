import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import CreateDossierView from './CreateDossierView.vue'
import SupervisionView from './SupervisionView.vue'
import { routes } from '@/app/routes'
import * as typesApi from '@/api/dossierTypes'
import * as dossiersApi from '@/api/dossiers'
import { useDossierStore } from '@/stores/dossier'

vi.mock('@/api/dossierTypes')
vi.mock('@/api/dossiers')
vi.mock('@/api/workflows')
vi.mock('@/api/tasks')

describe('phase 8 pilotage edge cases', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
    vi.mocked(typesApi.listDossierTypes).mockResolvedValue([
      {
        id: 't1',
        tenantId: 't',
        code: 'CLAIM',
        label: 'Sinistre',
        workflowKey: 'wf',
        active: true,
        allowUnknownFields: false,
        fieldDefinitions: [],
      },
    ])
  })

  it('CreateDossier shows validation when label missing', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/dossiers/new')
    await router.isReady()
    const wrapper = mount(CreateDossierView, { global: { plugins: [router] } })
    await flushPromises()
    await wrapper.get('#dossier-label').setValue('')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()
    expect(wrapper.text()).toContain('Type et libellé requis')
    expect(dossiersApi.createDossier).not.toHaveBeenCalled()
  })

  it('CreateDossier surfaces ProblemDetail on API error', async () => {
    vi.mocked(dossiersApi.createDossier).mockRejectedValue({
      response: { data: { detail: 'type inactive', status: 409 } },
    })
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/dossiers/new')
    await router.isReady()
    const wrapper = mount(CreateDossierView, { global: { plugins: [router] } })
    await flushPromises()
    await wrapper.get('#dossier-label').setValue('X')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()
    expect(wrapper.text()).toContain('type inactive')
  })

  it('Supervision opens by UUID', async () => {
    const store = useDossierStore()
    store.loadById = vi.fn().mockImplementation(async () => {
      store.dossier = { id: '11111111-1111-1111-1111-111111111111' } as never
    })
    store.loadByRef = vi.fn()
    const router = createRouter({ history: createMemoryHistory(), routes })
    const push = vi.spyOn(router, 'push')
    await router.push('/supervision')
    await router.isReady()
    const wrapper = mount(SupervisionView, { global: { plugins: [router] } })
    await wrapper.get('input').setValue('11111111-1111-1111-1111-111111111111')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()
    expect(store.loadById).toHaveBeenCalled()
    expect(push).toHaveBeenCalledWith('/dossiers/11111111-1111-1111-1111-111111111111')
  })
})
