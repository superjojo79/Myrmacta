import axios from 'axios'
import type { DossierPermission, DossierType, FieldDefinition } from './types'
import { listAllPages, type Page } from './page'

export interface AdminListParams {
  page?: number
  size?: number
}

export async function listDossierTypes(params: AdminListParams = {}): Promise<Page<DossierType>> {
  const query: Record<string, number> = {}
  if (params.page != null) query.page = params.page
  if (params.size != null) query.size = params.size
  const { data } = await axios.get<Page<DossierType>>('/api/dossier-types', { params: query })
  return data
}

export async function listAllDossierTypes(): Promise<DossierType[]> {
  return listAllPages((page, size) => listDossierTypes({ page, size }))
}

export async function getDossierType(code: string): Promise<DossierType> {
  const { data } = await axios.get<DossierType>(`/api/dossier-types/${code}`)
  return data
}

export async function createDossierType(payload: {
  code: string
  label: string
  workflowKey: string
  active?: boolean
  allowUnknownFields?: boolean
  fieldDefinitions?: FieldDefinition[]
  retentionDays?: number | null
  retentionNever?: boolean
  slaTargetHours?: number | null
}): Promise<DossierType> {
  const { data } = await axios.post<DossierType>('/api/dossier-types', payload)
  return data
}

export async function updateDossierType(
  code: string,
  payload: Partial<{
    label: string
    workflowKey: string
    active: boolean
    allowUnknownFields: boolean
    fieldDefinitions: FieldDefinition[]
    retentionDays: number | null
    retentionNever: boolean
    slaTargetHours: number | null
  }>,
): Promise<DossierType> {
  const { data } = await axios.patch<DossierType>(`/api/dossier-types/${code}`, payload)
  return data
}

export async function grantDossierTypeAcl(
  code: string,
  subjectUserId: string,
  permission: DossierPermission,
): Promise<void> {
  await axios.post(`/api/dossier-types/${code}/acl`, { subjectUserId, permission })
}

export async function revokeDossierTypeAcl(code: string, userId: string): Promise<void> {
  await axios.delete(`/api/dossier-types/${code}/acl/${userId}`)
}