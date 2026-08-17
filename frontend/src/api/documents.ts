import axios from 'axios'

export type PreviewPayload = {
  htmlContent: string
  variables?: Record<string, unknown>
}

export async function previewHtml(payload: PreviewPayload, signal?: AbortSignal): Promise<string> {
  const { data } = await axios.post<string>('/api/documents/preview', payload, {
    responseType: 'text',
    signal,
  })
  return data
}

export async function previewPdf(payload: PreviewPayload): Promise<Blob> {
  const { data } = await axios.post<Blob>('/api/documents/preview/pdf', payload, {
    responseType: 'blob',
  })
  return data
}
