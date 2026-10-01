import axios from 'axios'

/** TODO §83 (D4) — promotion de configuration entre environnements (export ZIP, plan à blanc, application). */
export type PlanAction = 'CREATE' | 'UPDATE' | 'UNCHANGED' | 'CONFLICT' | 'BLOCKED'
export type ImportOutcome = 'APPLIED' | 'UNCHANGED' | 'SKIPPED' | 'FAILED'
export type ArtifactType =
  | 'DOSSIER_TYPE'
  | 'TASK_QUESTION_SCHEMA'
  | 'CONNECTOR_INSTANCE'
  | 'DMN'
  | 'BPMN'
  | 'WORKFLOW_CONFIG'

export interface PlanEntry {
  type: ArtifactType
  key: string
  action: PlanAction
  reason: string
  /** Connecteur : noms des champs secrets à fournir à l'application (jamais leurs valeurs). */
  secretsRequired: string[]
}

export interface ImportPlan {
  bundleSha256: string
  formatVersion: number
  sourceLabel: string
  entries: PlanEntry[]
}

export interface ReportEntry {
  type: ArtifactType
  key: string
  outcome: ImportOutcome
  message: string
}

export interface ImportReport {
  bundleSha256: string
  performedAt: string
  entries: ReportEntry[]
}

export interface ConfigImportRecord {
  id: string
  performedBy: string
  performedAt: string
  channel: string
  bundleSha256: string
  sourceLabel: string
  applied: number
  unchanged: number
  skipped: number
  failed: number
}

export const ARTIFACT_LABELS: Record<ArtifactType, string> = {
  DOSSIER_TYPE: 'Type de dossier',
  TASK_QUESTION_SCHEMA: 'Schéma de questions',
  CONNECTOR_INSTANCE: 'Connecteur',
  DMN: 'Table DMN',
  BPMN: 'Processus BPMN',
  WORKFLOW_CONFIG: 'Config. de workflow',
}

/** URL de téléchargement du bundle (ZIP) : le navigateur gère le téléchargement et le cookie de session. */
export function exportUrl(sourceLabel: string): string {
  const query = sourceLabel.trim() ? `?sourceLabel=${encodeURIComponent(sourceLabel.trim())}` : ''
  return `/api/admin/configuration/export${query}`
}

function form(file: File, extra: Record<string, string | undefined>): FormData {
  const data = new FormData()
  data.append('file', file)
  for (const [name, value] of Object.entries(extra)) {
    if (value) data.append(name, value)
  }
  return data
}

export async function planImport(file: File, parameters: Record<string, string>): Promise<ImportPlan> {
  const { data } = await axios.post<ImportPlan>(
    '/api/admin/configuration/plan',
    form(file, { parameters: Object.keys(parameters).length ? JSON.stringify(parameters) : undefined }),
  )
  return data
}

/**
 * Applique le bundle planifié. `secrets` : `{ nomDuConnecteur: { champ: valeur } }` — transmis dans cette seule requête, jamais
 * conservés côté client ; `expectedBundleSha256` : somme du plan relu (l'application refuse un autre bundle).
 */
export async function applyImport(
  file: File,
  options: {
    parameters: Record<string, string>
    secrets: Record<string, Record<string, string>>
    expectedBundleSha256: string
  },
): Promise<ImportReport> {
  const { data } = await axios.post<ImportReport>(
    '/api/admin/configuration/apply',
    form(file, {
      parameters: Object.keys(options.parameters).length ? JSON.stringify(options.parameters) : undefined,
      secrets: Object.keys(options.secrets).length ? JSON.stringify(options.secrets) : undefined,
      expectedBundleSha256: options.expectedBundleSha256,
    }),
  )
  return data
}

export async function listImports(limit = 10): Promise<ConfigImportRecord[]> {
  const { data } = await axios.get<ConfigImportRecord[]>('/api/admin/configuration/imports', { params: { limit } })
  return data
}

/** Lit un JSON `{ nom: valeur }` saisi par l'utilisateur ; `null` si invalide. */
export function parseParameters(text: string): Record<string, string> | null {
  const trimmed = text.trim()
  if (!trimmed) return {}
  try {
    const value: unknown = JSON.parse(trimmed)
    if (value === null || typeof value !== 'object' || Array.isArray(value)) return null
    const out: Record<string, string> = {}
    for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
      if (typeof v !== 'string') return null
      out[k] = v
    }
    return out
  } catch {
    return null
  }
}
