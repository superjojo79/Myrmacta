import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import TemplatesAdminView from './TemplatesAdminView.vue'
import ConnectorsAdminView from './ConnectorsAdminView.vue'
import WorkflowConfigsAdminView from './WorkflowConfigsAdminView.vue'
import DossierTypesAdminView from './DossierTypesAdminView.vue'
import * as adminApi from '@/api/admin'
import * as workflowsApi from '@/api/workflows'
import * as typesApi from '@/api/dossierTypes'

vi.mock('@/api/admin')
vi.mock('@/api/workflows')
vi.mock('@/api/dossierTypes')

describe('admin actions coverage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.stubGlobal('prompt', vi.fn().mockReturnValue('Renamed'))
    vi.mocked(adminApi.listDocumentTemplates).mockResolvedValue([
      {
        id: 'tpl1',
        templateKey: 'k',
        variantKey: null,
        locale: null,
        label: 'Old',
        version: 1,
        status: 'DRAFT',
        htmlContent: null,
        createdAt: '',
        updatedAt: '',
        updatedBy: null,
      },
    ])
    vi.mocked(adminApi.listConnectors).mockResolvedValue([
      {
        id: 'c1',
        tenantId: 't',
        name: 'Hook',
        connectorType: 'HTTP',
        enabled: true,
        config: {},
        secretConfigured: {},
      },
    ])
    vi.mocked(workflowsApi.listWorkflowTypeConfigs).mockResolvedValue([
      {
        id: '1',
        tenantId: 't',
        workflowKey: 'wf',
        label: 'WF',
        statusDefinitions: [],
        allowedDossierStatuses: [],
        onStartDossierStatus: null,
        observerUserIds: [],
      },
    ])
    vi.mocked(typesApi.listDossierTypes).mockResolvedValue([
      {
        id: '1',
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

  it('templates rename and create', async () => {
    vi.mocked(adminApi.createDocumentTemplate).mockResolvedValue({} as never)
    vi.mocked(adminApi.updateDocumentTemplate).mockResolvedValue({} as never)
    const wrapper = mount(TemplatesAdminView)
    await flushPromises()
    await wrapper.get('li button').trigger('click')
    await flushPromises()
    expect(adminApi.updateDocumentTemplate).toHaveBeenCalled()
    const inputs = wrapper.findAll('form input')
    await inputs[0].setValue('k2')
    await inputs[1].setValue('Label')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()
    expect(adminApi.createDocumentTemplate).toHaveBeenCalled()
  })

  it('connectors toggle remove create', async () => {
    vi.mocked(adminApi.createConnector).mockResolvedValue({} as never)
    vi.mocked(adminApi.updateConnector).mockResolvedValue({} as never)
    vi.mocked(adminApi.deleteConnector).mockResolvedValue(undefined)
    const wrapper = mount(ConnectorsAdminView)
    await flushPromises()
    const buttons = wrapper.findAll('li button')
    await buttons[0].trigger('click')
    await buttons[1].trigger('click')
    await flushPromises()
    expect(adminApi.updateConnector).toHaveBeenCalled()
    expect(adminApi.deleteConnector).toHaveBeenCalled()
    await wrapper.get('form input').setValue('N')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()
    expect(adminApi.createConnector).toHaveBeenCalled()
  })

  it('workflow configs rename create and types acl', async () => {
    vi.mocked(workflowsApi.createWorkflowTypeConfig).mockResolvedValue({} as never)
    vi.mocked(workflowsApi.updateWorkflowTypeConfig).mockResolvedValue({} as never)
    vi.mocked(typesApi.updateDossierType).mockResolvedValue({} as never)
    vi.mocked(typesApi.grantDossierTypeAcl).mockResolvedValue(undefined)
    vi.mocked(typesApi.revokeDossierTypeAcl).mockResolvedValue(undefined)
    const wf = mount(WorkflowConfigsAdminView)
    await flushPromises()
    await wf.get('li button').trigger('click')
    await flushPromises()
    expect(workflowsApi.updateWorkflowTypeConfig).toHaveBeenCalled()
    await wf.get('form input').setValue('wf2')
    await wf.findAll('form input')[1].setValue('Label')
    await wf.get('form').trigger('submit.prevent')
    await flushPromises()
    expect(workflowsApi.createWorkflowTypeConfig).toHaveBeenCalled()

    const types = mount(DossierTypesAdminView)
    await flushPromises()
    await types.get('td button').trigger('click')
    await flushPromises()
    expect(typesApi.updateDossierType).toHaveBeenCalled()
    const aclInputs = types.findAll('form')[1].findAll('input')
    await aclInputs[0].setValue('CLAIM')
    await aclInputs[1].setValue('u1')
    await types.findAll('form')[1].trigger('submit.prevent')
    await flushPromises()
    expect(typesApi.grantDossierTypeAcl).toHaveBeenCalled()
    await types.findAll('form')[1].findAll('button')[1].trigger('click')
    await flushPromises()
    expect(typesApi.revokeDossierTypeAcl).toHaveBeenCalled()
  })
})
