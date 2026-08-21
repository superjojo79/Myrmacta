import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import WorkflowLinksList from './WorkflowLinksList.vue'

describe('WorkflowLinksList', () => {
  it('shows empty state', () => {
    const wrapper = mount(WorkflowLinksList, { props: { workflows: [] } })
    expect(wrapper.text()).toContain('Aucun workflow.')
  })

  it('lists workflow links', () => {
    const wrapper = mount(WorkflowLinksList, {
      props: {
        workflows: [
          {
            id: 'l1',
            dossierId: 'd1',
            tenantId: 't',
            processInstanceId: 'pi',
            processDefinitionKey: 'claimWorkflow',
            sequence: 1,
            status: 'ACTIVE',
            triggerRef: null,
            startedAt: '2026-08-12T00:00:00Z',
            endedAt: null,
            endReason: null,
            startedBy: 'u1',
          },
        ],
      },
    })
    expect(wrapper.text()).toContain('claimWorkflow')
    expect(wrapper.text()).toContain('ACTIVE')
  })

  it('shows the pager when there is more than one page and emits update:page', async () => {
    const wrapper = mount(WorkflowLinksList, { props: { workflows: [], page: 0, totalPages: 2 } })
    expect(wrapper.find('[data-testid="admin-pager-next"]').exists()).toBe(true)
    await wrapper.get('[data-testid="admin-pager-next"]').trigger('click')
    expect(wrapper.emitted('update:page')?.[0]).toEqual([1])
  })

  it('hides the pager when there is a single page', () => {
    const wrapper = mount(WorkflowLinksList, { props: { workflows: [], page: 0, totalPages: 1 } })
    expect(wrapper.find('[data-testid="admin-pager-next"]').exists()).toBe(false)
  })
})
