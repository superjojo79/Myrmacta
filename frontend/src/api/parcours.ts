import axios from 'axios'

/** TODO §88 (LC5) — concepteur de parcours : une suite d'étapes compilée en BPMN (sens unique), déposée en brouillon. */
export type StepKind =
  | 'QUESTION' | 'EXTERNAL' | 'DOCUMENT' | 'NOTIFY' | 'CONNECTOR' | 'DMN' | 'EXPRESSION' | 'MAP_FIELDS'
  | 'SET_WORKFLOW_STATUS' | 'SET_DOSSIER_STATUS' | 'WAIT' | 'DECISION'

export type Operator = 'EQ' | 'NE' | 'GT' | 'GE' | 'LT' | 'LE'

export interface ParcoursBranch {
  variable: string
  operator: Operator
  value: string
  next: string
}

export interface ParcoursStep {
  id: string
  label?: string | null
  kind: StepKind
  params: Record<string, string>
  next?: string | null
  branches?: ParcoursBranch[]
  defaultNext?: string | null
}

export interface ParcoursStatus {
  code: string
  label: string
  initial: boolean
  outcome: 'NONE' | 'COMPLETED' | 'CANCELLED' | string
}

export interface ParcoursDefinition {
  statuses: ParcoursStatus[]
  allowedDossierStatuses: string[]
  onStartDossierStatus: string | null
  steps: ParcoursStep[]
}

export interface ParcoursSummary {
  key: string
  label: string
  version: number
  steps: number
  updatedAt: string
  updatedBy: string
  lastCompiledAt?: string | null
}

export interface ParcoursView {
  key: string
  label: string
  version: number
  definition: ParcoursDefinition
  updatedAt: string
  updatedBy: string
  lastCompiledAt?: string | null
  lastCompiledSha256?: string | null
}

export interface CompileResult {
  xml: string
  sha256: string
  warnings: string[]
  draftWritten: boolean
  replacedExistingDraft: boolean
  /** Aperçu : pourquoi le déploiement refuserait ce BPMN (absent s'il l'accepterait). */
  problem?: string | null
}

export async function listParcours(): Promise<ParcoursSummary[]> {
  const { data } = await axios.get<ParcoursSummary[]>('/api/parcours')
  return data
}

export async function getParcours(key: string): Promise<ParcoursView> {
  const { data } = await axios.get<ParcoursView>(`/api/parcours/${encodeURIComponent(key)}`)
  return data
}

export async function saveParcours(
  key: string,
  label: string,
  definition: ParcoursDefinition,
  expectedVersion: number | null,
): Promise<ParcoursView> {
  const { data } = await axios.put<ParcoursView>(`/api/parcours/${encodeURIComponent(key)}`, { label, definition, expectedVersion })
  return data
}

export async function deleteParcours(key: string): Promise<void> {
  await axios.delete(`/api/parcours/${encodeURIComponent(key)}`)
}

export async function compileParcours(key: string, dryRun: boolean, replaceDraft = false): Promise<CompileResult> {
  const { data } = await axios.post<CompileResult>(`/api/parcours/${encodeURIComponent(key)}/compile`, { dryRun, replaceDraft })
  return data
}

export async function previewParcours(key: string, label: string, definition: ParcoursDefinition): Promise<CompileResult> {
  const { data } = await axios.post<CompileResult>('/api/parcours/preview', { key, label, definition })
  return data
}
