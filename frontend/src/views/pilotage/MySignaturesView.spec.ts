import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MySignaturesView from './MySignaturesView.vue'
import * as api from '@/api/signatures'
import * as lib from '@/lib/signedUpload'
import * as bridges from '@/lib/cardBridge'
import { CardBridgeError, type CardBridge } from '@/lib/cardBridge'

vi.mock('@/api/signatures')

const RouterLinkStub = { props: ['to'], template: '<a><slot /></a>' }

function item(overrides: Partial<api.MySignatureItem> = {}): api.MySignatureItem {
  return {
    signerId: 's1',
    dossierId: 'd1',
    dossierReference: 'DOS-1',
    documentLabel: 'Contrat',
    required: true,
    myTurn: true,
    deadlineAt: '2026-10-30T10:00:00Z',
    simulated: true,
    hosted: false,
    signingUrl: null,
    signedUpload: false,
    cardBridges: [],
    ...overrides,
  }
}

const mountView = () => mount(MySignaturesView, { global: { stubs: { RouterLink: RouterLinkStub } } })

describe('MySignaturesView (S-VT-4)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('separates « à mon tour » from « en attente » and only offers actions on my turn', async () => {
    vi.mocked(api.listMySignatures).mockResolvedValue([item(), item({ signerId: 's2', myTurn: false, required: false })])
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('[data-testid="signatures-active"]').text()).toContain('Signer')
    expect(wrapper.find('[data-testid="signatures-waiting"]').text()).not.toContain('Signer')
    expect(wrapper.find('[data-testid="signatures-waiting"]').text()).toContain('(facultatif)')
    expect(wrapper.text()).toContain('SIMULATION')
  })

  it('signs then reloads the list', async () => {
    vi.mocked(api.listMySignatures).mockResolvedValueOnce([item()]).mockResolvedValueOnce([])
    vi.mocked(api.signMine).mockResolvedValue({ status: 'SIGNED' })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.findAll('button').find((b) => b.text() === 'Signer')!.trigger('click')
    await flushPromises()

    expect(api.signMine).toHaveBeenCalledWith('s1')
    expect(wrapper.find('[data-testid="signatures-empty"]').exists()).toBe(true)
  })

  it('declines with a reason', async () => {
    vi.mocked(api.listMySignatures).mockResolvedValue([item()])
    vi.mocked(api.declineMine).mockResolvedValue({ status: 'DECLINED' })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.findAll('button').find((b) => b.text() === 'Refuser')!.trigger('click')
    await wrapper.find('input[aria-label="Motif du refus"]').setValue('Non')
    await wrapper.findAll('button').find((b) => b.text() === 'Confirmer le refus')!.trigger('click')
    await flushPromises()

    expect(api.declineMine).toHaveBeenCalledWith('s1', 'Non')
  })

  it('shows a neutral error and reloads when the decision fails (already closed)', async () => {
    vi.mocked(api.listMySignatures).mockResolvedValue([item()])
    vi.mocked(api.signMine).mockRejectedValue({ response: { data: { detail: 'Demande close' } } })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.findAll('button').find((b) => b.text() === 'Signer')!.trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(api.listMySignatures).toHaveBeenCalledTimes(2)
  })

  it('says the feature is off when the API is unavailable', async () => {
    vi.mocked(api.listMySignatures).mockRejectedValue(new Error('404'))
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.find('[data-testid="signatures-unavailable"]').exists()).toBe(true)
  })

  it('opens the provider when the signing link is ready', async () => {
    vi.mocked(api.listMySignatures).mockResolvedValue([item({ hosted: true, simulated: false, signingUrl: null })])
    vi.mocked(api.fetchMySigningUrl).mockResolvedValue('https://yousign.app/signatures/abc')
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('[data-testid="signature-provider"]').trigger('click')
    await flushPromises()

    expect(api.fetchMySigningUrl).toHaveBeenCalledWith('s1')
    expect(api.openSigningPage).toHaveBeenCalledWith('https://yousign.app/signatures/abc')
    expect(api.signMine).not.toHaveBeenCalled()
  })

  it('waits without a local sign button when the link is not ready', async () => {
    vi.mocked(api.listMySignatures).mockResolvedValue([item({ hosted: true, signingUrl: null })])
    vi.mocked(api.fetchMySigningUrl).mockResolvedValue(null)
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('[data-testid="signature-provider"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="signature-waiting"]').exists()).toBe(true)
    expect(wrapper.findAll('button').some((b) => b.text() === 'Signer')).toBe(false)
    expect(wrapper.text()).toContain('Refuser')
    expect(api.openSigningPage).not.toHaveBeenCalled()
  })

})

