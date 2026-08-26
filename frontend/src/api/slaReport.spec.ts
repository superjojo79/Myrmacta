import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import { downloadSlaReportCsv, getSlaReport } from './slaReport'

vi.mock('axios')

describe('slaReport api client', () => {
  beforeEach(() => {
    vi.mocked(axios.get).mockReset()
  })

  it('GETs snapshot with optional period and type filter', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: { compliancePercent: 94.2 } })
    await getSlaReport({
      from: '2026-03-01T00:00:00Z',
      to: '2026-03-15T00:00:00Z',
      dossierTypeCode: 'CLAIM',
    })
    expect(axios.get).toHaveBeenCalledWith('/api/reports/sla', {
      params: {
        from: '2026-03-01T00:00:00Z',
        to: '2026-03-15T00:00:00Z',
        dossierTypeCode: 'CLAIM',
      },
    })
  })

  it('downloads CSV as a blob with the same filters', async () => {
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:sla'),
      revokeObjectURL: vi.fn(),
    })
    const click = vi.fn()
    vi.spyOn(document, 'createElement').mockReturnValue({
      click,
      href: '',
      download: '',
    } as unknown as HTMLAnchorElement)
    vi.mocked(axios.get).mockResolvedValue({ data: new Blob(['csv']) })

    await downloadSlaReportCsv({ from: '2026-03-01', dossierTypeCode: 'CLAIM' })

    expect(axios.get).toHaveBeenCalledWith('/api/reports/sla.csv', {
      params: { from: '2026-03-01', dossierTypeCode: 'CLAIM' },
      responseType: 'blob',
    })
    expect(click).toHaveBeenCalled()
    vi.unstubAllGlobals()
  })
})
