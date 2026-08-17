import axios from 'axios'
import { resolveApiBaseUrl } from './http'
import type { DossierElementView } from './types'

export async function uploadAttachment(
  dossierId: string,
  file: File,
  label: string,
): Promise<DossierElementView> {
  const form = new FormData()
  form.append('file', file)
  form.append('label', label)
  const { data } = await axios.post<DossierElementView>(
    `/api/dossiers/${dossierId}/elements/attachment`,
    form,
    { headers: { 'Content-Type': 'multipart/form-data' } },
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
