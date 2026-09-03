import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useTasksStore } from './tasks'
import * as tasksApi from '@/api/tasks'
import { asPage } from '@/api/page'

vi.mock('@/api/tasks')

describe('tasks store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
  })

  it('refreshes inbox from /mine and stores totalPages', async () => {
    vi.mocked(tasksApi.listMyTasks).mockResolvedValue(
      asPage(
        [
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
        ],
        { totalElements: 25, size: 20, totalPages: 2 },
      ),
    )
    const store = useTasksStore()
    await store.refresh(0, 20)
    expect(store.tasks).toHaveLength(1)
    expect(store.totalPages).toBe(2)
    expect(tasksApi.listMyTasks).toHaveBeenCalledWith({ page: 0, size: 20 })
  })

  it('completes then refreshes', async () => {
    vi.mocked(tasksApi.completeTask).mockResolvedValue(undefined)
    vi.mocked(tasksApi.listMyTasks).mockResolvedValue(asPage([]))
    const store = useTasksStore()
    await store.complete('t1')
    expect(tasksApi.completeTask).toHaveBeenCalledWith('t1', {})
    expect(tasksApi.listMyTasks).toHaveBeenCalled()
  })

  it('completes with the given task-form variables (TODO §52)', async () => {
    vi.mocked(tasksApi.completeTask).mockResolvedValue(undefined)
    vi.mocked(tasksApi.listMyTasks).mockResolvedValue(asPage([]))
    const store = useTasksStore()
    await store.complete('t1', { templateKey: 'invoice', variantKey: null, locale: 'fr' })
    expect(tasksApi.completeTask).toHaveBeenCalledWith('t1', {
      templateKey: 'invoice',
      variantKey: null,
      locale: 'fr',
    })
  })
})
