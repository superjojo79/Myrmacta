import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import TemplatesAdminView from './TemplatesAdminView.vue'
import ConnectorsAdminView from './ConnectorsAdminView.vue'
import WorkflowConfigsAdminView from './WorkflowConfigsAdminView.vue'
import * as adminApi from '@/api/admin'
import * as workflowsApi from '@/api/workflows'

vi.mock('@/api/admin')
vi.mock('@/api/workflows')

describe('admin views smoke', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(adminApi.listDocumentTemplates).mockResolvedValue([])
    vi.mocked(adminApi.listConnectors).mockResolvedValue([
      {
        id: 'c1',
        tenantId: 't',
        name: 'Hook',
        connectorType: 'HTTP',
        enabled: true,
        config: { password: '***' },
        secretConfigured: { password: true },
      },
    ])
    vi.mocked(workflowsApi.listWorkflowTypeConfigs).mockResolvedValue([])
  })

  it('renders templates admin', async () => {
    const wrapper = mount(TemplatesAdminView)
    await flushPromises()
    expect(wrapper.text()).toContain('Templates documents')
  })

  it('renders connectors with secretConfigured', async () => {
    const wrapper = mount(ConnectorsAdminView)
    await flushPromises()
    expect(wrapper.text()).toContain('Hook')
    expect(wrapper.text()).toContain('password')
  })

  it('renders workflow configs admin', async () => {
    const wrapper = mount(WorkflowConfigsAdminView)
    await flushPromises()
    expect(wrapper.text()).toContain('Configurations workflow')
  })
})
