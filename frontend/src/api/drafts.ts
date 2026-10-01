import axios from 'axios'

/** TODO §82 (D6) — brouillons de configuration (BPMN, DMN) en attente de publication. */
export type DraftArtifactType = 'BPMN' | 'DMN'
export type Provenance = 'UI' | 'MCP' | 'ASSISTANT'

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
}

export interface ConfigPublication {
  id: string
  artifactType: DraftArtifactType
  artifactKey: string
  version: number | null
  draftProvenance: Provenance
  draftAuthor: string
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

export async function discardDraft(type: DraftArtifactType, key: string): Promise<void> {
  await axios.delete(`/api/admin/drafts/${type}/${encodeURIComponent(key)}`)
}
