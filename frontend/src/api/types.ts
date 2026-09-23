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
  retentionDays?: number | null
  retentionNever?: boolean
  slaTargetHours?: number | null
  documentTemplateKeys?: string[]
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
  purgeEligibleAt?: string | null
  canEdit: boolean
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

export type { Page } from './page'

export type RelationType =
  | 'PARENT_CHILD'
  | 'SUBSEQUENT'
  | 'LINKED'
  | 'DUPLICATE'
  | 'MERGED_INTO'

export type RelationDirection = 'OUTGOING' | 'INCOMING'

export interface DossierRelation {
  id: string
  type: RelationType
  direction: RelationDirection
  relatedDossier: DossierListItem
  createdBy: string
  createdAt: string
}

export const RELATION_TYPES: RelationType[] = [
  'PARENT_CHILD',
  'SUBSEQUENT',
  'LINKED',
  'DUPLICATE',
  'MERGED_INTO',
]

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

export interface ProcessDeploymentInfo {
  processDefinitionKey: string
  latestVersion: number
  activeInstanceCount: number
}

export interface BpmnDraft {
  processDefinitionKey: string
  xml: string
  updatedAt: string
  updatedBy: string
}

export interface BpmnDeploymentResult {
  processDefinitionKey: string
  deployedVersion: number
  deploymentId: string
}

export interface DecisionTableDeploymentInfo {
  decisionKey: string
  latestVersion: number
}

export interface DmnDraft {
  decisionKey: string
  xml: string
  updatedAt: string
  updatedBy: string
}

export interface DecisionTableDeploymentResult {
  decisionKey: string
  deployedVersion: number
  deploymentId: string
}

export interface DecisionTableVersionInfo {
  decisionKey: string
  version: number
  deploymentId: string
  deployedAt: string
}

export interface DecisionEvaluationResult {
  decisionKey: string
  version: number
  rows: Record<string, unknown>[]
}

export interface DecisionTestCase {
  inputs: Record<string, unknown>
  expectedRows: Record<string, unknown>[]
}

export interface DecisionTestCaseResult {
  index: number
  passed: boolean
  actualRows: Record<string, unknown>[]
  failureMessage: string | null
}

export interface DecisionTestReport {
  decisionKey: string
  version: number
  results: DecisionTestCaseResult[]
}

export interface WorkflowTaskView {
  taskId: string
  name: string | null
  assignee: string | null
  taskDefinitionKey: string | null
  createTime: string | null
  formKey?: string | null
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
  slaRemainingHours?: number | null
  formKey?: string | null
}

/** TODO §52 -- picker source for the document-generation task form kind; never carries htmlContent. */
export interface AllowedDocumentTemplate {
  templateKey: string
  variantKey: string | null
  locale: string | null
  label: string
}

export type ElementType = 'PRODUCED_DOC' | 'ATTACHMENT' | 'INFORMATION' | 'NOTE' | 'LINK'

export const MANUAL_ELEMENT_TYPES: ElementType[] = ['ATTACHMENT', 'INFORMATION', 'NOTE', 'LINK']

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

export interface TaskQuestionSchema {
  id: string
  schemaKey: string
  label: string
  fieldDefinitions: FieldDefinition[]
  createdBy: string
  updatedBy: string
}

export interface TaskQuestionSchemaRuntime {
  schemaKey: string
  label: string
  fieldDefinitions: FieldDefinition[]
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
