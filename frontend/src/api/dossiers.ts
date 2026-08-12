import axios from 'axios'
import type { Dossier, DossierElementView, DossierStatus, WorkflowLink } from './types'

export async function createDossier(payload: {
  dossierTypeCode: string
  label: string
  fields: Record<string, unknown>
}): Promise<Dossier> {
  const { data } = await axios.post<Dossier>('/api/dossiers', payload)
  return data
}

export async function getDossierById(id: string): Promise<Dossier> {
  const { data } = await axios.get<Dossier>(`/api/dossiers/${id}`)
  return data
}

export async function getDossierByRef(ref: string): Promise<Dossier> {
  const { data } = await axios.get<Dossier>('/api/dossiers', { params: { ref } })
  return data
}

export async function updateDossierStatus(
  id: string,
  status: DossierStatus,
  expectedVersion: number,
): Promise<Dossier> {
  const { data } = await axios.patch<Dossier>(`/api/dossiers/${id}/status`, {
    status,
    expectedVersion,
  })
  return data
}

export async function listDossierElements(id: string): Promise<DossierElementView[]> {
  const { data } = await axios.get<DossierElementView[]>(`/api/dossiers/${id}/elements`)
  return data
}

export async function listDossierWorkflows(id: string): Promise<WorkflowLink[]> {
  const { data } = await axios.get<WorkflowLink[]>(`/api/dossiers/${id}/workflows`)
  return data
}

export async function startDossierWorkflow(
  id: string,
  processDefinitionKey: string,
  variables: Record<string, unknown>,
  idempotencyKey: string,
): Promise<WorkflowLink> {
  const { data } = await axios.post<WorkflowLink>(
    `/api/dossiers/${id}/workflows`,
    { processDefinitionKey, variables },
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  return data
}
