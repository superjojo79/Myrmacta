import axios from 'axios'

// TODO §68 (portail-externe) — API instructeur des liens portail d'un dossier (chaîne BFF).

export type PortalLinkStatus = 'PENDING_SEND' | 'ACTIVE' | 'NOT_SENT' | 'COMPLETED' | 'EXPIRED' | 'REVOKED'

export interface PortalLinkView {
  id: string
  taskId: string
  status: PortalLinkStatus
  statusReason: string | null
  recipient: string | null
  ttlDays: number
  createdAt: string
  sentAt: string | null
  expiresAt: string | null
  lastAccessedAt: string | null
  completedAt: string | null
  revokedAt: string | null
  createdBy: string
  revokedBy: string | null
}

export interface PortalLinksResponse {
  canManage: boolean
  links: PortalLinkView[]
}

export async function listPortalLinks(dossierId: string): Promise<PortalLinksResponse> {
  const { data } = await axios.get<PortalLinksResponse>(`/api/dossiers/${encodeURIComponent(dossierId)}/portal-links`)
  return data
}

/** Envoyer / renvoyer : un 409 porte le lien NOT_SENT créé (adresse toujours invalide). */
export async function sendPortalLink(dossierId: string, taskId: string): Promise<PortalLinkView> {
  const { data } = await axios.post<PortalLinkView>(
    `/api/dossiers/${encodeURIComponent(dossierId)}/portal-links/tasks/${encodeURIComponent(taskId)}/send`,
    undefined,
    { validateStatus: (status) => status === 201 || status === 409 },
  )
  return data
}

export async function revokePortalLink(dossierId: string, linkId: string): Promise<PortalLinkView> {
  const { data } = await axios.post<PortalLinkView>(
    `/api/dossiers/${encodeURIComponent(dossierId)}/portal-links/${encodeURIComponent(linkId)}/revoke`,
  )
  return data
}
