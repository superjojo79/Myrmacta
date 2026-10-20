import axios from 'axios'
import type { TenantIncidentsPage, WorkflowIncident } from './types'

/** TODO §94 — échecs des jobs d'une exécution ; `message` présent pour les administrateurs seulement. */
export async function listLinkIncidents(linkId: string): Promise<WorkflowIncident[]> {
  const { data } = await axios.get<WorkflowIncident[]>(
    `/api/workflow-links/${encodeURIComponent(linkId)}/incidents`,
  )
  return data
}

/** Rejoue un incident (dead letter) — TENANT_ADMIN / WORKFLOW_ADMIN. */
export async function retryIncident(linkId: string, jobId: string): Promise<void> {
  await axios.post(
    `/api/workflow-links/${encodeURIComponent(linkId)}/incidents/${encodeURIComponent(jobId)}/retry`,
  )
}

/** Supervision : échecs du tenant, les plus récents d'abord, bornés côté serveur (`truncated`). */
export async function listTenantIncidents(
  params: { page?: number; size?: number } = {},
): Promise<TenantIncidentsPage> {
  const { data } = await axios.get<TenantIncidentsPage>('/api/admin/incidents', { params })
  return data
}
