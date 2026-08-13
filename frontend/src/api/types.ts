export interface AuthUser {
  id: string
  name: string | null
  email: string | null
  roles: string[]
}

export type DossierStatus =
  | 'DRAFT'
  | 'OUVERT'
  | 'EN_COURS'
  | 'SUSPENDU'
  | 'CLOTURE'
  | 'ARCHIVE'

export type FieldType = 'STRING' | 'NUMBER' | 'DATE' | 'BOOLEAN' | 'SELECT' | 'FILE'

export interface FieldDefinition {
  name: string
  label: string
  type: FieldType
  required: boolean
  defaultValue?: string | null
  options?: string[]
  group?: string | null
  sensitive?: boolean
}

export interface DossierType {
  id: string
  tenantId: string
  code: string
  label: string
  workflowKey: string
  active: boolean
  allowUnknownFields: boolean
  fieldDefinitions: FieldDefinition[]
}

export interface Dossier {
  id: string
  tenantId: string
  reference: string
  dossierTypeCode: string
  label: string
  status: DossierStatus
  fields: Record<string, unknown>
  currentWorkflowLinkId: string | null
  version: number
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
  closedBy: string | null
  closedAt: string | null
}

export interface DossierListItem {
  id: string
  reference: string
  label: string
  dossierTypeCode: string
  dossierTypeLabel: string
  status: DossierStatus
  createdAt: string
  updatedAt: string
}

export interface DossierPage {
  content: DossierListItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface DossierListParams {
  page?: number
  size?: number
  sort?: string
  status?: DossierStatus | ''
  dossierTypeCode?: string
  reference?: string
}

export interface WorkflowLink {
  id: string
  dossierId: string
  tenantId: string
  processInstanceId: string | null
  processDefinitionKey: string
  sequence: number
  status: string
  triggerRef: string | null
  startedAt: string | null
  endedAt: string | null
  endReason: string | null
  startedBy: string | null
}

export interface WorkflowDiagram {
  bpmnXml: string
  activeIds: string[]
  completedIds: string[]
  failedIds: string[]
}

export interface WorkflowTaskView {
  taskId: string
  name: string | null
  assignee: string | null
  taskDefinitionKey: string | null
  createTime: string | null
}

export interface ActivityHistoryEntry {
  activityId: string
  name: string | null
  assignee: string | null
  startTime: string
  endTime: string | null
  durationMs: number | null
}

export interface MyWorkflowTask {
  taskId: string
  name: string | null
  assigneeId: string | null
  createdAt: string | null
  processInstanceId: string | null
  dossierId: string
  dossierReference: string | null
  linkId: string | null
}

export type ElementType = 'PRODUCED_DOC' | 'ATTACHMENT' | 'NOTE' | 'EXTERNAL_REF'

export interface DossierElementView {
  id: string
  type: ElementType
  label: string
  addedBy: string
  addedAt: string
  workflowStepId: string | null
  metadata: Record<string, unknown>
  downloadable: boolean
}

export interface WorkflowTypeConfig {
  id: string
  tenantId: string
  workflowKey: string
  label: string
  statusDefinitions: Array<{
    code: string
    label: string
    initial: boolean
    outcome: string
  }>
  allowedDossierStatuses: DossierStatus[]
  onStartDossierStatus: DossierStatus | null
  observerUserIds: string[]
}

export interface DocumentTemplate {
  id: string
  templateKey: string
  variantKey: string | null
  locale: string | null
  label: string
  version: number
  status: string
  htmlContent: string | null
  createdAt: string
  updatedAt: string
  updatedBy: string | null
}

export interface Connector {
  id: string
  tenantId: string
  name: string
  connectorType: string
  enabled: boolean
  config: Record<string, unknown>
  secretConfigured: Record<string, boolean>
}

export type DossierPermission = 'VIEW' | 'EDIT'

export const DOSSIER_STATUSES: DossierStatus[] = [
  'DRAFT',
  'OUVERT',
  'EN_COURS',
  'SUSPENDU',
  'CLOTURE',
  'ARCHIVE',
]
