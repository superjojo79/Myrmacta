import axios from 'axios'
import { resolveApiBaseUrl } from './http'
import type { DossierElementView } from './types'

export async function uploadAttachment(
  dossierId: string,
  file: File,
  label: string,
  pieceKey?: string | null,
): Promise<DossierElementView> {
  const form = new FormData()
  form.append('file', file)
  form.append('label', label)
  if (pieceKey) form.append('pieceKey', pieceKey)
  const { data } = await axios.post<DossierElementView>(
    `/api/dossiers/${dossierId}/elements/attachment`,
    form,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  )
  return data
}

/** TODO §105 — attribuer, changer ou retirer (`null`) le type d'une pièce jointe (droit EDIT). */
export async function classifyAttachment(
  dossierId: string,
  elementId: string,
  pieceKey: string | null,
): Promise<DossierElementView> {
  const { data } = await axios.put<DossierElementView>(
    `/api/dossiers/${dossierId}/elements/${elementId}/piece-type`,
    { pieceKey },
  )
  return data
}

export function elementContentUrl(elementId: string): string {
  const prefix = resolveApiBaseUrl()
  const id = encodeURIComponent(elementId)
  return `${prefix}/api/dossier-elements/${id}/content`
}

export async function downloadElementContent(elementId: string): Promise<Blob> {
  const id = encodeURIComponent(elementId)
  const { data } = await axios.get<Blob>(`/api/dossier-elements/${id}/content`, {
    responseType: 'blob',
  })
  return data
}
