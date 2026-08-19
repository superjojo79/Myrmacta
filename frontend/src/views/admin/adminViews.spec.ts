import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import TemplatesAdminView from './TemplatesAdminView.vue'
import ConnectorsAdminView from './ConnectorsAdminView.vue'
import WorkflowConfigsAdminView from './WorkflowConfigsAdminView.vue'
import * as adminApi from '@/api/admin'
import * as workflowsApi from '@/api/workflows'
import { routes } from '@/app/routes'
import { asPage } from '@/api/page'
import type { Component } from 'vue'

vi.mock('@/api/admin')
vi.mock('@/api/workflows')
vi.mock('@/api/documents', () => ({
  previewHtml: vi.fn().mockResolvedValue('<p>ok</p>'),
  previewPdf: vi.fn().mockResolvedValue(new Blob()),
}))

async function mountView(component: Component, path: string) {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(path)
  await router.isReady()
  return mount(component, { global: { plugins: [router] } })
}

describe('admin views smoke', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(adminApi.listDocumentTemplates).mockResolvedValue(asPage([]))
    vi.mocked(adminApi.listConnectors).mockResolvedValue(
      asPage([
        {
          id: 'c1',
          tenantId: 't',
          name: 'Hook',
          connectorType: 'HTTP',
          enabled: true,
          config: { password: '***' },
          secretConfigured: { password: true },
        },
      ]),
    )
    vi.mocked(adminApi.listConnectorDefinitions).mockResolvedValue([
      { type: 'HTTP', label: 'HTTP' },
    ])
    vi.mocked(workflowsApi.listWorkflowTypeConfigs).mockResolvedValue(asPage([]))
  })

  it('renders templates admin', async () => {
    const wrapper = await mountView(TemplatesAdminView, '/admin/templates')
    await flushPromises()
    expect(wrapper.text()).toContain('Templates documents')
    expect(wrapper.text()).toContain('Aperçu')
  })

  it('renders connectors with secretConfigured', async () => {
    const wrapper = await mountView(ConnectorsAdminView, '/admin/connectors')
    await flushPromises()
    expect(wrapper.text()).toContain('Hook')
    expect(wrapper.text()).toContain('password')
  })

  it('renders workflow configs admin', async () => {
    const wrapper = await mountView(WorkflowConfigsAdminView, '/admin/workflow-types')
    await flushPromises()
    expect(wrapper.text()).toContain('Configurations workflow')
  })
})
