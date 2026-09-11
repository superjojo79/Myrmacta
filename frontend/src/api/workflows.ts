import axios from 'axios'
import type {
  ActivityHistoryEntry,
  BpmnDeploymentResult,
  BpmnDraft,
  DecisionTableDeploymentInfo,
  DecisionTableDeploymentResult,
  DmnDraft,
  ProcessDeploymentInfo,
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

export async function getWorkflowHistory(
  linkId: string,
  params: { page?: number; size?: number } = {},
): Promise<Page<ActivityHistoryEntry>> {
  const { data } = await axios.get<Page<ActivityHistoryEntry>>(`/api/workflow-links/${linkId}/history`, {
    params: { page: params.page, size: params.size },
  })
  return data
}

export async function getWorkflowTasks(
  linkId: string,
  params: { page?: number; size?: number } = {},
): Promise<Page<WorkflowTaskView>> {
  const { data } = await axios.get<Page<WorkflowTaskView>>(`/api/workflow-links/${linkId}/tasks`, {
    params: { page: params.page, size: params.size },
  })
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

export async function getBpmnDeploymentInfo(
  processDefinitionKey: string,
): Promise<ProcessDeploymentInfo | null> {
  const { data, status } = await axios.get<ProcessDeploymentInfo>(
    '/api/workflow-bpmn-deployments/info',
    { params: { processDefinitionKey } },
  )
  return status === 204 ? null : data
}

export async function deployBpmn(file: File): Promise<BpmnDeploymentResult> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await axios.post<BpmnDeploymentResult>('/api/workflow-bpmn-deployments', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return data
}

export async function getBpmnDraft(processDefinitionKey: string): Promise<BpmnDraft | null> {
  const { data, status } = await axios.get<BpmnDraft>(`/api/workflow-bpmn-drafts/${processDefinitionKey}`)
  return status === 204 ? null : data
}

export async function saveBpmnDraft(processDefinitionKey: string, xml: string): Promise<BpmnDraft> {
  const { data } = await axios.put<BpmnDraft>(`/api/workflow-bpmn-drafts/${processDefinitionKey}`, { xml })
  return data
}

export async function getDeployedBpmnXml(processDefinitionKey: string): Promise<string | null> {
  const { data, status } = await axios.get<string>(
    `/api/workflow-bpmn-deployments/${processDefinitionKey}/xml`,
  )
  return status === 204 ? null : data
}

export async function getDecisionTableDeploymentInfo(
  decisionKey: string,
): Promise<DecisionTableDeploymentInfo | null> {
  const { data, status } = await axios.get<DecisionTableDeploymentInfo>(
    '/api/dmn-decision-deployments/info',
    { params: { decisionKey } },
  )
  return status === 204 ? null : data
}

export async function deployDecisionTable(file: File): Promise<DecisionTableDeploymentResult> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await axios.post<DecisionTableDeploymentResult>(
    '/api/dmn-decision-deployments',
    form,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  )
  return data
}

export async function getDmnDraft(decisionKey: string): Promise<DmnDraft | null> {
  const { data, status } = await axios.get<DmnDraft>(`/api/dmn-decision-drafts/${decisionKey}`)
  return status === 204 ? null : data
}

export async function saveDmnDraft(decisionKey: string, xml: string): Promise<DmnDraft> {
  const { data } = await axios.put<DmnDraft>(`/api/dmn-decision-drafts/${decisionKey}`, { xml })
  return data
}

export async function getDeployedDmnXml(decisionKey: string): Promise<string | null> {
  const { data, status } = await axios.get<string>(
    `/api/dmn-decision-deployments/${decisionKey}/xml`,
  )
  return status === 204 ? null : data
}
