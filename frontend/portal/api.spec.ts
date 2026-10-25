import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiBase, fetchTask, PortalApiError, startIdentity, submitAnswers, uploadAttachment } from './api'

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

  /** TODO §105 — le type de pièce choisi par le tiers part avec le fichier. */
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
})
