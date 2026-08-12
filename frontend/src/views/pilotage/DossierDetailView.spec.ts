import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import DossierDetailView from './DossierDetailView.vue'
import { routes } from '@/app/routes'
import * as dossiersApi from '@/api/dossiers'
import * as typesApi from '@/api/dossierTypes'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/api/dossiers')
vi.mock('@/api/dossierTypes')
vi.mock('@/api/workflows', () => ({
  getWorkflowDiagram: vi.fn(),
  getWorkflowHistory: vi.fn().mockResolvedValue([]),
  getWorkflowTasks: vi.fn().mockResolvedValue([]),
  listWorkflowTypeConfigs: vi.fn().mockResolvedValue([]),
  reassignWorkflowTask: vi.fn(),
}))
vi.mock('@/api/tasks')
vi.mock('bpmn-js/lib/NavigatedViewer', () => ({
  default: class {
    importXML = vi.fn().mockResolvedValue(undefined)
    destroy = vi.fn()
    get = () => ({ zoom: vi.fn(), addMarker: vi.fn() })
  },
}))

describe('DossierDetailView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
    vi.mocked(dossiersApi.getDossierById).mockResolvedValue({
      id: 'd1',
      tenantId: 't',
      reference: 'DOS-1',
      dossierTypeCode: 'CLAIM',
      label: 'Claim A',
      status: 'DRAFT',
      fields: { priority: 'NORMAL' },
      currentWorkflowLinkId: null,
      version: 1,
      createdBy: 'u',
      createdAt: '',
      updatedBy: 'u',
      updatedAt: '',
      closedBy: null,
      closedAt: null,
    })
    vi.mocked(typesApi.getDossierType).mockResolvedValue({
      id: 't1',
      tenantId: 't',
      code: 'CLAIM',
      label: 'Sinistre',
      workflowKey: 'wf',
      active: true,
      allowUnknownFields: false,
      fieldDefinitions: [],
    })
    vi.mocked(dossiersApi.listDossierWorkflows).mockResolvedValue([])
    vi.mocked(dossiersApi.listDossierElements).mockResolvedValue([])
    vi.mocked(dossiersApi.updateDossierStatus).mockResolvedValue({
      id: 'd1',
      tenantId: 't',
      reference: 'DOS-1',
      dossierTypeCode: 'CLAIM',
      label: 'Claim A',
      status: 'OUVERT',
      fields: {},
      currentWorkflowLinkId: null,
      version: 2,
      createdBy: 'u',
      createdAt: '',
      updatedBy: 'u',
      updatedAt: '',
      closedBy: null,
      closedAt: null,
    })
  })

  it('loads dossier and patches status', async () => {
    const auth = useAuthStore()
    auth.user = { id: 'u', name: 'Ada', email: null, roles: ['TENANT_ADMIN'] }
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/dossiers/d1')
    await router.isReady()
    const wrapper = mount(DossierDetailView, { global: { plugins: [router] } })
    await flushPromises()
    expect(wrapper.text()).toContain('Claim A')
    await wrapper.get('.status-select select').setValue('OUVERT')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(dossiersApi.updateDossierStatus).toHaveBeenCalledWith('d1', 'OUVERT', 1)
  })
})
