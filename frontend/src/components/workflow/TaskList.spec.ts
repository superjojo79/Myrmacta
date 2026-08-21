import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import TaskList from './TaskList.vue'

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
})
