import axios from 'axios'
import type { MyWorkflowTask } from './types'
import { listAllPages, type Page } from './page'

export async function listMyTasks(): Promise<MyWorkflowTask[]> {
  return listAllPages((page, size) =>
    axios
      .get<Page<MyWorkflowTask>>('/api/workflow-tasks/mine', { params: { page, size } })
      .then((response) => response.data),
  )
}

export async function completeTask(
  taskId: string,
  variables: Record<string, unknown> = {},
): Promise<void> {
  await axios.post(`/api/workflow-tasks/${taskId}/complete`, { variables })
}
