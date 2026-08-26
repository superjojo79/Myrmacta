import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import MobileTasksView from './MobileTasksView.vue'
import { useTasksStore } from '@/stores/tasks'
import { createMemoryHistory, createRouter } from 'vue-router'
import { routes } from '@/app/routes'

vi.mock('@/api/tasks', () => ({
  listMyTasks: vi.fn().mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
  completeTask: vi.fn().mockResolvedValue(undefined),
}))

describe('MobileTasksView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('shows empty state', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/tasks')
    await router.isReady()
    const store = useTasksStore()
    store.tasks = []
    store.loading = false
    const wrapper = mount(MobileTasksView, { global: { plugins: [router] } })
    expect(wrapper.text()).toContain('Aucune tâche assignée')
  })

  it('shows the pager and refreshes on page change', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/tasks')
    await router.isReady()
    const store = useTasksStore()
    const refreshSpy = vi.spyOn(store, 'refresh').mockResolvedValue()
    store.tasks = [
      {
        taskId: 't1',
        name: 'Review',
        assigneeId: 'u1',
        createdAt: null,
        processInstanceId: 'pi',
        dossierId: 'd1',
        dossierReference: 'DOS-1',
        linkId: 'l1',
      },
    ]
    store.totalPages = 2
    store.loading = false
    const wrapper = mount(MobileTasksView, { global: { plugins: [router] } })
    await wrapper.vm.$nextTick()

    expect(refreshSpy).toHaveBeenCalled()
    await wrapper.get('[data-testid="admin-pager-next"]').trigger('click')
    await wrapper.vm.$nextTick()
    expect(refreshSpy.mock.calls.length).toBeGreaterThan(1)
  })

  it('shows SLA remaining hours when provided', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/tasks')
    await router.isReady()
    const store = useTasksStore()
    vi.spyOn(store, 'refresh').mockResolvedValue()
    store.tasks = [
      {
        taskId: 't1',
        name: 'Review',
        assigneeId: 'u1',
        createdAt: null,
        processInstanceId: 'pi',
        dossierId: 'd1',
        dossierReference: 'DOS-1',
        linkId: 'l1',
        slaRemainingHours: 12,
      },
    ]
    store.loading = false
    const wrapper = mount(MobileTasksView, { global: { plugins: [router] } })
    await wrapper.vm.$nextTick()
    expect(wrapper.get('[data-testid="task-sla"]').text()).toContain('SLA')
    expect(wrapper.get('[data-testid="task-sla"]').text()).toContain('12')
  })
})
