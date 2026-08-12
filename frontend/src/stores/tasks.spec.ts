import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useTasksStore } from './tasks'
import * as tasksApi from '@/api/tasks'

vi.mock('@/api/tasks')

describe('tasks store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
  })

  it('refreshes inbox from /mine', async () => {
    vi.mocked(tasksApi.listMyTasks).mockResolvedValue([
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
    ])
    const store = useTasksStore()
    await store.refresh()
    expect(store.tasks).toHaveLength(1)
    expect(tasksApi.listMyTasks).toHaveBeenCalled()
  })

  it('completes then refreshes', async () => {
    vi.mocked(tasksApi.completeTask).mockResolvedValue(undefined)
    vi.mocked(tasksApi.listMyTasks).mockResolvedValue([])
    const store = useTasksStore()
    await store.complete('t1')
    expect(tasksApi.completeTask).toHaveBeenCalledWith('t1', {})
    expect(tasksApi.listMyTasks).toHaveBeenCalled()
  })
})
