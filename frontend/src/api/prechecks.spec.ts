import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import { decidePrecheckFinding, downloadPrecheckExport, listPrechecks, rerunPrecheck } from './prechecks'

vi.mock('axios')

describe('prechecks api (TODO §106, lot C)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('lists the runs of a dossier and reads a 404 as a disabled feature', async () => {
    vi.mocked(axios.get).mockResolvedValueOnce({ status: 200, data: [{ id: 'r1' }] })
    await expect(listPrechecks('d 1')).resolves.toEqual([{ id: 'r1' }])
    const [url, config] = vi.mocked(axios.get).mock.calls[0]!
    expect(url).toBe('/api/dossiers/d%201/prechecks')
    const validate = (config as { validateStatus: (s: number) => boolean }).validateStatus
    expect([200, 404].map(validate)).toEqual([true, true])
    expect([403, 500].map(validate)).toEqual([false, false])

    vi.mocked(axios.get).mockResolvedValueOnce({ status: 404, data: { title: 'Pre-check not found' } })
    await expect(listPrechecks('d1')).resolves.toBeNull()
  })

  it('reruns, decides and undoes a decision', async () => {
    vi.mocked(axios.post).mockResolvedValue({ data: { id: 'r2', status: 'PENDING' } })
    await expect(rerunPrecheck('d1')).resolves.toEqual({ id: 'r2', status: 'PENDING' })
    expect(axios.post).toHaveBeenCalledWith('/api/dossiers/d1/prechecks')

    vi.mocked(axios.put).mockResolvedValue({ data: { id: 'f1' } })
    await decidePrecheckFinding('d1', 'r1', 'f/1', 'ACCEPTED')
    expect(axios.put).toHaveBeenLastCalledWith('/api/dossiers/d1/prechecks/r1/findings/f%2F1/decision', {
      decision: 'ACCEPTED',
    })
    await decidePrecheckFinding('d1', 'r1', 'f1', null)
    expect(axios.put).toHaveBeenLastCalledWith('/api/dossiers/d1/prechecks/r1/findings/f1/decision', { decision: null })
  })

  it('saves the exported log as JSON', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: { inputSha256: 'abc' } })
    const click = vi.fn()
    const anchor = { click, href: '', download: '' }
    vi.spyOn(document, 'createElement').mockReturnValue(anchor as unknown as HTMLAnchorElement)
    URL.createObjectURL = vi.fn(() => 'blob:x')
    URL.revokeObjectURL = vi.fn()

    await downloadPrecheckExport('d1', 'r1')

    expect(axios.get).toHaveBeenCalledWith('/api/dossiers/d1/prechecks/r1/export')
    expect(anchor.download).toBe('pre-verification-r1.json')
    expect(click).toHaveBeenCalled()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:x')
  })
})
