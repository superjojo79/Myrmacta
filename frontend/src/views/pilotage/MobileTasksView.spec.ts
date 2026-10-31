import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import MobileTasksView from './MobileTasksView.vue'
import { useTasksStore } from '@/stores/tasks'
import { createMemoryHistory, createRouter } from 'vue-router'
import { routes } from '@/app/routes'
import { getDossierById, updateDossierFields } from '@/api/dossiers'

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
  evaluateFieldRules: vi.fn(),
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

  it('clears the banner when a later field error replaces an off-screen holder reference (TODO §102)', async () => {
    vi.mocked(getDossierById).mockResolvedValue({
      id: 'd1',
      dossierTypeCode: 'CLAIM',
      fields: { priority: 'NORMAL' },
      version: 1,
    } as never)
    const { getDossierType } = await import('@/api/dossierTypes')
    vi.mocked(getDossierType).mockResolvedValue({
      code: 'CLAIM',
      fieldDefinitions: [{ name: 'priority', label: 'Priority', type: 'STRING', required: false }],
    } as never)
    vi.mocked(updateDossierFields)
      .mockRejectedValueOnce({
        response: {
          data: {
            detail: 'absent UNIQUE Value already used by dossier DOS-9',
            errors: [{ field: 'absent', code: 'UNIQUE', message: 'Value already used by dossier DOS-9', reference: 'DOS-9' }],
          },
        },
      })
      .mockRejectedValueOnce({
        response: {
          data: {
            detail: 'priority UNIQUE Value already used by dossier DOS-2',
            errors: [{ field: 'priority', code: 'UNIQUE', message: 'Value already used by dossier DOS-2', reference: 'DOS-2' }],
          },
        },
      })
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
        formKey: 'dossier-fields',
      },
    ]
    store.loading = false
    const wrapper = mount(MobileTasksView, { global: { plugins: [router] } })
    await flushPromises()
    const input = wrapper.get('.dossier-fields-task-form input')
    await input.setValue('HIGH')
    await wrapper.get('.dossier-fields-task-form button').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="task-form-banner"]').text()).toContain('DOS-9')
    await input.setValue('LOW')
    await wrapper.get('.dossier-fields-task-form button').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="task-form-banner"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('DOS-9')
    expect(wrapper.get('[data-testid="field-error"]').text()).toContain('DOS-2')
  })

  it('keeps the first task off-screen holder when another task shows a visible UNIQUE error (TODO §102)', async () => {
    vi.mocked(getDossierById).mockResolvedValue({
      id: 'd1',
      dossierTypeCode: 'CLAIM',
      fields: { priority: 'NORMAL' },
      version: 1,
    } as never)
    const { getDossierType } = await import('@/api/dossierTypes')
    vi.mocked(getDossierType).mockResolvedValue({
      code: 'CLAIM',
      fieldDefinitions: [{ name: 'priority', label: 'Priority', type: 'STRING', required: false }],
    } as never)
    vi.mocked(updateDossierFields)
      .mockRejectedValueOnce({
        response: {
          data: {
            detail: 'absent UNIQUE Value already used by dossier DOS-9',
            errors: [{ field: 'absent', code: 'UNIQUE', message: 'Value already used by dossier DOS-9', reference: 'DOS-9' }],
          },
        },
      })
      .mockRejectedValueOnce({
        response: {
          data: {
            detail: 'priority UNIQUE Value already used by dossier DOS-2',
            errors: [{ field: 'priority', code: 'UNIQUE', message: 'Value already used by dossier DOS-2', reference: 'DOS-2' }],
          },
        },
      })
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/tasks')
    await router.isReady()
    const store = useTasksStore()
    vi.spyOn(store, 'refresh').mockResolvedValue()
    const task = (taskId: string, name: string) => ({
      taskId,
      name,
      assigneeId: 'u1',
      createdAt: null,
      processInstanceId: 'pi',
      dossierId: 'd1',
      dossierReference: 'DOS-1',
      linkId: 'l1',
      formKey: 'dossier-fields',
    })
    store.tasks = [task('t1', 'Première'), task('t2', 'Seconde')]
    store.loading = false
    const wrapper = mount(MobileTasksView, { global: { plugins: [router] } })
    await flushPromises()
    const forms = wrapper.findAll('.dossier-fields-task-form')
    await forms[0]!.get('input').setValue('HIGH')
    await forms[0]!.get('button').trigger('click')
    await flushPromises()
    await forms[1]!.get('input').setValue('LOW')
    await forms[1]!.get('button').trigger('click')
    await flushPromises()
    const row = (name: string) => wrapper.findAll('.tasks-view__list li').find((item) => item.text().includes(name))!
    expect(row('Première').text()).toContain('DOS-9')
    expect(row('Première').get('[data-testid="task-form-banner"]').attributes('role')).toBe('alert')
    expect(row('Seconde').get('[data-testid="field-error"]').text()).toContain('DOS-2')
    expect(row('Seconde').text()).not.toContain('DOS-9')
  })

  it('offers to take a free task of one of my groups instead of completing it', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/tasks')
    await router.isReady()
    const store = useTasksStore()
    vi.spyOn(store, 'refresh').mockResolvedValue()
    const take = vi.spyOn(store, 'take').mockResolvedValue()
    const free = {
      taskId: 't-free',
      name: 'Trier le courrier',
      assigneeId: null,
      createdAt: null,
      processInstanceId: 'pi',
      dossierId: 'd1',
      dossierReference: 'DOS-COURRIER-1',
      linkId: 'l1',
    }
    store.tasks = [free]
    store.loading = false
    const wrapper = mount(MobileTasksView, { global: { plugins: [router] } })
    await flushPromises()
    expect(wrapper.get('[data-testid="task-group"]').text()).toContain('À prendre')
    expect(wrapper.find('[data-testid="complete-task"]').exists()).toBe(false)
    await wrapper.get('[data-testid="take-task"]').trigger('click')
    await flushPromises()
    expect(take).toHaveBeenCalledWith(free)
  })

  it('shows the reason when a group task cannot be taken', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/tasks')
    await router.isReady()
    const store = useTasksStore()
    vi.spyOn(store, 'refresh').mockResolvedValue()
    vi.spyOn(store, 'take').mockRejectedValue({
      response: { status: 409, data: { detail: 'Task already claimed by another user', status: 409 } },
    })
    store.tasks = [
      {
        taskId: 't-free',
        name: 'Trier le courrier',
        assigneeId: null,
        createdAt: null,
        processInstanceId: 'pi',
        dossierId: 'd1',
        dossierReference: 'DOS-COURRIER-1',
        linkId: 'l1',
      },
    ]
    store.loading = false
    const wrapper = mount(MobileTasksView, { global: { plugins: [router] } })
    await flushPromises()
    await wrapper.get('[data-testid="take-task"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('Task already claimed by another user')
  })
})