/** Issue #10 — signature apportée dans « Mes signatures ». */
describe('MySignaturesView, signature apportée (issue #10)', () => {
  const pdf = () => new File(['%PDF signed'], 'Contrat signé.pdf', { type: 'application/pdf' })
  const uploadItem = (overrides: Partial<api.MySignatureItem> = {}) => item({ signedUpload: true, simulated: false, ...overrides })

  async function choose(wrapper: ReturnType<typeof mountView>, file: File | null) {
    const input = wrapper.get('[data-testid="signature-file"]')
    Object.defineProperty(input.element, 'files', { value: file ? [file] : [], configurable: true })
    await input.trigger('change')
  }

  async function send(wrapper: ReturnType<typeof mountView>) {
    await choose(wrapper, pdf())
    await wrapper.get('[data-testid="signature-send"]').trigger('click')
    await flushPromises()
  }

  const rejection = (code: string, status = 422) => ({ response: { status, data: { status, code } } })

  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('offers download and upload instead of the sign button, and still allows declining', async () => {
    vi.mocked(api.listMySignatures).mockResolvedValue([uploadItem()])
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.findAll('button').some((b) => b.text() === 'Signer')).toBe(false)
    expect(wrapper.get('[data-testid="signature-download"]').text()).toBe('Télécharger le document à signer')
    expect(wrapper.text()).toContain('Adobe Acrobat Reader')
    expect(wrapper.get('label[for="signed-file-s1"]').text()).toBe('Document signé (PDF)')
    expect(wrapper.get('#signed-file-s1').attributes('accept')).toContain('.pdf')
    expect(wrapper.get('[data-testid="signature-send"]').attributes('disabled')).toBeDefined()
    expect(wrapper.findAll('button').some((b) => b.text() === 'Refuser')).toBe(true)
  })

  it('offers nothing to upload while waiting for the turn, nor for the other modes', async () => {
    vi.mocked(api.listMySignatures).mockResolvedValue([uploadItem({ myTurn: false }), item({ signerId: 's2' })])
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('[data-testid="signature-upload"]').exists()).toBe(false)
    expect(wrapper.findAll('button').some((b) => b.text() === 'Signer')).toBe(true)
  })

  it('downloads the current document under a name derived from its label', async () => {
    const blob = new Blob(['pdf'])
    vi.mocked(api.listMySignatures).mockResolvedValue([uploadItem()])
    vi.mocked(api.fetchMySignatureDocument).mockResolvedValue(blob)
    const save = vi.spyOn(lib, 'saveBlob').mockImplementation(() => {})
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-testid="signature-download"]').trigger('click')
    await flushPromises()

    expect(api.fetchMySignatureDocument).toHaveBeenCalledWith('s1')
    expect(save).toHaveBeenCalledWith(blob, 'Contrat.pdf')
  })

  it('enables the send button once a PDF is chosen, uploads it, confirms and reloads', async () => {
    vi.mocked(api.listMySignatures).mockResolvedValueOnce([uploadItem()]).mockResolvedValueOnce([])
    vi.mocked(api.uploadMySignedDocument).mockResolvedValue({ status: 'SIGNED' })
    const wrapper = mountView()
    await flushPromises()
    const file = pdf()

    await choose(wrapper, file)
    expect(wrapper.get('[data-testid="signature-send"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('[data-testid="signature-send"]').trigger('click')
    await flushPromises()

    expect(api.uploadMySignedDocument).toHaveBeenCalledWith('s1', file)
    expect(api.signMine).not.toHaveBeenCalled()
    expect(api.listMySignatures).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[data-testid="signature-upload-done"]').attributes('role')).toBe('status')
    expect(wrapper.find('[data-testid="signatures-empty"]').exists()).toBe(true)
  })

  it('explains a refusal by its code, in an alert, and keeps the form', async () => {
    vi.mocked(api.listMySignatures).mockResolvedValue([uploadItem()])
    vi.mocked(api.uploadMySignedDocument).mockRejectedValue(rejection('NO_NEW_SIGNATURE'))
    const wrapper = mountView()
    await flushPromises()
    await send(wrapper)

    const alert = wrapper.get('[data-testid="signature-error"]')
    expect(alert.attributes('role')).toBe('alert')
    expect(alert.text()).toContain('ne contient pas votre signature')
    expect(api.listMySignatures).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-testid="signature-send"]').attributes('disabled')).toBeUndefined()
  })

  it('gives a generic refusal for an unknown or missing code', async () => {
    vi.mocked(api.listMySignatures).mockResolvedValue([uploadItem()])
    vi.mocked(api.uploadMySignedDocument).mockRejectedValueOnce(rejection('NEW_CODE'))
    const wrapper = mountView()
    await flushPromises()
    await send(wrapper)
    expect(wrapper.get('[data-testid="signature-error"]').text()).toContain('Le document signé a été refusé')

    vi.mocked(api.uploadMySignedDocument).mockRejectedValueOnce({ response: { status: 422, data: {} } })
    await wrapper.get('[data-testid="signature-send"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="signature-error"]').text()).toContain('Le document signé a été refusé')
  })

  it('drops the chosen file and reloads the list on STALE_DOCUMENT', async () => {
    vi.mocked(api.listMySignatures).mockResolvedValue([uploadItem()])
    vi.mocked(api.uploadMySignedDocument).mockRejectedValue(rejection('STALE_DOCUMENT'))
    const wrapper = mountView()
    await flushPromises()
    await send(wrapper)

    expect(wrapper.get('[data-testid="signature-error"]').text()).toContain('Quelqu’un d’autre a signé')
    expect(api.listMySignatures).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[data-testid="signature-send"]').attributes('disabled')).toBeDefined()
  })

  it('says the file is too large for a 413', async () => {
    vi.mocked(api.listMySignatures).mockResolvedValue([uploadItem()])
    vi.mocked(api.uploadMySignedDocument).mockRejectedValue({ response: { status: 413, data: {} } })
    const wrapper = mountView()
    await flushPromises()
    await send(wrapper)

    expect(wrapper.get('[data-testid="signature-error"]').text()).toBe('Le fichier dépasse la taille autorisée.')
  })

  it('falls back to the server detail for any other failure', async () => {
    vi.mocked(api.listMySignatures).mockResolvedValue([uploadItem()])
    vi.mocked(api.uploadMySignedDocument).mockRejectedValue({ response: { status: 409, data: { detail: 'Demande close' } } })
    const wrapper = mountView()
    await flushPromises()
    await send(wrapper)

    expect(wrapper.get('[data-testid="signature-error"]').text()).toBe('Demande close')
  })
})

