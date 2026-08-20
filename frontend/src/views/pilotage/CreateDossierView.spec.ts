import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import CreateDossierView from './CreateDossierView.vue'
import { routes } from '@/app/routes'
import * as typesApi from '@/api/dossierTypes'
import * as dossiersApi from '@/api/dossiers'

vi.mock('@/api/dossierTypes')
vi.mock('@/api/dossiers')
vi.mock('@/api/workflows')
vi.mock('@/api/tasks')
vi.mock('@/api/elements')

describe('CreateDossierView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
    vi.mocked(typesApi.listAllDossierTypes).mockResolvedValue([
      {
        id: 't1',
        tenantId: 't',
        code: 'CLAIM',
        label: 'Sinistre',
        workflowKey: 'wf',
        active: true,
        allowUnknownFields: false,
        fieldDefinitions: [
          {
            name: 'priority',
            label: 'Priority',
            type: 'STRING',
            required: true,
            defaultValue: 'NORMAL',
          },
        ],
      },
    ])
  })

  it('creates dossier and navigates', async () => {
    vi.mocked(dossiersApi.createDossier).mockResolvedValue({
      id: 'd1',
      tenantId: 't',
      reference: 'DOS-1',
      dossierTypeCode: 'CLAIM',
      label: 'Claim',
      status: 'DRAFT',
      fields: {},
      currentWorkflowLinkId: null,
      version: 0,
      createdBy: 'u',
      createdAt: '',
      updatedBy: 'u',
      updatedAt: '',
      closedBy: null,
      closedAt: null,
    })
    const router = createRouter({ history: createMemoryHistory(), routes })
    const push = vi.spyOn(router, 'push')
    await router.push('/dossiers/new')
    await router.isReady()
    const wrapper = mount(CreateDossierView, { global: { plugins: [router] } })
    await flushPromises()
    await wrapper.get('#dossier-label').setValue('Claim')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()
    expect(dossiersApi.createDossier).toHaveBeenCalled()
    expect(push).toHaveBeenCalledWith('/dossiers/d1')
  })
})
