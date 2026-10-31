import { defineStore } from 'pinia'
import { i18n } from '@/i18n'
import { ref } from 'vue'
import { completeTask, listMyTasks } from '@/api/tasks'
import { claimWorkflowTask } from '@/api/workflows'
import { toProblemDetail } from '@/api/http'
import type { MyWorkflowTask } from '@/api/types'

export const useTasksStore = defineStore('tasks', () => {
  const tasks = ref<MyWorkflowTask[]>([])
  const totalPages = ref(0)
  const page = ref(0)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function refresh(nextPage = page.value, size = 20): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const result = await listMyTasks({ page: nextPage, size })
      tasks.value = result.content
      totalPages.value = result.totalPages
      page.value = nextPage
    } catch (err) {
      error.value = toProblemDetail(err).detail ?? i18n.global.t('common.errors.tasks')
      throw err
    } finally {
      loading.value = false
    }
  }

  async function complete(taskId: string, variables: Record<string, unknown> = {}): Promise<void> {
    await completeTask(taskId, variables)
    await refresh()
  }

  /** TODO §114 — prendre une tâche libre de l'un de ses groupes (« M'assigner », §92), puis recharger la liste. */
  async function take(task: MyWorkflowTask): Promise<void> {
    if (!task.linkId) return
    await claimWorkflowTask(task.linkId, task.taskId)
    await refresh()
  }

  return { tasks, totalPages, page, loading, error, refresh, complete, take }
})