/** Lot 3b — signature dans la page par un pont de carte, depuis « Mes signatures ». */
describe('MySignaturesView, signature dans la page (lot 3b)', () => {
  const PREPARED = { digest: 'RElH', hashFunction: 'SHA-256' as const, signingTime: '2026-11-14T10:00:00Z', documentDigest: 'abc' }
  const CERT = { certificate: 'Q0VSVA==', certificateChain: ['SU5U'] }
  const cardItem = (overrides: Partial<api.MySignatureItem> = {}) =>
    item({ signedUpload: true, simulated: false, cardBridges: ['web-eid', 'beidconnect'], ...overrides })

  function fakeBridge(overrides: Partial<CardBridge> = {}): CardBridge {
    return {
      id: 'web-eid',
      needsPin: false,
      detect: () => Promise.resolve(),
      readCertificate: vi.fn(() => Promise.resolve(CERT)),
      sign: vi.fn(() => Promise.resolve('U0lH')),
      ...overrides,
    }
  }

  const useBridge = (bridge: CardBridge | null, errors: CardBridgeError[] = []) =>
    vi.spyOn(bridges, 'detectCardBridge').mockResolvedValue({ bridge, errors })

  async function clickSign(wrapper: ReturnType<typeof mountView>) {
    await wrapper.get('[data-testid="signature-card-sign"]').trigger('click')
    await flushPromises()
  }

  const rejection = (code: string, status = 422) => ({ response: { status, data: { status, code } } })

  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.prepareMyCardSignature).mockResolvedValue(PREPARED)
    vi.mocked(api.completeMyCardSignature).mockResolvedValue({ status: 'SIGNED' })
  })

  it('offers the card signature as the main action with the upload as another way, and no click signature', async () => {
    vi.mocked(api.listMySignatures).mockResolvedValue([cardItem()])
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('[data-testid="signature-card-sign"]').text()).toBe('Signer avec ma carte eID')
    expect(wrapper.get('[data-testid="signature-card"]').text()).toContain('n’est jamais envoyé à ce site')
    expect(wrapper.get('[data-testid="signature-card"]').text()).toContain('Autre moyen : signer hors de cette page')
    expect(wrapper.find('[data-testid="signature-download"]').exists()).toBe(true)
    expect(wrapper.findAll('button').some((b) => b.text() === 'Signer')).toBe(false)
    expect(wrapper.findAll('button').some((b) => b.text() === 'Refuser')).toBe(true)
  })

  it('shows nothing of the card signature when no bridge is offered, or while waiting for the turn', async () => {
    vi.mocked(api.listMySignatures).mockResolvedValue([
      cardItem({ cardBridges: [] }),
      cardItem({ signerId: 's2', myTurn: false }),
      item({ signerId: 's3' }),
    ])
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('[data-testid="signature-card"]').exists()).toBe(false)
    expect(wrapper.findAll('[data-testid="signature-upload"]')).toHaveLength(1)
    expect(wrapper.findAll('button').filter((b) => b.text() === 'Signer')).toHaveLength(1)
  })

  it('signs in the page, then reloads the list and confirms', async () => {
    const bridge = fakeBridge()
    const detect = useBridge(bridge)
    vi.mocked(api.listMySignatures).mockResolvedValueOnce([cardItem()]).mockResolvedValueOnce([])
    const wrapper = mountView()
    await flushPromises()

    await clickSign(wrapper)

    expect(detect).toHaveBeenCalledWith(['web-eid', 'beidconnect'], expect.any(String), undefined)
    expect(api.prepareMyCardSignature).toHaveBeenCalledWith('s1', CERT)
    expect(bridge.sign).toHaveBeenCalledWith(CERT, 'RElH', 'SHA-256', null)
    expect(api.completeMyCardSignature).toHaveBeenCalledWith('s1', {
      ...CERT,
      signingTime: PREPARED.signingTime,
      documentDigest: 'abc',
      signature: 'U0lH',
    })
    expect(api.signMine).not.toHaveBeenCalled()
    expect(api.listMySignatures).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[data-testid="signature-upload-done"]').text()).toContain('bien été enregistrée')
    expect(wrapper.find('[data-testid="signatures-empty"]').exists()).toBe(true)
  })

  it('shows a visible status per step on the signer row only', async () => {
    let finishRead!: () => void
    useBridge(fakeBridge({ readCertificate: () => new Promise((r) => (finishRead = () => r(CERT))) }))
    vi.mocked(api.listMySignatures).mockResolvedValue([cardItem(), cardItem({ signerId: 's2', documentLabel: 'Avenant' })])
    const wrapper = mountView()
    await flushPromises()

    await clickSign(wrapper)

    const statuses = wrapper.findAll('[data-testid="signature-card-status"]')
    expect(statuses).toHaveLength(1)
    expect(statuses[0]!.text()).toContain('Lecture de la carte')
    expect(statuses[0]!.attributes('aria-live')).toBe('polite')
    expect(wrapper.findAll('[data-testid="signature-card-sign"]').every((b) => b.attributes('disabled') !== undefined)).toBe(true)
    finishRead()
    await flushPromises()
  })

  it('asks for the PIN in a labelled password field, passes it to the bridge only and wipes it', async () => {
    const bridge = fakeBridge({ id: 'beidconnect', needsPin: true })
    useBridge(bridge)
    vi.mocked(api.listMySignatures).mockResolvedValue([cardItem()])
    const wrapper = mountView()
    await flushPromises()
    await clickSign(wrapper)

    const input = wrapper.get('[data-testid="signature-pin"]')
    expect(input.attributes('type')).toBe('password')
    expect(input.attributes('autocomplete')).toBe('off')
    expect(input.attributes('maxlength')).toBe('12')
    expect(wrapper.get('label[for="signature-pin-s1"]').text()).toContain('Code PIN')
    expect(wrapper.get('[data-testid="signature-pin-form"]').text()).toContain('n’est jamais envoyé à ce site')
    expect(wrapper.get('[data-testid="signature-pin-submit"]').attributes('disabled')).toBeDefined()

    await input.setValue('1234')
    expect(wrapper.get('[data-testid="signature-pin-submit"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('[data-testid="signature-pin-form"]').trigger('submit')
    await flushPromises()

    expect(bridge.sign).toHaveBeenCalledWith(CERT, 'RElH', 'SHA-256', '1234')
    expect(JSON.stringify(vi.mocked(api.prepareMyCardSignature).mock.calls)).not.toContain('1234')
    expect(JSON.stringify(vi.mocked(api.completeMyCardSignature).mock.calls)).not.toContain('1234')
    expect(wrapper.find('[data-testid="signature-pin"]').exists()).toBe(false)
    expect(wrapper.html()).not.toContain('1234')
  })

  it('wipes the PIN after a wrong one and says how many attempts remain', async () => {
    useBridge(fakeBridge({ needsPin: true, sign: vi.fn(() => Promise.reject(new CardBridgeError('pin-incorrect', { attemptsLeft: 1 }))) }))
    vi.mocked(api.listMySignatures).mockResolvedValue([cardItem()])
    const wrapper = mountView()
    await flushPromises()
    await clickSign(wrapper)
    await wrapper.get('[data-testid="signature-pin"]').setValue('0000')
    await wrapper.get('[data-testid="signature-pin-form"]').trigger('submit')
    await flushPromises()

    const alert = wrapper.get('[data-testid="signature-error"]')
    expect(alert.attributes('role')).toBe('alert')
    expect(alert.text()).toContain('Il vous reste 1 essai(s)')
    expect(wrapper.html()).not.toContain('0000')
    expect(api.completeMyCardSignature).not.toHaveBeenCalled()
  })

  it('lets the signer cancel the PIN prompt', async () => {
    const bridge = fakeBridge({ needsPin: true })
    useBridge(bridge)
    vi.mocked(api.listMySignatures).mockResolvedValue([cardItem()])
    const wrapper = mountView()
    await flushPromises()
    await clickSign(wrapper)

    await wrapper.get('[data-testid="signature-pin-cancel"]').trigger('click')
    await flushPromises()

    expect(bridge.sign).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="signature-error"]').text()).toContain('annulée')
  })

  it('says no eID software was found, names both bridges and keeps the upload available', async () => {
    useBridge(null, [new CardBridgeError('unavailable')])
    vi.mocked(api.listMySignatures).mockResolvedValue([cardItem()])
    const wrapper = mountView()
    await flushPromises()
    await clickSign(wrapper)

    const text = wrapper.get('[data-testid="signature-error"]').text()
    expect(text).toContain('Aucun logiciel eID')
    expect(text).toContain('Web eID')
    expect(text).toContain('BeIDConnect')
    expect(api.prepareMyCardSignature).not.toHaveBeenCalled()
    expect(wrapper.findAll('[data-testid="signature-error"]')).toHaveLength(1)
    expect(wrapper.find('[data-testid="signature-send"]').exists()).toBe(true)
  })

  it.each([
    ['no-reader', 'Aucun lecteur'],
    ['no-card', 'Aucune carte'],
    ['timeout', 'délai est dépassé'],
    ['card-blocked', 'bloquée'],
    ['update-required', 'Mettez-le à jour'],
    ['insecure-context', 'connexion sécurisée'],
    ['failed', 'a échoué'],
  ] as const)('translates the bridge error « %s »', async (kind, fragment) => {
    useBridge(fakeBridge({ readCertificate: () => Promise.reject(new CardBridgeError(kind)) }))
    vi.mocked(api.listMySignatures).mockResolvedValue([cardItem()])
    const wrapper = mountView()
    await flushPromises()
    await clickSign(wrapper)
    expect(wrapper.get('[data-testid="signature-error"]').text()).toContain(fragment)
  })

  it('explains an expired preparation, and falls back on the upload messages for the shared codes', async () => {
    useBridge(fakeBridge())
    vi.mocked(api.listMySignatures).mockResolvedValue([cardItem()])
    const wrapper = mountView()
    await flushPromises()

    vi.mocked(api.completeMyCardSignature).mockRejectedValueOnce(rejection('SIGNING_SESSION_EXPIRED'))
    await clickSign(wrapper)
    expect(wrapper.get('[data-testid="signature-error"]').text()).toContain('préparation de la signature a expiré')

    vi.mocked(api.completeMyCardSignature).mockRejectedValueOnce(rejection('CERTIFICATE_NOT_QUALIFIED'))
    await clickSign(wrapper)
    expect(wrapper.get('[data-testid="signature-error"]').text()).toContain('pas valable')

    vi.mocked(api.completeMyCardSignature).mockRejectedValueOnce(rejection('NEW_CODE'))
    await clickSign(wrapper)
    expect(wrapper.get('[data-testid="signature-error"]').text()).toContain('Le document signé a été refusé')
  })

  it('reloads the list on STALE_DOCUMENT, and shows 413 and server details as before', async () => {
    useBridge(fakeBridge())
    vi.mocked(api.listMySignatures).mockResolvedValue([cardItem()])
    const wrapper = mountView()
    await flushPromises()

    vi.mocked(api.prepareMyCardSignature).mockRejectedValueOnce(rejection('STALE_DOCUMENT'))
    await clickSign(wrapper)
    expect(wrapper.get('[data-testid="signature-error"]').text()).toContain('Quelqu’un d’autre a signé')
    expect(api.listMySignatures).toHaveBeenCalledTimes(2)

    vi.mocked(api.prepareMyCardSignature).mockRejectedValueOnce({ response: { status: 413, data: {} } })
    await clickSign(wrapper)
    expect(wrapper.get('[data-testid="signature-error"]').text()).toBe('Le fichier dépasse la taille autorisée.')

    vi.mocked(api.prepareMyCardSignature).mockRejectedValueOnce({ response: { status: 409, data: { detail: 'Demande close' } } })
    await clickSign(wrapper)
    expect(wrapper.get('[data-testid="signature-error"]').text()).toBe('Demande close')
  })
})
