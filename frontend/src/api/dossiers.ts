import axios from 'axios'
import type {
  Dossier,
  DossierElementView,
  DossierListParams,
  DossierPage,
  DossierRelation,
  DossierStatus,
  ElementType,
  RelationType,
  WorkflowLink,
} from './types'

export async function createDossier(payload: {
  dossierTypeCode: string
  label: string
  fields: Record<string, unknown>
}): Promise<Dossier> {
  const { data } = await axios.post<Dossier>('/api/dossiers', payload)
  return data
}

export async function listDossiers(params: DossierListParams = {}): Promise<DossierPage> {
  const query: Record<string, string | number> = {}
  if (params.page != null) query.page = params.page
  if (params.size != null) query.size = params.size
  if (params.sort) query.sort = params.sort
  if (params.status) query.status = params.status
  if (params.dossierTypeCode) query.dossierTypeCode = params.dossierTypeCode
  if (params.reference) query.reference = params.reference
  const { data } = await axios.get<DossierPage>('/api/dossiers', { params: query })
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

export async function listDossierRelations(id: string): Promise<DossierRelation[]> {
  const { data } = await axios.get<DossierRelation[]>(`/api/dossiers/${id}/relations`)
  return data
}

export async function createDossierRelation(
  id: string,
  payload: { targetDossierId: string; type: RelationType },
): Promise<DossierRelation> {
  const { data } = await axios.post<DossierRelation>(`/api/dossiers/${id}/relations`, payload)
  return data
}

export async function listDossierElements(id: string): Promise<DossierElementView[]> {
  const { data } = await axios.get<DossierElementView[]>(`/api/dossiers/${id}/elements`)
  return data
}

export async function addDossierElement(
  id: string,
  payload: { type: ElementType; label: string; metadata: Record<string, unknown> },
): Promise<DossierElementView> {
  const { data } = await axios.post<DossierElementView>(`/api/dossiers/${id}/elements`, payload)
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
