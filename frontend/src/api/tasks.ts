import axios from 'axios'
import type { MyWorkflowTask } from './types'
import type { Page } from './page'

export async function listMyTasks(params: { page?: number; size?: number } = {}): Promise<Page<MyWorkflowTask>> {
  const { data } = await axios.get<Page<MyWorkflowTask>>('/api/workflow-tasks/mine', {
    params: { page: params.page, size: params.size },
  })
  return data
}

export async function completeTask(
  taskId: string,
  variables: Record<string, unknown> = {},
): Promise<void> {
  await axios.post(`/api/workflow-tasks/${taskId}/complete`, { variables })
}
