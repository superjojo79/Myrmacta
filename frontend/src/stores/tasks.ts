import { defineStore } from 'pinia'
import { ref } from 'vue'
import { completeTask, listMyTasks } from '@/api/tasks'
import { toProblemDetail } from '@/api/http'
import type { MyWorkflowTask } from '@/api/types'

export const useTasksStore = defineStore('tasks', () => {
  const tasks = ref<MyWorkflowTask[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function refresh(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      tasks.value = await listMyTasks()
    } catch (err) {
      error.value = toProblemDetail(err).detail ?? 'Impossible de charger les tâches'
      throw err
    } finally {
      loading.value = false
    }
  }

  async function complete(taskId: string): Promise<void> {
    await completeTask(taskId, {})
    await refresh()
  }

  return { tasks, loading, error, refresh, complete }
})
