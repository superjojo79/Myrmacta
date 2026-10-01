import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import { applyImport, exportUrl, listImports, parseParameters, planImport } from './configuration'

vi.mock('axios')

const file = new File(['zip'], 'bundle.zip', { type: 'application/zip' })

describe('configuration api (TODO §83, D4)', () => {
  beforeEach(() => vi.resetAllMocks())

  it('builds the export url with an encoded label', () => {
    expect(exportUrl('')).toBe('/api/admin/configuration/export')
    expect(exportUrl(' recette & co ')).toBe('/api/admin/configuration/export?sourceLabel=recette%20%26%20co')
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
})
