import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import { applyImport, downloadExport, listImports, parseParameters, planImport } from './configuration'

vi.mock('axios')

const file = new File(['zip'], 'bundle.zip', { type: 'application/zip' })

describe('configuration api (TODO §83, D4)', () => {
  beforeEach(() => vi.resetAllMocks())

  it('downloads the ZIP through axios (blob) with the label as a query parameter and a safe file name', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: new Blob(['zip']) })
    const created = vi.fn(() => 'blob:x')
    const revoked = vi.fn()
    Object.assign(URL, { createObjectURL: created, revokeObjectURL: revoked })
    const names: string[] = []
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      names.push(this.download)
    })
    await downloadExport(' rec & co ')
    expect(axios.get).toHaveBeenCalledWith('/api/admin/configuration/export', {
      params: { sourceLabel: 'rec & co' },
      responseType: 'blob',
    })
    expect(names).toEqual(['myrmacta-config-rec-co.zip'])
    expect(click).toHaveBeenCalled()
    expect(created).toHaveBeenCalled()
    expect(revoked).toHaveBeenCalledWith('blob:x')
    await downloadExport('')
    expect(names).toEqual(['myrmacta-config-rec-co.zip', 'myrmacta-config.zip'])
    expect(vi.mocked(axios.get).mock.calls[1]![1]).toEqual({ params: undefined, responseType: 'blob' })
    click.mockRestore()
  })

  it('plans with the file and the parameters only when there are some', async () => {
    vi.mocked(axios.post).mockResolvedValue({ data: { entries: [] } })
    await planImport(file, { pk: 'k1' })
    const sent = vi.mocked(axios.post).mock.calls[0]![1] as FormData
    expect(vi.mocked(axios.post).mock.calls[0]![0]).toBe('/api/admin/configuration/plan')
    expect(sent.get('file')).toBeInstanceOf(File)
    expect(sent.get('parameters')).toBe('{"pk":"k1"}')
    await planImport(file, {})
    expect((vi.mocked(axios.post).mock.calls[1]![1] as FormData).has('parameters')).toBe(false)
  })

  it('applies with the reviewed checksum and sends secrets in that single request', async () => {
    vi.mocked(axios.post).mockResolvedValue({ data: { entries: [] } })
    await applyImport(file, {
      parameters: {},
      secrets: { 'Mon connecteur': { webhookSecret: 's3cret' } },
      expectedBundleSha256: 'abc',
    })
    const sent = vi.mocked(axios.post).mock.calls[0]![1] as FormData
    expect(sent.get('expectedBundleSha256')).toBe('abc')
    expect(sent.get('secrets')).toBe('{"Mon connecteur":{"webhookSecret":"s3cret"}}')
    expect(sent.has('parameters')).toBe(false)
  })

  it('lists the journal', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: [] })
    await listImports(5)
    expect(axios.get).toHaveBeenCalledWith('/api/admin/configuration/imports', { params: { limit: 5 } })
  })

  it('parses a parameters object and refuses anything else', () => {
    expect(parseParameters('')).toEqual({})
    expect(parseParameters('{"a":"b"}')).toEqual({ a: 'b' })
    expect(parseParameters('[1]')).toBeNull()
    expect(parseParameters('{"a":1}')).toBeNull()
    expect(parseParameters('nope')).toBeNull()
    expect(parseParameters('null')).toBeNull()
  })

  it('reads the JSON body of a failed blob download so the server reason is not lost (revue D4 L-3)', async () => {
    const error = { isAxiosError: true, response: { status: 403, data: new Blob([JSON.stringify({ detail: 'Access denied' })]) } }
    vi.mocked(axios.get).mockRejectedValue(error)
    await expect(downloadExport('x')).rejects.toBe(error)
    expect((error.response.data as unknown as { detail: string }).detail).toBe('Access denied')
    // un corps non JSON laisse l'erreur intacte
    const opaque = { isAxiosError: true, response: { status: 500, data: new Blob(['<html>']) } }
    vi.mocked(axios.get).mockRejectedValue(opaque)
    await expect(downloadExport('x')).rejects.toBe(opaque)
  })
})
