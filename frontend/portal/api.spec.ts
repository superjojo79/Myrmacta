import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  apiBase,
  completeCardSignature,
  prepareCardSignature,
  fetchSignatureDocument,
  fetchTask,
  PortalApiError,
  startIdentity,
  submitAnswers,
  uploadAttachment,
  uploadSignedDocument,
} from './api'

const TOKEN = 't'.repeat(43)

function mockFetch(status: number, body: unknown) {
  const fetchMock = vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('portal api client', () => {
  it('derives the API base from the tenant path prefix', () => {
    expect(apiBase('/tenant-a/portal/')).toBe('/tenant-a/api/portal/task')
    expect(apiBase('/portal/')).toBe('/api/portal/task')
    expect(apiBase('/portal-rh/portal/')).toBe('/portal-rh/api/portal/task')
  })

  it('sends the token in a header, never in the URL, and never with cookies (V-PORTAL-1)', async () => {
    const fetchMock = mockFetch(200, { attachments: [] })

    await fetchTask(TOKEN)

    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).not.toContain(TOKEN)
    expect((init.headers as Headers).get('X-Portal-Token')).toBe(TOKEN)
    expect(init.credentials).toBe('omit')
  })

  it('turns an error status into PortalApiError with the server detail', async () => {
    mockFetch(400, { detail: 'Saisie invalide' })

    await expect(submitAnswers(TOKEN, {})).rejects.toEqual(new PortalApiError(400, 'Saisie invalide'))
  })

  it('posts answers as JSON and attachments as multipart', async () => {
    const fetchMock = mockFetch(204, null)
    await submitAnswers(TOKEN, { disponible: true })
    expect(JSON.parse(fetchMock.mock.calls[0]![1].body)).toEqual({ answers: { disponible: true } })

    const uploadMock = mockFetch(201, { id: '1', label: 'Devis', filename: 'd.pdf', addedAt: '' })
    await uploadAttachment(TOKEN, new File(['%PDF'], 'd.pdf', { type: 'application/pdf' }), ' Devis ')
    const form = uploadMock.mock.calls[0]![1].body as FormData
    expect(form.get('label')).toBe('Devis')
    expect(form.has('pieceKey')).toBe(false)
    expect(uploadMock.mock.calls[0]![0]).toContain('/attachments')
  })

  /** TODO §106 — le type de pièce choisi par le tiers part avec le fichier. */
  it('sends the chosen piece type with the attachment', async () => {
    const uploadMock = mockFetch(201, { id: '1', label: 'Preuve', filename: 'p.pdf', addedAt: '' })
    await uploadAttachment(TOKEN, new File(['%PDF'], 'p.pdf', { type: 'application/pdf' }), '', 'preuveDomicile')
    const form = uploadMock.mock.calls[0]![1].body as FormData
    expect(form.get('pieceKey')).toBe('preuveDomicile')
    expect(form.has('label')).toBe(false)
  })

  /** TODO §104 (D-BEAUTH-PROOF) — après l'authentification forte, la preuve remplace le jeton. */
  it('sends the identity proof instead of the token once the third party is authenticated', async () => {
    const fetchMock = mockFetch(200, { attachments: [] })

    await fetchTask({ proof: TOKEN })

    const init = fetchMock.mock.calls[0]![1]
    expect((init.headers as Headers).get('X-Portal-Identity')).toBe(TOKEN)
    expect((init.headers as Headers).get('X-Portal-Token')).toBeNull()
    expect(init.credentials).toBe('omit')
  })

  it('starts the strong authentication with the token and returns the provider URL', async () => {
    const fetchMock = mockFetch(200, { authorizationUrl: 'https://idp.example/auth?state=s' })
    window.history.replaceState(null, '', '/tenant-a/portal/')

    const url = await startIdentity(TOKEN, 'fas')

    expect(url).toBe('https://idp.example/auth?state=s')
    const [target, init] = fetchMock.mock.calls[0]!
    expect(String(target)).toBe('/tenant-a/api/portal/identity/start')
    expect(String(target)).not.toContain(TOKEN)
    expect((init.headers as Headers).get('X-Portal-Token')).toBe(TOKEN)
    expect(JSON.parse(init.body)).toEqual({ provider: 'fas' })
    expect(init.credentials).toBe('omit')
  })

  /** Issue #10 — le PDF signé part en multipart (partie `file`), jeton en en-tête, sans cookie. */
  it('uploads the signed document as multipart with the token in the header', async () => {
    const fetchMock = mockFetch(200, { status: 'SIGNED' })
    window.history.replaceState(null, '', '/tenant-a/portal/')
    const file = new File(['%PDF signed'], 'Contrat.pdf', { type: 'application/pdf' })

    await expect(uploadSignedDocument(TOKEN, file)).resolves.toEqual({ status: 'SIGNED' })

    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toBe('/tenant-a/api/portal/signature/signed-document')
    expect(String(url)).not.toContain(TOKEN)
    expect(init.method).toBe('POST')
    expect((init.body as FormData).get('file')).toBe(file)
    expect((init.headers as Headers).get('X-Portal-Token')).toBe(TOKEN)
    expect((init.headers as Headers).has('Content-Type')).toBe(false)
    expect(init.credentials).toBe('omit')
  })

  it('reads the rejection code of a problem+json body, with or without a detail', async () => {
    mockFetch(422, { type: 'about:blank', status: 422, code: 'NO_NEW_SIGNATURE', detail: 'x' })
    await expect(uploadSignedDocument(TOKEN, new File(['%PDF'], 'a.pdf'))).rejects.toEqual(
      new PortalApiError(422, 'x', 'NO_NEW_SIGNATURE'),
    )

    mockFetch(422, { code: 'STALE_DOCUMENT' })
    await expect(uploadSignedDocument(TOKEN, new File(['%PDF'], 'a.pdf'))).rejects.toMatchObject({
      status: 422,
      detail: '',
      code: 'STALE_DOCUMENT',
    })

    mockFetch(413, { detail: 'trop gros' })
    await expect(uploadSignedDocument(TOKEN, new File(['%PDF'], 'a.pdf'))).rejects.toMatchObject({ status: 413, code: undefined })
  })

  it('reads the code on the other portal calls too and ignores a non-text code', async () => {
    mockFetch(409, { code: 'SIGNED_UPLOAD_REQUIRED' })
    await expect(fetchSignatureDocument(TOKEN)).rejects.toMatchObject({ status: 409, code: 'SIGNED_UPLOAD_REQUIRED' })
    mockFetch(500, { code: 42 })
    await expect(fetchTask(TOKEN)).rejects.toMatchObject({ status: 500, code: undefined })
  })

  /** Lot 3b — signature dans la page : JSON, jeton en en-tête, sans cookie ; le PIN n'a aucune place dans ces échanges. */
  it('prepares and completes a card signature with JSON bodies, the token in the header and no cookie', async () => {
    window.history.replaceState(null, '', '/tenant-a/portal/')
    const prepared = { digest: 'RElH', hashFunction: 'SHA-256', signingTime: '2026-11-14T10:00:00Z', documentDigest: 'abc' }
    const prepare = mockFetch(200, prepared)
    await expect(prepareCardSignature(TOKEN, { certificate: 'Q0VSVA==', certificateChain: ['SU5U'] })).resolves.toEqual(prepared)
    const [prepareUrl, prepareInit] = prepare.mock.calls[0]!
    expect(String(prepareUrl)).toBe('/tenant-a/api/portal/signature/card-signature/prepare')
    expect(String(prepareUrl)).not.toContain(TOKEN)
    expect(prepareInit.method).toBe('POST')
    expect(JSON.parse(prepareInit.body)).toEqual({ certificate: 'Q0VSVA==', certificateChain: ['SU5U'] })
    expect((prepareInit.headers as Headers).get('Content-Type')).toBe('application/json')
    expect((prepareInit.headers as Headers).get('X-Portal-Token')).toBe(TOKEN)
    expect(prepareInit.credentials).toBe('omit')

    const complete = mockFetch(200, { status: 'SIGNED' })
    const body = { certificate: 'Q0VSVA==', certificateChain: [], signingTime: prepared.signingTime, documentDigest: 'abc', signature: 'U0lH' }
    await expect(completeCardSignature(TOKEN, body)).resolves.toEqual({ status: 'SIGNED' })
    const [completeUrl, completeInit] = complete.mock.calls[0]!
    expect(String(completeUrl)).toBe('/tenant-a/api/portal/signature/card-signature/complete')
    expect(JSON.parse(completeInit.body)).toEqual(body)
    expect((completeInit.headers as Headers).get('X-Portal-Token')).toBe(TOKEN)
    expect(completeInit.credentials).toBe('omit')
  })

  it('exposes the rejection code of a card signature refusal', async () => {
    mockFetch(422, { code: 'SIGNING_SESSION_EXPIRED' })
    await expect(
      completeCardSignature(TOKEN, { certificate: 'c', certificateChain: [], signingTime: 't', documentDigest: 'd', signature: 's' }),
    ).rejects.toMatchObject({ status: 422, code: 'SIGNING_SESSION_EXPIRED' })
  })
})
