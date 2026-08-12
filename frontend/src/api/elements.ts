import axios from 'axios'
import { resolveApiBaseUrl } from './http'

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
