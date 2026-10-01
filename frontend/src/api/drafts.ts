import axios from 'axios'

/** TODO §82 (D6) — brouillons de configuration (BPMN, DMN) en attente de publication. */
export type DraftArtifactType = 'BPMN' | 'DMN'
export type Provenance = 'UI' | 'MCP' | 'ASSISTANT' | 'IMPORT'

export interface ProposedWorkflowConfig {
  label: string
  statusDefinitions: Array<{ code: string; label: string; initial: boolean; outcome: string }>
  allowedDossierStatuses: string[]
  onStartDossierStatus: string | null
  observerUserIds: string[]
}

export interface PendingDraft {
  type: DraftArtifactType
  key: string
  provenance: Provenance
  author: string
  updatedAt: string
  hasProposedConfig: boolean
  note: string | null
  publishedVersion: number | null
  addedLines: number
  removedLines: number
  /** Configuration de workflow qui sera appliquée à la publication — à relire avec le diagramme. */
  proposedConfig: ProposedWorkflowConfig | null
  configProvenance: Provenance | null
  configAuthor: string | null
}

export interface ConfigPublication {
  id: string
  artifactType: DraftArtifactType
  artifactKey: string
  version: number | null
  draftProvenance: Provenance
  draftAuthor: string
  configProvenance?: Provenance | null
  configAuthor?: string | null
  publishedBy: string
  publishedAt: string
  mode: 'MANUAL' | 'DIRECT'
}

export async function listPendingDrafts(): Promise<PendingDraft[]> {
  const { data } = await axios.get<PendingDraft[]>('/api/admin/drafts')
  return data
}

export async function listPublications(limit = 20): Promise<ConfigPublication[]> {
  const { data } = await axios.get<ConfigPublication[]>('/api/admin/drafts/publications', { params: { limit } })
  return data
}

/** Publie exactement le brouillon relu : `expectedUpdatedAt` est l'horodatage affiché (409 s'il a changé entre-temps). */
export async function publishDraft(
  type: DraftArtifactType,
  key: string,
  expectedUpdatedAt: string,
): Promise<ConfigPublication> {
  const { data } = await axios.post<ConfigPublication>(
    `/api/admin/drafts/${type}/${encodeURIComponent(key)}/publish`,
    { expectedUpdatedAt },
  )
  return data
}

/** Écarte le brouillon vu : `expectedUpdatedAt` protège contre un brouillon remplacé entre-temps (409). */
export async function discardDraft(type: DraftArtifactType, key: string, expectedUpdatedAt: string): Promise<void> {
  await axios.delete(`/api/admin/drafts/${type}/${encodeURIComponent(key)}`, { params: { expectedUpdatedAt } })
}
