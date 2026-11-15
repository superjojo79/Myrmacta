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

  /** TODO §141 — la liste est un sélecteur de run. */
  it('emits select when a link with a process instance is chosen', async () => {
    const wrapper = mount(WorkflowLinksList, {
      props: {
        selectedId: 'l1',
        workflows: [
          {
            id: 'l1',
            dossierId: 'd1',
            tenantId: 't',
            processInstanceId: 'pi-1',
            processDefinitionKey: 'claimWorkflow',
            sequence: 1,
            status: 'COMPLETED',
            triggerRef: null,
            startedAt: '2026-08-12T00:00:00Z',
            endedAt: '2026-08-12T01:00:00Z',
            endReason: 'COMPLETED',
            startedBy: 'u1',
          },
          {
            id: 'l2',
            dossierId: 'd1',
            tenantId: 't',
            processInstanceId: 'pi-2',
            processDefinitionKey: 'claimWorkflow',
            sequence: 2,
            status: 'PROVISIONNE',
            triggerRef: null,
            startedAt: '2026-08-13T00:00:00Z',
            endedAt: '2026-08-13T02:00:00Z',
            endReason: 'COMPLETED',
            startedBy: 'u1',
          },
        ],
      },
    })
    expect(wrapper.get('[data-testid="workflow-link-l1"]').classes()).toContain(
      'workflow-links-list__item--selected',
    )
    await wrapper.get('[data-testid="workflow-link-l2"]').trigger('click')
    expect(wrapper.emitted('select')?.[0]).toEqual(['l2'])
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
