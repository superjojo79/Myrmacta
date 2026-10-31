import axios from 'axios'
import type { AllowedDocumentTemplate, DossierPermission, DossierType, FieldDefinition, FieldRulesPreview, PieceType } from './types'
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

/** TODO §80 : aperçu des règles du type (champs calculés, champs sans objet, obligations) — même moteur que l'enregistrement. */
export async function evaluateFieldRules(code: string, fields: Record<string, unknown>): Promise<FieldRulesPreview> {
  const { data } = await axios.post<FieldRulesPreview>(`/api/dossier-types/${encodeURIComponent(code)}/evaluate-rules`, { fields })
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
  documentTemplateKeys?: string[]
  pieceTypes?: PieceType[]
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
    clearSlaTargetHours: boolean
    documentTemplateKeys: string[]
    pieceTypes: PieceType[]
  }>,
): Promise<DossierType> {
  const { data } = await axios.patch<DossierType>(`/api/dossier-types/${code}`, payload)
  return data
}

export async function listDossierTypeDocumentTemplates(code: string): Promise<AllowedDocumentTemplate[]> {
  const { data } = await axios.get<AllowedDocumentTemplate[]>(`/api/dossier-types/${code}/document-templates`)
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

/** TODO §113 (D-MAILROOM-REPLY-WORKFLOW) — workflow « réponse » d'un type : ce qui démarre quand une réponse rejoint un dossier. */
export type ReplyWorkflowMode = 'SUR_DOSSIER' | 'DOSSIER_ENFANT'

export interface ReplyWorkflow {
  dossierTypeCode: string
  mode: ReplyWorkflowMode
  workflowKey: string
  childTypeCode: string | null
}

function replyWorkflowPath(code: string): string {
  return `/api/dossier-types/${encodeURIComponent(code)}/reply-workflow`
}

/** `null` quand le type n'en déclare pas (réponse 204). */
export async function getReplyWorkflow(code: string): Promise<ReplyWorkflow | null> {
  const { data, status } = await axios.get<ReplyWorkflow | ''>(replyWorkflowPath(code))
  return status === 204 || !data ? null : data
}

export async function setReplyWorkflow(
  code: string,
  payload: { mode: ReplyWorkflowMode; workflowKey: string; childTypeCode: string | null },
): Promise<ReplyWorkflow> {
  const { data } = await axios.put<ReplyWorkflow>(replyWorkflowPath(code), payload)
  return data
}

export async function clearReplyWorkflow(code: string): Promise<void> {
  await axios.delete(replyWorkflowPath(code))
}
