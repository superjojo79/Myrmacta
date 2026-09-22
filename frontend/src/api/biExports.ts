import axios from 'axios'
import type { Page } from './page'

export interface BiExportFile {
  id: string
  kind: 'DOSSIERS' | 'SLA'
  dossierTypeCode: string | null
  fileName: string
  createdAt: string
  sizeBytes: number
}

export async function listBiExports(page = 0, size = 20): Promise<Page<BiExportFile>> {
  const { data } = await axios.get<Page<BiExportFile>>('/api/reports/bi-exports', {
    params: { page, size },
  })
  return data
}

export async function downloadBiExport(file: BiExportFile): Promise<void> {
  const { data } = await axios.get<Blob>(`/api/reports/bi-exports/${file.id}`, { responseType: 'blob' })
  const url = URL.createObjectURL(data)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = file.fileName
  anchor.click()
  URL.revokeObjectURL(url)
}
