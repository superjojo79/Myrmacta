import axios from 'axios'
import type { DossierPermission, DossierType, FieldDefinition } from './types'

export async function listDossierTypes(): Promise<DossierType[]> {
  const { data } = await axios.get<DossierType[]>('/api/dossier-types')
  return data
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
