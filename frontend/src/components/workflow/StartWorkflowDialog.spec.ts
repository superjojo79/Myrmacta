import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import StartWorkflowDialog from './StartWorkflowDialog.vue'

vi.mock('@/api/workflows', () => ({
  listAllWorkflowTypeConfigs: vi.fn().mockResolvedValue([
    {
      id: '1',
      tenantId: 't',
      workflowKey: 'other',
      label: 'Other',
      statusDefinitions: [],
      allowedDossierStatuses: [],
      onStartDossierStatus: null,
      observerUserIds: [],
    },
  ]),
}))

describe('StartWorkflowDialog', () => {
  it('emits default workflow key', async () => {
    const wrapper = mount(StartWorkflowDialog, {
      props: { open: true, defaultWorkflowKey: 'default-wf' },
    })
    await wrapper.find('button.primary').trigger('click')
    expect(wrapper.emitted('start')?.[0]).toEqual(['default-wf'])
  })
})
