import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import WorkflowConfigsAdminView from './WorkflowConfigsAdminView.vue'
import * as workflowsApi from '@/api/workflows'
import { routes } from '@/app/routes'
import { asPage } from '@/api/page'

vi.mock('@/api/workflows')

describe('WorkflowConfigsAdminView — editor link', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(workflowsApi.listWorkflowTypeConfigs).mockResolvedValue(
      asPage([
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
      ]),
    )
  })

  it('links "Éditer" per config to /admin/bpmn-editor?processDefinitionKey=<workflowKey>', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/admin/workflow-types')
    await router.isReady()
    const wrapper = mount(WorkflowConfigsAdminView, { global: { plugins: [router] } })
    await flushPromises()

    const editLink = wrapper.get('li a.workflow-config-edit-link')
    expect(editLink.attributes('href')).toBe('/admin/bpmn-editor?processDefinitionKey=wf')
  })
})
