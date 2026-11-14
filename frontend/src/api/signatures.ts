import axios from 'axios'

// TODO §72 (signature-electronique) — API instructeur des demandes de signature d'un dossier et
// « Mes signatures » de l'utilisateur connecté (chaîne BFF).

export type SignatureStatus = 'PENDING' | 'SIGNED' | 'DECLINED' | 'EXPIRED' | 'FAILED' | 'CANCELLED'
export type SignerStatus = 'WAITING' | 'PENDING' | 'SIGNED' | 'DECLINED' | 'EXPIRED' | 'REVOKED'

export interface SignerView {
  id: string
  position: number
  kind: 'EXTERNAL' | 'INTERNAL'
  required: boolean
  displayName: string
  /** Adresse masquée ou identifiant utilisateur ; absent sans droit de gestion. */
  contact: string | null
  status: SignerStatus
  decidedAt: string | null
  declineReason: string | null
  invited: boolean
}

export interface SignatureRequestView {
  id: string
  status: SignatureStatus
  statusReason: string | null
  mode: 'PARALLEL' | 'SEQUENTIAL'
  simulated: boolean
  providerCode: string
  sourceElementId: string
  deadlineAt: string | null
  createdAt: string
  completedAt: string | null
  canManage: boolean
  signers: SignerView[]
}

export interface SignatureRequestsResponse {
  canManage: boolean
  requests: SignatureRequestView[]
}

export interface MySignatureItem {
  signerId: string
  dossierId: string
  dossierReference: string
  documentLabel: string
  required: boolean
  myTurn: boolean
  deadlineAt: string | null
  simulated: boolean
  hosted: boolean
  /** Adresse du fournisseur, seulement quand c'est son tour ; nulle en attente du lien. */
  signingUrl: string | null
  /** Issue #10 — signature apportée : télécharger le PDF, le signer avec sa carte eID, téléverser le résultat. */
  signedUpload: boolean
}

/** Navigation vers la cérémonie du fournisseur (isolée pour les tests). */
export function openSigningPage(url: string): void {
  window.location.assign(url)
}

export async function listSignatureRequests(dossierId: string): Promise<SignatureRequestsResponse> {
  const { data } = await axios.get<SignatureRequestsResponse>(
    `/api/dossiers/${encodeURIComponent(dossierId)}/signature-requests`,
  )
  return data
}

export async function cancelSignatureRequest(requestId: string): Promise<SignatureRequestView> {
  const { data } = await axios.post<SignatureRequestView>(
    `/api/signature-requests/${encodeURIComponent(requestId)}/cancel`,
  )
  return data
}

export async function resendSignatureInvite(requestId: string, signerId: string): Promise<SignatureRequestView> {
  const { data } = await axios.post<SignatureRequestView>(
    `/api/signature-requests/${encodeURIComponent(requestId)}/signers/${encodeURIComponent(signerId)}/resend`,
  )
  return data
}

export async function listMySignatures(): Promise<MySignatureItem[]> {
  const { data } = await axios.get<MySignatureItem[]>('/api/signatures/mine')
  return data
}

/** Lien demandé au clic, pour ne pas appeler le fournisseur une fois par ligne de la liste. */
export async function fetchMySigningUrl(signerId: string): Promise<string | null> {
  const { data } = await axios.get<{ signingUrl: string | null }>(
    `/api/signatures/mine/${encodeURIComponent(signerId)}/signing-url`,
  )
  return data.signingUrl
}

export async function fetchMySignatureDocument(signerId: string): Promise<Blob> {
  const { data } = await axios.get<Blob>(`/api/signatures/mine/${encodeURIComponent(signerId)}/document`, {
    responseType: 'blob',
  })
  return data
}

/** Issue #10 — téléverse le PDF signé (multipart, partie `file`) ; un refus 422 porte son motif dans `code` (`toProblemDetail`). */
export async function uploadMySignedDocument(signerId: string, file: File): Promise<{ status: SignerStatus }> {
  const body = new FormData()
  body.append('file', file)
  const { data } = await axios.post<{ status: SignerStatus }>(
    `/api/signatures/mine/${encodeURIComponent(signerId)}/signed-document`,
    body,
  )
  return data
}

export async function signMine(signerId: string): Promise<{ status: SignerStatus }> {
  const { data } = await axios.post<{ status: SignerStatus }>(`/api/signatures/mine/${encodeURIComponent(signerId)}/sign`)
  return data
}

export async function declineMine(signerId: string, reason: string): Promise<{ status: SignerStatus }> {
  const { data } = await axios.post<{ status: SignerStatus }>(
    `/api/signatures/mine/${encodeURIComponent(signerId)}/decline`,
    { reason: reason.trim() || null },
  )
  return data
}
