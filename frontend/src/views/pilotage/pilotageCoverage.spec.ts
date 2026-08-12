import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import SupervisionView from './SupervisionView.vue'
import MobileTasksView from './MobileTasksView.vue'
import { routes } from '@/app/routes'
import { useDossierStore } from '@/stores/dossier'
import { useTasksStore } from '@/stores/tasks'
import * as tasksApi from '@/api/tasks'

vi.mock('@/api/tasks')
vi.mock('@/api/dossiers')
vi.mock('@/api/dossierTypes')
vi.mock('@/api/workflows')

describe('pilotage remaining coverage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
  })

  it('SupervisionView opens by ref', async () => {
    const store = useDossierStore()
    store.loadByRef = vi.fn().mockImplementation(async () => {
      store.dossier = { id: 'd1' } as never
    })
    store.loadById = vi.fn()
    const router = createRouter({ history: createMemoryHistory(), routes })
    const push = vi.spyOn(router, 'push')
    await router.push('/supervision')
    await router.isReady()
    const wrapper = mount(SupervisionView, { global: { plugins: [router] } })
    await wrapper.get('input').setValue('DOS-1')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()
    expect(store.loadByRef).toHaveBeenCalledWith('DOS-1')
    expect(push).toHaveBeenCalledWith('/dossiers/d1')
  })

  it('MobileTasksView completes a task', async () => {
    vi.mocked(tasksApi.listMyTasks).mockResolvedValue([
      {
        taskId: 't1',
        name: 'Review',
        assigneeId: 'u',
        createdAt: null,
        processInstanceId: 'pi',
        dossierId: 'd1',
        dossierReference: 'DOS-1',
        linkId: 'l1',
      },
    ])
    vi.mocked(tasksApi.completeTask).mockResolvedValue(undefined)
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/tasks')
    await router.isReady()
    const wrapper = mount(MobileTasksView, { global: { plugins: [router] } })
    await flushPromises()
    const store = useTasksStore()
    store.tasks = [
      {
        taskId: 't1',
        name: 'Review',
        assigneeId: 'u',
        createdAt: null,
        processInstanceId: 'pi',
        dossierId: 'd1',
        dossierReference: 'DOS-1',
        linkId: 'l1',
      },
    ]
    await wrapper.vm.$nextTick()
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(tasksApi.completeTask).toHaveBeenCalledWith('t1', {})
  })
})
