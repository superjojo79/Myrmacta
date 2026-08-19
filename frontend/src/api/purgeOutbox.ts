import axios from 'axios'
import type { Page } from './page'

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

export interface PurgeOutboxList extends Page<PurgeOutboxItem> {
  counts: PurgeOutboxCounts
}

export async function listPurgeOutbox(
  params: {
    status?: PurgeOutboxStatus
    page?: number
    size?: number
  } = {},
): Promise<PurgeOutboxList> {
  const query: Record<string, string | number> = {}
  if (params.status) query.status = params.status
  if (params.page != null) query.page = params.page
  if (params.size != null) query.size = params.size
  const { data } = await axios.get<PurgeOutboxList>('/api/purge-outbox', { params: query })
  return data
}

export async function enqueueDossierPurge(dossierId: string): Promise<void> {
  await axios.post(`/api/dossiers/${dossierId}/purge-requests`)
}
