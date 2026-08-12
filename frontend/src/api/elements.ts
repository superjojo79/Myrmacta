import axios from 'axios'
import { resolveApiBaseUrl } from './http'

export function elementContentUrl(elementId: string): string {
  const prefix = resolveApiBaseUrl()
  return `${prefix}/api/dossier-elements/${elementId}/content`
}

export async function downloadElementContent(elementId: string): Promise<Blob> {
  const { data } = await axios.get<Blob>(`/api/dossier-elements/${elementId}/content`, {
    responseType: 'blob',
  })
  return data
}
