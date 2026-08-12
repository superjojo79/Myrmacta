import axios from 'axios'
import type { MyWorkflowTask } from './types'

export async function listMyTasks(): Promise<MyWorkflowTask[]> {
  const { data } = await axios.get<MyWorkflowTask[]>('/api/workflow-tasks/mine')
  return data
}

export async function completeTask(
  taskId: string,
  variables: Record<string, unknown> = {},
): Promise<void> {
  await axios.post(`/api/workflow-tasks/${taskId}/complete`, { variables })
}
