import axios from 'axios'
import type {
  ActivityHistoryEntry,
  WorkflowDiagram,
  WorkflowTaskView,
  WorkflowTypeConfig,
} from './types'
import type { AdminListParams } from './dossierTypes'
import { listAllPages, type Page } from './page'

export async function getWorkflowDiagram(linkId: string): Promise<WorkflowDiagram> {
  const { data } = await axios.get<WorkflowDiagram>(`/api/workflow-links/${linkId}/diagram`)
  return data
}

export async function getWorkflowHistory(linkId: string): Promise<ActivityHistoryEntry[]> {
  const { data } = await axios.get<ActivityHistoryEntry[]>(`/api/workflow-links/${linkId}/history`)
  return data
}

export async function getWorkflowTasks(linkId: string): Promise<WorkflowTaskView[]> {
  const { data } = await axios.get<WorkflowTaskView[]>(`/api/workflow-links/${linkId}/tasks`)
  return data
}

export async function reassignWorkflowTask(
  linkId: string,
  taskId: string,
  assigneeId: string,
): Promise<void> {
  await axios.post(`/api/workflow-links/${linkId}/tasks/${taskId}/reassign`, { assigneeId })
}

export async function listWorkflowTypeConfigs(
  params: AdminListParams = {},
): Promise<Page<WorkflowTypeConfig>> {
  const query: Record<string, number> = {}
  if (params.page != null) query.page = params.page
  if (params.size != null) query.size = params.size
  const { data } = await axios.get<Page<WorkflowTypeConfig>>('/api/workflow-type-configs', {
    params: query,
  })
  return data
}

export async function listAllWorkflowTypeConfigs(): Promise<WorkflowTypeConfig[]> {
  return listAllPages((page, size) => listWorkflowTypeConfigs({ page, size }))
}

export async function getWorkflowTypeConfig(workflowKey: string): Promise<WorkflowTypeConfig> {
  const { data } = await axios.get<WorkflowTypeConfig>(`/api/workflow-type-configs/${workflowKey}`)
  return data
}

export async function createWorkflowTypeConfig(
  payload: Omit<WorkflowTypeConfig, 'id' | 'tenantId'>,
): Promise<WorkflowTypeConfig> {
  const { data } = await axios.post<WorkflowTypeConfig>('/api/workflow-type-configs', payload)
  return data
}

export async function updateWorkflowTypeConfig(
  workflowKey: string,
  payload: Omit<WorkflowTypeConfig, 'id' | 'tenantId'>,
): Promise<WorkflowTypeConfig> {
  const { data } = await axios.patch<WorkflowTypeConfig>(
    `/api/workflow-type-configs/${workflowKey}`,
    payload,
  )
  return data
}
