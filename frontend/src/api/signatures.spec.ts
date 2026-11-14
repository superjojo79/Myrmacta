import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import {
  cancelSignatureRequest,
  declineMine,
  fetchMySignatureDocument,
  fetchMySigningUrl,
  listMySignatures,
  listSignatureRequests,
  resendSignatureInvite,
  signMine,
  uploadMySignedDocument,
} from './signatures'

vi.mock('axios')

describe('signatures api (TODO §72)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('lists the requests of a dossier, encoding the id', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: { canManage: false, requests: [] } })
    await expect(listSignatureRequests('d 1')).resolves.toEqual({ canManage: false, requests: [] })
    expect(axios.get).toHaveBeenCalledWith('/api/dossiers/d%201/signature-requests')
  })

  it('cancels and resends through the request-scoped routes', async () => {
    vi.mocked(axios.post).mockResolvedValue({ data: { id: 'r1' } })
    await cancelSignatureRequest('r/1')
    await resendSignatureInvite('r1', 's 2')
    expect(vi.mocked(axios.post).mock.calls.map((c) => c[0])).toEqual([
      '/api/signature-requests/r%2F1/cancel',
      '/api/signature-requests/r1/signers/s%202/resend',
    ])
  })

  it('reads and decides « Mes signatures » from the session identity', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: [] })
    vi.mocked(axios.post).mockResolvedValue({ data: { status: 'SIGNED' } })
    await listMySignatures()
    await fetchMySignatureDocument('s1')
    await fetchMySigningUrl('s 1')
    expect(axios.get).toHaveBeenCalledWith('/api/signatures/mine/s%201/signing-url')
    await signMine('s1')
    await declineMine('s1', '  trop cher ')
    expect(axios.get).toHaveBeenCalledWith('/api/signatures/mine')
    expect(axios.get).toHaveBeenCalledWith('/api/signatures/mine/s1/document', { responseType: 'blob' })
    expect(vi.mocked(axios.post).mock.calls[0]![0]).toBe('/api/signatures/mine/s1/sign')
    expect(vi.mocked(axios.post).mock.calls[1]).toEqual(['/api/signatures/mine/s1/decline', { reason: 'trop cher' }])
  })

  it('uploads the signed document as multipart through the session client (issue #10)', async () => {
    vi.mocked(axios.post).mockResolvedValue({ data: { status: 'SIGNED' } })
    const file = new File(['%PDF signed'], 'Contrat.pdf', { type: 'application/pdf' })

    await expect(uploadMySignedDocument('s 1', file)).resolves.toEqual({ status: 'SIGNED' })

    const [url, body] = vi.mocked(axios.post).mock.calls[0]!
    expect(url).toBe('/api/signatures/mine/s%201/signed-document')
    expect(body).toBeInstanceOf(FormData)
    expect((body as FormData).get('file')).toBe(file)
    // Pas d'en-tête Content-Type posé à la main : le client fixe la frontière du multipart.
    expect(vi.mocked(axios.post).mock.calls[0]).toHaveLength(2)
  })
})
