import { describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import TaskList from './TaskList.vue'
import { getDossierById } from '@/api/dossiers'

vi.mock('@/api/dossiers', () => ({
  getDossierById: vi.fn(),
  updateDossierFields: vi.fn(),
}))
vi.mock('@/api/dossierTypes', () => ({
  getDossierType: vi.fn(),
}))

describe('TaskList', () => {
  it('shows empty state', () => {
    const wrapper = mount(TaskList, { props: { tasks: [] } })
    expect(wrapper.text()).toContain('Aucune tâche ouverte.')
  })

  it('lists tasks and emits complete/reassign', async () => {
    const wrapper = mount(TaskList, {
      props: {
        tasks: [
          { taskId: 't1', name: 'Do it', assignee: 'u1', taskDefinitionKey: 'k', createTime: null },
        ],
        canReassign: true,
      },
    })
    expect(wrapper.text()).toContain('Do it')
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('complete')?.[0]).toEqual(['t1'])
  })

  it('offers the claim button on unassigned tasks only, when the user can claim (D-ASSIGN-CLAIM)', async () => {
    const wrapper = mount(TaskList, {
      props: {
        tasks: [
          { taskId: 'free', name: 'Free', assignee: null, taskDefinitionKey: 'k', createTime: null },
          { taskId: 'taken', name: 'Taken', assignee: 'u2', taskDefinitionKey: 'k', createTime: null },
        ],
        canClaim: true,
      },
    })
    const claimButtons = wrapper.findAll('[data-testid="claim-task"]')
    expect(claimButtons).toHaveLength(1)
    expect(claimButtons[0].text()).toBe('M’assigner')
    await claimButtons[0].trigger('click')
    expect(wrapper.emitted('claim')?.[0]).toEqual(['free'])
  })

  it('hides the claim button without edit permission', () => {
    const wrapper = mount(TaskList, {
      props: {
        tasks: [{ taskId: 'free', name: 'Free', assignee: null, taskDefinitionKey: 'k', createTime: null }],
        canClaim: false,
      },
    })
    expect(wrapper.find('[data-testid="claim-task"]').exists()).toBe(false)
  })

  it('shows "vous" for the current user and a known display name for others', () => {
    const wrapper = mount(TaskList, {
      props: {
        tasks: [
          { taskId: 'a', name: 'Mine', assignee: 'me', taskDefinitionKey: 'k', createTime: null },
          { taskId: 'b', name: 'Theirs', assignee: 'u2', taskDefinitionKey: 'k', createTime: null },
          { taskId: 'c', name: 'Unknown', assignee: 'u3', taskDefinitionKey: 'k', createTime: null },
        ],
        currentUserId: 'me',
        assigneeNames: { u2: 'Grace Hopper' },
      },
    })
    const text = wrapper.text()
    expect(text).toContain('Assignee : vous')
    expect(text).toContain('Assignee : Grace Hopper')
    expect(text).toContain('Assignee : u3')
  })

  it('shows the pager when there is more than one page and emits update:page', async () => {
    const wrapper = mount(TaskList, { props: { tasks: [], page: 0, totalPages: 2 } })
    expect(wrapper.find('[data-testid="admin-pager-next"]').exists()).toBe(true)
    await wrapper.get('[data-testid="admin-pager-next"]').trigger('click')
    expect(wrapper.emitted('update:page')?.[0]).toEqual([1])
  })

  it('hides the pager when there is a single page', () => {
    const wrapper = mount(TaskList, { props: { tasks: [], page: 0, totalPages: 1 } })
    expect(wrapper.find('[data-testid="admin-pager-next"]').exists()).toBe(false)
  })

  it('renders the resolved task form instead of the blind button when formKey is known', async () => {
    vi.mocked(getDossierById).mockResolvedValue({
      id: 'd1',
      dossierTypeCode: 'CLAIM',
      fields: {},
      version: 1,
    } as never)
    const { getDossierType } = await import('@/api/dossierTypes')
    vi.mocked(getDossierType).mockResolvedValue({ fieldDefinitions: [] } as never)

    const wrapper = mount(TaskList, {
      props: {
        tasks: [
          {
            taskId: 't1',
            name: 'Do it',
            assignee: 'u1',
            taskDefinitionKey: 'k',
            createTime: null,
            formKey: 'dossier-fields',
          },
        ],
        dossierId: 'd1',
      },
    })
    await flushPromises()

    expect(wrapper.find('[data-testid="complete-task"]').exists()).toBe(false)
    await wrapper.get('.dossier-fields-task-form button').trigger('click')
    await flushPromises()

    expect(wrapper.emitted('complete')?.[0]).toEqual(['t1', {}])
  })
})
