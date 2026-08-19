import axios from 'axios'

export type PurgeOutboxStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
export type PurgeOutboxOrigin = 'RETENTION' | 'ADMIN'

export interface PurgeOutboxItem {
  id: string
  dossierId: string
  status: PurgeOutboxStatus
  origin: PurgeOutboxOrigin
  eventType: string
  createdAt: string
  lastError: string | null
}

export interface PurgeOutboxCounts {
  pending: number
  processing: number
  failed: number
  completed: number
}

export interface PurgeOutboxList {
  items: PurgeOutboxItem[]
  counts: PurgeOutboxCounts
}

export async function listPurgeOutbox(params: {
  status?: PurgeOutboxStatus
  limit?: number
} = {}): Promise<PurgeOutboxList> {
  const query: Record<string, string | number> = {}
  if (params.status) query.status = params.status
  if (params.limit != null) query.limit = params.limit
  const { data } = await axios.get<PurgeOutboxList>('/api/purge-outbox', { params: query })
  return data
}

export async function enqueueDossierPurge(dossierId: string): Promise<void> {
  await axios.post(`/api/dossiers/${dossierId}/purge-requests`)
}
