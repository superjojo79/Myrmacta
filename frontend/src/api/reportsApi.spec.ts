import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import { downloadBiExport, listBiExports, type BiExportFile } from './biExports'
import { getOperationalCounts } from './operations'

// Clients d'API du rapport « Rapports & SLA » : exports BI et compteurs opérationnels (URL, paramètres, téléchargement).
// RapportsView.spec.ts les simule ; le contrôle de mutation des PR les mute dès que ce test de vue change (#58).
vi.mock('axios')

const file: BiExportFile = {
  id: 'f1', kind: 'SLA', dossierTypeCode: null, fileName: 'sla-2026-11.csv', createdAt: '2026-11-16T00:00:00Z', sizeBytes: 42,
}

describe('reports api clients', () => {
  beforeEach(() => {
    vi.mocked(axios.get).mockReset()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('pages the BI export files, first page of 20 by default', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: { content: [file] } })
    expect(await listBiExports()).toEqual({ content: [file] })
    expect(axios.get).toHaveBeenLastCalledWith('/api/reports/bi-exports', { params: { page: 0, size: 20 } })
    await listBiExports(3, 50)
    expect(axios.get).toHaveBeenLastCalledWith('/api/reports/bi-exports', { params: { page: 3, size: 50 } })
  })

  it('downloads a BI export as a blob, under its own file name, then frees the object URL', async () => {
    const blob = new Blob(['a;b'])
    vi.mocked(axios.get).mockResolvedValue({ data: blob })
    const createObjectURL = vi.fn(() => 'blob:sla')
    const revokeObjectURL = vi.fn()
    vi.stubGlobal('URL', { ...URL, createObjectURL, revokeObjectURL })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      expect(this.href).toBe('blob:sla')
      expect(this.download).toBe('sla-2026-11.csv')
    })
    try {
      await downloadBiExport(file)
    } finally {
      vi.unstubAllGlobals()
    }
    expect(axios.get).toHaveBeenCalledWith('/api/reports/bi-exports/f1', { responseType: 'blob' })
    expect(createObjectURL).toHaveBeenCalledWith(blob)
    expect(click).toHaveBeenCalledOnce()
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:sla')
  })

  it('reads the operational counts', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: { deadLetterJobCount: 1, unassignedOpenTaskCount: 5 } })
    expect(await getOperationalCounts()).toEqual({ deadLetterJobCount: 1, unassignedOpenTaskCount: 5 })
    expect(axios.get).toHaveBeenCalledWith('/api/reports/operations')
  })
})
