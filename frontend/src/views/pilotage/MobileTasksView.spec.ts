import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import MobileTasksView from './MobileTasksView.vue'
import { useTasksStore } from '@/stores/tasks'
import { createMemoryHistory, createRouter } from 'vue-router'
import { routes } from '@/app/routes'
import { getDossierById } from '@/api/dossiers'

vi.mock('@/api/tasks', () => ({
  listMyTasks: vi.fn().mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
  completeTask: vi.fn().mockResolvedValue(undefined),
}))
vi.mock('@/api/dossiers', () => ({
  getDossierById: vi.fn(),
  updateDossierFields: vi.fn(),
}))
vi.mock('@/api/dossierTypes', () => ({
  getDossierType: vi.fn(),
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

  it('shows the blind complete button for a task without formKey (non-regression)', async () => {
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
      },
    ]
    store.loading = false
    const wrapper = mount(MobileTasksView, { global: { plugins: [router] } })
    await wrapper.vm.$nextTick()

    const button = wrapper.find('[data-testid="complete-task"]')
    expect(button.exists()).toBe(true)
    expect(button.text()).toBe('Compléter')
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

    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/tasks')
    await router.isReady()
    const store = useTasksStore()
    vi.spyOn(store, 'refresh').mockResolvedValue()
    const completeSpy = vi.spyOn(store, 'complete').mockResolvedValue()
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
        formKey: 'dossier-fields',
      },
    ]
    store.loading = false
    const wrapper = mount(MobileTasksView, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.find('[data-testid="complete-task"]').exists()).toBe(false)
    await wrapper.get('.dossier-fields-task-form button').trigger('click')
    await flushPromises()

    expect(completeSpy).toHaveBeenCalledWith('t1', {})
  })
})
