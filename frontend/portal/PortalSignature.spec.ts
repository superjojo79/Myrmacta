import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import PortalApp from './PortalApp.vue'
import PortalSignature from './PortalSignature.vue'
import * as api from './api'
import * as bridges from '@/lib/cardBridge'
import { setLocale } from '@/i18n'
import { CardBridgeError, type CardBridge, type CardChoice } from '@/lib/cardBridge'

const TOKEN = 'k'.repeat(43)

const context: api.SignatureContext = {
  dossierReference: 'DOS-2026-0042',
  documentLabel: 'Contrat',
  signerName: 'Alice',
  required: true,
  deadlineAt: '2026-10-30T10:00:00Z',
  simulated: true,
  hosted: false,
  signingUrl: null,
  signedUpload: false,
  cardBridges: [],
}

describe('PortalSignature (S-VT-1)', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    URL.createObjectURL = vi.fn(() => 'blob:doc')
    URL.revokeObjectURL = vi.fn()
    vi.spyOn(api, 'fetchSignatureDocument').mockResolvedValue(new Blob(['pdf']))
  })

  it('shows the context, the document link and the simulation banner', async () => {
    vi.spyOn(api, 'fetchSignatureContext').mockResolvedValue(context)
    const wrapper = mount(PortalSignature, { props: { token: TOKEN } })
    await flushPromises()

    expect(wrapper.text()).toContain('Contrat')
    expect(wrapper.text()).toContain('DOS-2026-0042')
    expect(wrapper.find('[data-testid="signature-simulation"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="signature-document"]').attributes('href')).toBe('blob:doc')
  })

  it('shows the same neutral screen for any 404', async () => {
    vi.spyOn(api, 'fetchSignatureContext').mockRejectedValue(new api.PortalApiError(404, 'expired secret detail'))
    const wrapper = mount(PortalSignature, { props: { token: TOKEN } })
    await flushPromises()

    expect(wrapper.find('[data-testid="portal-denied"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('expired secret detail')
  })

  it('signs and shows the confirmation', async () => {
    vi.spyOn(api, 'fetchSignatureContext').mockResolvedValue(context)
    const sign = vi.spyOn(api, 'signDocument').mockResolvedValue({ status: 'SIGNED' })
    const wrapper = mount(PortalSignature, { props: { token: TOKEN } })
    await flushPromises()

    await wrapper.find('button.portal__primary').trigger('click')
    await flushPromises()

    expect(sign).toHaveBeenCalledWith(TOKEN)
    expect(wrapper.find('[data-testid="signature-signed"]').exists()).toBe(true)
  })

  it('declines with a reason and shows the confirmation', async () => {
    vi.spyOn(api, 'fetchSignatureContext').mockResolvedValue({ ...context, simulated: false })
    const decline = vi.spyOn(api, 'declineSignature').mockResolvedValue({ status: 'DECLINED' })
    const wrapper = mount(PortalSignature, { props: { token: TOKEN } })
    await flushPromises()
    expect(wrapper.find('[data-testid="signature-simulation"]').exists()).toBe(false)

    await wrapper.find('button.portal__secondary').trigger('click')
    await wrapper.find('#signature-reason').setValue('Pas d’accord')
    await wrapper.find('button.portal__primary').trigger('click')
    await flushPromises()

    expect(decline).toHaveBeenCalledWith(TOKEN, 'Pas d’accord')
    expect(wrapper.find('[data-testid="signature-declined"]').exists()).toBe(true)
  })

  it('is used by the portal app for a signature link and never calls the task API', async () => {
    const task = vi.spyOn(api, 'fetchTask')
    vi.spyOn(api, 'fetchSignatureContext').mockResolvedValue(context)
    const wrapper = mount(PortalApp, { props: { token: TOKEN, kind: 'signature' } })
    await flushPromises()

    expect(wrapper.text()).toContain('Contrat')
    expect(task).not.toHaveBeenCalled()
  })

  it('sends a hosted signer to the provider without signing in the portal', async () => {
    vi.spyOn(api, 'fetchSignatureContext').mockResolvedValue({
      ...context,
      simulated: false,
      hosted: true,
      signingUrl: 'https://yousign.app/signatures/abc',
    })
    const sign = vi.spyOn(api, 'signDocument')
    const go = vi.spyOn(api, 'navigateTo').mockImplementation(() => {})
    const wrapper = mount(PortalSignature, { props: { token: TOKEN } })
    await flushPromises()

    expect(go).toHaveBeenCalledWith('https://yousign.app/signatures/abc')
    expect(sign).not.toHaveBeenCalled()
    expect(wrapper.findAll('button').some((b) => b.text() === 'Signer')).toBe(false)
    expect(wrapper.find('button.portal__secondary').text()).toContain('Refuser')
  })

  it('waits when the provider has not published the link yet', async () => {
    vi.spyOn(api, 'fetchSignatureContext').mockResolvedValue({ ...context, hosted: true, signingUrl: null })
    const go = vi.spyOn(api, 'navigateTo')
    const wrapper = mount(PortalSignature, { props: { token: TOKEN } })
    await flushPromises()

    expect(wrapper.find('[data-testid="signature-waiting"]').exists()).toBe(true)
    expect(wrapper.findAll('button').some((b) => b.text() === 'Signer')).toBe(false)
    expect(wrapper.text()).toContain('Refuser')
    expect(go).not.toHaveBeenCalled()
  })

})

/** Issue #10 — signature apportée : télécharger, signer hors de la page avec la carte eID, téléverser. */
describe('PortalSignature, signature apportée (issue #10)', () => {
  const upload: api.SignatureContext = { ...context, simulated: false, signedUpload: true }
  const pdf = () => new File(['%PDF signed'], 'Contrat signé.pdf', { type: 'application/pdf' })

  async function mountReady(ctx: api.SignatureContext = upload) {
    vi.spyOn(api, 'fetchSignatureContext').mockResolvedValue(ctx)
    const wrapper = mount(PortalSignature, { props: { token: TOKEN }, attachTo: document.body })
    await flushPromises()
    return wrapper
  }

  /** jsdom n'expose pas `files` en écriture : on le définit sur l'élément, comme le fait le navigateur. */
  async function choose(wrapper: Awaited<ReturnType<typeof mountReady>>, file: File | null) {
    const input = wrapper.get('[data-testid="signature-file"]')
    Object.defineProperty(input.element, 'files', { value: file ? [file] : [], configurable: true })
    await input.trigger('change')
  }

  beforeEach(() => {
    vi.restoreAllMocks()
    URL.createObjectURL = vi.fn(() => 'blob:doc')
    URL.revokeObjectURL = vi.fn()
    vi.spyOn(api, 'fetchSignatureDocument').mockResolvedValue(new Blob(['pdf']))
  })

  it('shows the three steps, a download link named after the document and no sign button', async () => {
    const wrapper = await mountReady()

    const link = wrapper.get('[data-testid="signature-download"]')
    expect(link.text()).toBe('Télécharger le document à signer')
    expect(link.attributes('href')).toBe('blob:doc')
    expect(link.attributes('download')).toBe('Contrat.pdf')
    expect(wrapper.findAll('ol li')).toHaveLength(3)
    expect(wrapper.text()).toContain('Adobe Acrobat Reader')
    expect(wrapper.text()).toContain('service fédéral de signature')
    expect(wrapper.get('label[for="signature-file"]').text()).toBe('Document signé (PDF)')
    expect(wrapper.get('#signature-file').attributes('accept')).toContain('.pdf')
    expect(wrapper.findAll('button').some((b) => b.text() === 'Signer')).toBe(false)
    expect(wrapper.find('[data-testid="signature-document"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Refuser')
    wrapper.unmount()
  })

  it('keeps the send button disabled until a file is chosen', async () => {
    const wrapper = await mountReady()
    const send = wrapper.get('[data-testid="signature-send"]')
    expect(send.text()).toBe('Envoyer le document signé')
    expect(send.attributes('disabled')).toBeDefined()

    await choose(wrapper, pdf())
    expect(send.attributes('disabled')).toBeUndefined()

    await choose(wrapper, null)
    expect(send.attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('sends the chosen file and shows the same confirmation as a click signature', async () => {
    const send = vi.spyOn(api, 'uploadSignedDocument').mockResolvedValue({ status: 'SIGNED' })
    const sign = vi.spyOn(api, 'signDocument')
    const wrapper = await mountReady()
    const file = pdf()
    await choose(wrapper, file)

    await wrapper.get('[data-testid="signature-send"]').trigger('click')
    await flushPromises()

    expect(send).toHaveBeenCalledWith(TOKEN, file)
    expect(sign).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="signature-signed"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('shows a localised message for a refusal code and lets the signer try again', async () => {
    vi.spyOn(api, 'uploadSignedDocument').mockRejectedValue(new api.PortalApiError(422, 'raw server text', 'CERTIFICATE_REVOKED'))
    const wrapper = await mountReady()
    await choose(wrapper, pdf())

    await wrapper.get('[data-testid="signature-send"]').trigger('click')
    await flushPromises()

    const alert = wrapper.get('[data-testid="signature-error"]')
    expect(alert.attributes('role')).toBe('alert')
    expect(alert.text()).toContain('révoqué')
    expect(alert.text()).not.toContain('raw server text')
    expect(wrapper.find('[data-testid="signature-signed"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="signature-send"]').attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })

  it.each([
    ['NOT_A_PDF', 'n’est pas un PDF'],
    ['DOCUMENT_MISMATCH', 'n’est pas le document à signer'],
    ['NO_NEW_SIGNATURE', 'ne contient pas votre signature'],
    ['SEVERAL_SIGNATURES', 'plusieurs nouvelles signatures'],
    ['CONTENT_MODIFIED', 'modifié'],
    ['SIGNATURE_INVALID', 'n’a pas pu être validée'],
    ['CERTIFICATE_UNTRUSTED', 'pas reconnu'],
    ['CERTIFICATE_NOT_QUALIFIED', 'pas valable'],
    ['REVOCATION_UNAVAILABLE', 'Réessayez dans quelques minutes'],
    ['DUPLICATE_SIGNER', 'déjà servi'],
  ])('has a dedicated message for %s', async (code, fragment) => {
    vi.spyOn(api, 'uploadSignedDocument').mockRejectedValue(new api.PortalApiError(422, '', code))
    const wrapper = await mountReady()
    await choose(wrapper, pdf())
    await wrapper.get('[data-testid="signature-send"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="signature-error"]').text()).toContain(fragment)
    wrapper.unmount()
  })

  it('shows a generic refusal for an unknown or missing code', async () => {
    const send = vi.spyOn(api, 'uploadSignedDocument')
    const wrapper = await mountReady()
    await choose(wrapper, pdf())

    send.mockRejectedValueOnce(new api.PortalApiError(422, '', 'NEW_CODE_FROM_THE_FUTURE'))
    await wrapper.get('[data-testid="signature-send"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="signature-error"]').text()).toContain('Le document signé a été refusé')

    send.mockRejectedValueOnce(new api.PortalApiError(422, ''))
    await wrapper.get('[data-testid="signature-send"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="signature-error"]').text()).toContain('Le document signé a été refusé')
    wrapper.unmount()
  })

  it('reloads the document and clears the file when it was signed by someone else meanwhile (STALE_DOCUMENT)', async () => {
    vi.spyOn(api, 'uploadSignedDocument').mockRejectedValue(new api.PortalApiError(422, '', 'STALE_DOCUMENT'))
    const urls = ['blob:old', 'blob:new']
    URL.createObjectURL = vi.fn(() => urls.shift()!)
    const wrapper = await mountReady()
    expect(wrapper.get('[data-testid="signature-download"]').attributes('href')).toBe('blob:old')
    await choose(wrapper, pdf())

    await wrapper.get('[data-testid="signature-send"]').trigger('click')
    await flushPromises()

    expect(api.fetchSignatureDocument).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[data-testid="signature-download"]').attributes('href')).toBe('blob:new')
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:old')
    expect(wrapper.get('[data-testid="signature-error"]').text()).toContain('Quelqu’un d’autre a signé')
    expect(wrapper.get('[data-testid="signature-error"]').text()).toContain('téléchargez-le de nouveau')
    expect(wrapper.get('[data-testid="signature-send"]').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('shows the too-large message for a 413', async () => {
    vi.spyOn(api, 'uploadSignedDocument').mockRejectedValue(new api.PortalApiError(413, ''))
    const wrapper = await mountReady()
    await choose(wrapper, pdf())
    await wrapper.get('[data-testid="signature-send"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="signature-error"]').text()).toBe('Le fichier dépasse la taille autorisée.')
    wrapper.unmount()
  })

  it('shows the neutral screen for a 404 and the rate-limit message for a 429', async () => {
    const send = vi.spyOn(api, 'uploadSignedDocument').mockRejectedValueOnce(new api.PortalApiError(429, ''))
    const wrapper = await mountReady()
    await choose(wrapper, pdf())
    await wrapper.get('[data-testid="signature-send"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="signature-error"]').text()).toContain('Trop de tentatives')

    send.mockRejectedValueOnce(new api.PortalApiError(404, 'secret detail'))
    await wrapper.get('[data-testid="signature-send"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="portal-denied"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('secret detail')
    wrapper.unmount()
  })

  it('still lets the signer decline', async () => {
    const decline = vi.spyOn(api, 'declineSignature').mockResolvedValue({ status: 'DECLINED' })
    const wrapper = await mountReady()

    await wrapper.get('button.portal__secondary').trigger('click')
    expect(wrapper.find('[data-testid="signature-upload"]').exists()).toBe(false)
    await wrapper.get('#signature-reason').setValue('Non')
    await wrapper.get('button.portal__primary').trigger('click')
    await flushPromises()

    expect(decline).toHaveBeenCalledWith(TOKEN, 'Non')
    expect(wrapper.find('[data-testid="signature-declined"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('does not change the click and hosted modes', async () => {
    const wrapper = await mountReady({ ...context, signedUpload: false })
    expect(wrapper.find('[data-testid="signature-upload"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="signature-download"]').exists()).toBe(false)
    expect(wrapper.findAll('button').some((b) => b.text() === 'Signer')).toBe(true)
    expect(wrapper.find('[data-testid="signature-document"]').exists()).toBe(true)
    wrapper.unmount()
  })
})

/** Lot 3b — signature dans la page par un pont de carte (Web eID, BeIDConnect). */
describe('PortalSignature, signature dans la page (lot 3b)', () => {
  const card: api.SignatureContext = { ...context, simulated: false, signedUpload: true, cardBridges: ['web-eid', 'beidconnect'] }
  const PREPARED = { digest: 'RElH', hashFunction: 'SHA-256' as const, signingTime: '2026-11-14T10:00:00Z', documentDigest: 'abc' }
  const CERT = { certificate: 'Q0VSVA==', certificateChain: ['SU5U'] }

  function fakeBridge(o: {
    id?: CardBridge['id']
    needsPin?: boolean
    choices?: CardChoice[]
    readCertificate?: () => Promise<typeof CERT>
    sign?: CardBridge['sign']
  } = {}): CardBridge {
    const needsPin = o.needsPin ?? false
    const base: CardChoice = { id: '0:0', certificate: CERT, readerName: null, holder: null, notAfter: null, needsPin }
    return {
      id: o.id ?? 'web-eid',
      detect: () => Promise.resolve(),
      listChoices: vi.fn(() => (o.readCertificate ? o.readCertificate().then((certificate) => [{ ...base, certificate }]) : Promise.resolve(o.choices ?? [base]))),
      certificateOf: vi.fn((choice: CardChoice) => Promise.resolve(choice.certificate)),
      sign: o.sign ?? vi.fn(() => Promise.resolve('U0lH')),
    }
  }

  function useBridge(bridge: CardBridge | null, errors: CardBridgeError[] = []) {
    return vi.spyOn(bridges, 'detectCardBridge').mockResolvedValue({ bridge, errors })
  }

  async function mountReady(ctx: api.SignatureContext = card) {
    vi.spyOn(api, 'fetchSignatureContext').mockResolvedValue(ctx)
    const wrapper = mount(PortalSignature, { props: { token: TOKEN } })
    await flushPromises()
    return wrapper
  }

  const click = async (wrapper: Awaited<ReturnType<typeof mountReady>>) => {
    await wrapper.get('[data-testid="signature-card-sign"]').trigger('click')
    await flushPromises()
  }

  beforeEach(() => {
    vi.restoreAllMocks()
    URL.createObjectURL = vi.fn(() => 'blob:doc')
    URL.revokeObjectURL = vi.fn()
    vi.spyOn(api, 'fetchSignatureDocument').mockResolvedValue(new Blob(['pdf']))
    vi.spyOn(api, 'prepareCardSignature').mockResolvedValue(PREPARED)
    vi.spyOn(api, 'completeCardSignature').mockResolvedValue({ status: 'SIGNED' })
  })

  it('offers the card signature as the main action, with the upload as another way and no click signature', async () => {
    const wrapper = await mountReady()

    expect(wrapper.get('[data-testid="signature-card-sign"]').text()).toBe('Signer avec ma carte eID')
    expect(wrapper.get('[data-testid="signature-card"]').text()).toContain('n’est jamais envoyé à ce site')
    expect(wrapper.get('[data-testid="signature-upload"] h2').text()).toBe('Autre moyen : signer hors de cette page')
    expect(wrapper.find('[data-testid="signature-download"]').exists()).toBe(true)
    expect(wrapper.findAll('button').some((b) => b.text() === 'Signer')).toBe(false)
    expect(wrapper.find('[data-testid="signature-document"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="signature-card-status"]').attributes('aria-live')).toBe('polite')
  })

  it('speaks English when the page language is English, and passes the language to the bridge', async () => {
    setLocale('en')
    try {
      const detect = useBridge(null, [new CardBridgeError('unavailable')])
      const wrapper = await mountReady()
      expect(wrapper.get('[data-testid="signature-card-sign"]').text()).toBe('Sign with my eID card')
      await click(wrapper)
      expect(detect).toHaveBeenCalledWith(expect.anything(), 'en', undefined)
      expect(wrapper.get('[data-testid="signature-error"]').text()).toContain('No eID software was detected')
    } finally {
      setLocale('fr')
    }
  })

  it('behaves exactly as in lot 3 when no bridge is offered', async () => {
    const wrapper = await mountReady({ ...card, cardBridges: [] })
    expect(wrapper.find('[data-testid="signature-card"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="signature-upload"] h2').text()).toBe('Pour signer')
    expect(wrapper.find('[data-testid="signature-document"]').exists()).toBe(false)
  })

  it('signs in the page without a PIN field (Web eID) and shows the usual confirmation', async () => {
    const bridge = fakeBridge()
    const detect = useBridge(bridge)
    const wrapper = await mountReady()

    await click(wrapper)

    expect(detect).toHaveBeenCalledWith(['web-eid', 'beidconnect'], expect.any(String), undefined)
    expect(api.prepareCardSignature).toHaveBeenCalledWith(TOKEN, CERT)
    expect(bridge.sign).toHaveBeenCalledWith(expect.objectContaining({ certificate: CERT }), 'RElH', 'SHA-256', null)
    expect(api.completeCardSignature).toHaveBeenCalledWith(TOKEN, {
      ...CERT,
      signingTime: PREPARED.signingTime,
      documentDigest: 'abc',
      signature: 'U0lH',
    })
    expect(wrapper.find('[data-testid="signature-signed"]').exists()).toBe(true)
  })

  it('shows a visible status at each step', async () => {
    let finishRead!: () => void
    let finishSign!: () => void
    useBridge(
      fakeBridge({
        id: 'beidconnect',
        readCertificate: () => new Promise((r) => (finishRead = () => r(CERT))),
        sign: () => new Promise((r) => (finishSign = () => r('U0lH'))),
      }),
    )
    const wrapper = await mountReady()
    const status = () => wrapper.get('[data-testid="signature-card-status"]').text()

    await click(wrapper)
    expect(status()).toContain('Lecture de la carte')
    expect(wrapper.get('[data-testid="signature-card-sign"]').attributes('disabled')).toBeDefined()
    finishRead()
    await flushPromises()
    expect(status()).toContain('Signature en cours')
    finishSign()
    await flushPromises()
    expect(wrapper.find('[data-testid="signature-signed"]').exists()).toBe(true)
  })

  it('asks for the PIN in a labelled password field, signs with it and wipes it', async () => {
    const bridge = fakeBridge({ id: 'beidconnect', needsPin: true })
    useBridge(bridge)
    const wrapper = await mountReady()
    await click(wrapper)

    const input = wrapper.get('[data-testid="signature-pin"]')
    expect(input.attributes('type')).toBe('password')
    expect(input.attributes('autocomplete')).toBe('off')
    expect(input.attributes('minlength')).toBe('4')
    expect(input.attributes('maxlength')).toBe('12')
    expect(wrapper.get('label[for="signature-pin"]').text()).toContain('Code PIN')
    expect(wrapper.get('[data-testid="signature-pin-form"]').text()).toContain('n’est jamais envoyé à ce site')
    expect(api.prepareCardSignature).toHaveBeenCalled()
    expect(wrapper.get('[data-testid="signature-pin-submit"]').attributes('disabled')).toBeDefined()
    // Le déclin est impossible pendant la saisie.
    expect(wrapper.get('.portal__actions button.portal__secondary').attributes('disabled')).toBeDefined()

    await input.setValue('123')
    expect(wrapper.get('[data-testid="signature-pin-submit"]').attributes('disabled')).toBeDefined()
    await input.setValue('1234')
    expect(wrapper.get('[data-testid="signature-pin-submit"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('[data-testid="signature-pin-form"]').trigger('submit')
    await flushPromises()

    expect(bridge.sign).toHaveBeenCalledWith(expect.objectContaining({ certificate: CERT }), 'RElH', 'SHA-256', '1234')
    expect(JSON.stringify(vi.mocked(api.prepareCardSignature).mock.calls)).not.toContain('1234')
    expect(JSON.stringify(vi.mocked(api.completeCardSignature).mock.calls)).not.toContain('1234')
    expect(wrapper.find('[data-testid="signature-pin"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="signature-signed"]').exists()).toBe(true)
    expect(wrapper.html()).not.toContain('1234')
  })

  it('wipes the PIN and says how many attempts remain after a wrong PIN, then allows another try', async () => {
    const bridge = fakeBridge({ needsPin: true, sign: vi.fn(() => Promise.reject(new CardBridgeError('pin-incorrect', { attemptsLeft: 2 }))) })
    useBridge(bridge)
    const wrapper = await mountReady()
    await click(wrapper)
    await wrapper.get('[data-testid="signature-pin"]').setValue('0000')
    await wrapper.get('[data-testid="signature-pin-form"]').trigger('submit')
    await flushPromises()

    const alert = wrapper.get('[data-testid="signature-error"]')
    expect(alert.attributes('role')).toBe('alert')
    expect(alert.text()).toContain('Il vous reste 2 essai(s)')
    expect(alert.text()).not.toContain('0000')
    expect(wrapper.find('[data-testid="signature-pin"]').exists()).toBe(false)
    expect(wrapper.html()).not.toContain('0000')
    expect(wrapper.get('[data-testid="signature-card-sign"]').attributes('disabled')).toBeUndefined()
    expect(api.completeCardSignature).not.toHaveBeenCalled()
  })

  it('lets the signer cancel the PIN prompt', async () => {
    const bridge = fakeBridge({ needsPin: true })
    useBridge(bridge)
    const wrapper = await mountReady()
    await click(wrapper)
    await wrapper.get('[data-testid="signature-pin"]').setValue('1234')

    await wrapper.get('[data-testid="signature-pin-cancel"]').trigger('click')
    await flushPromises()

    expect(bridge.sign).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="signature-error"]').text()).toContain('annulée')
    expect(wrapper.find('[data-testid="signature-pin"]').exists()).toBe(false)
  })

  it('says no eID software was found, names both bridges and keeps the upload available', async () => {
    useBridge(null, [new CardBridgeError('unavailable'), new CardBridgeError('unavailable')])
    const wrapper = await mountReady()
    await click(wrapper)

    const message = wrapper.get('[data-testid="signature-error"]').text()
    expect(message).toContain('Aucun logiciel eID')
    expect(message).toContain('Web eID')
    expect(message).toContain('BeIDConnect')
    expect(api.prepareCardSignature).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="signature-upload"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="signature-send"]').attributes('disabled')).toBeDefined()
  })

  it('shows the more precise reason when a bridge is present but unusable', async () => {
    useBridge(null, [new CardBridgeError('update-required')])
    const wrapper = await mountReady()
    await click(wrapper)
    expect(wrapper.get('[data-testid="signature-error"]').text()).toContain('Mettez-le à jour')
  })

  it.each([
    ['no-reader', 'Aucun lecteur'],
    ['no-card', 'Aucune carte'],
    ['cancelled', 'annulée'],
    ['timeout', 'délai est dépassé'],
    ['card-blocked', 'bloquée'],
    ['insecure-context', 'connexion sécurisée'],
    ['unavailable', 'ne répond pas'],
    ['failed', 'a échoué'],
  ] as const)('translates the bridge error « %s » for the signer', async (kind, fragment) => {
    useBridge(fakeBridge({ readCertificate: () => Promise.reject(new CardBridgeError(kind)) }))
    const wrapper = await mountReady()
    await click(wrapper)
    expect(wrapper.get('[data-testid="signature-error"]').text()).toContain(fragment)
    expect(api.prepareCardSignature).not.toHaveBeenCalled()
  })

  it('explains an expired preparation and uses the upload messages for the shared refusal codes', async () => {
    useBridge(fakeBridge())
    const wrapper = await mountReady()
    vi.mocked(api.completeCardSignature).mockRejectedValueOnce(new api.PortalApiError(422, '', 'SIGNING_SESSION_EXPIRED'))
    await click(wrapper)
    expect(wrapper.get('[data-testid="signature-error"]').text()).toContain('préparation de la signature a expiré')

    vi.mocked(api.completeCardSignature).mockRejectedValueOnce(new api.PortalApiError(422, '', 'CERTIFICATE_REVOKED'))
    await click(wrapper)
    expect(wrapper.get('[data-testid="signature-error"]').text()).toContain('révoqué')

    vi.mocked(api.completeCardSignature).mockRejectedValueOnce(new api.PortalApiError(422, '', 'WHATEVER'))
    await click(wrapper)
    expect(wrapper.get('[data-testid="signature-error"]').text()).toContain('Le document signé a été refusé')
  })

  it('reloads the document when it changed meanwhile (STALE_DOCUMENT)', async () => {
    useBridge(fakeBridge())
    vi.mocked(api.prepareCardSignature).mockRejectedValue(new api.PortalApiError(422, '', 'STALE_DOCUMENT'))
    const wrapper = await mountReady()
    await click(wrapper)

    expect(api.fetchSignatureDocument).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[data-testid="signature-error"]').text()).toContain('Quelqu’un d’autre a signé')
  })

  it('shows the neutral screen for a 404 and the usual messages for 413 and 429', async () => {
    useBridge(fakeBridge())
    const wrapper = await mountReady()
    vi.mocked(api.prepareCardSignature).mockRejectedValueOnce(new api.PortalApiError(429, ''))
    await click(wrapper)
    expect(wrapper.get('[data-testid="signature-error"]').text()).toContain('Trop de tentatives')
    vi.mocked(api.prepareCardSignature).mockRejectedValueOnce(new api.PortalApiError(413, ''))
    await click(wrapper)
    expect(wrapper.get('[data-testid="signature-error"]').text()).toBe('Le fichier dépasse la taille autorisée.')
    vi.mocked(api.prepareCardSignature).mockRejectedValueOnce(new api.PortalApiError(404, 'secret'))
    await click(wrapper)
    expect(wrapper.find('[data-testid="portal-denied"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('secret')
  })

  it('keeps the decline path and blocks the upload while a card signature runs', async () => {
    let finishRead!: () => void
    useBridge(fakeBridge({ id: 'beidconnect', readCertificate: () => new Promise((r) => (finishRead = () => r(CERT))) }))
    const wrapper = await mountReady()
    await click(wrapper)
    expect(wrapper.get('[data-testid="signature-file"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('.portal__actions button.portal__secondary').attributes('disabled')).toBeDefined()
    finishRead()
    await flushPromises()
    expect(wrapper.find('[data-testid="signature-signed"]').exists()).toBe(true)

    const other = await mountReady()
    expect(other.get('.portal__actions button.portal__secondary').text()).toContain('Refuser')
    expect(other.get('.portal__actions button.portal__secondary').attributes('disabled')).toBeUndefined()
  })

  describe('choix du lecteur et du certificat (lot 3c)', () => {
    const CERT2 = { certificate: 'Q0VSVDI=', certificateChain: ['SU5UMg=='] }
    const two = (): CardChoice[] => [
      { id: '0:0', certificate: CERT, readerName: 'Lecteur A', holder: 'Alice Martin', notAfter: '2036-11-11T13:04:49Z', needsPin: true },
      { id: '1:0', certificate: CERT2, readerName: 'Lecteur B', holder: null, notAfter: null, needsPin: false },
    ]
    const withTwo = () => {
      const bridge = fakeBridge({ id: 'beidconnect', choices: two() })
      useBridge(bridge)
      return bridge
    }

    it('shows an accessible choice step before prepare, with the first choice preselected and nothing sent yet', async () => {
      withTwo()
      const wrapper = await mountReady()
      await click(wrapper)

      const form = wrapper.get('[data-testid="signature-choice-form"]')
      expect(form.get('legend').text()).toContain('Choisissez le certificat de signature')
      const radios = form.findAll('input[type="radio"]')
      expect(radios).toHaveLength(2)
      expect(new Set(radios.map((r) => r.attributes('name'))).size).toBe(1)
      expect((radios[0]!.element as HTMLInputElement).checked).toBe(true)
      expect(form.text()).toContain('Alice Martin')
      expect(form.text()).toContain('valable jusqu’au')
      expect(form.text()).toContain('Lecteur : Lecteur A')
      // Libellé de repli quand le certificat est illisible.
      expect(form.text()).toContain('Certificat 2')
      expect(form.text()).toContain('Lecteur : Lecteur B')
      expect(form.get('[data-testid="signature-choice-submit"]').text()).toBe('Continuer')
      expect(api.prepareCardSignature).not.toHaveBeenCalled()
    })

    it('uses the chosen certificate for prepare and sign, and the reader type of that choice for the PIN', async () => {
      const bridge = withTwo()
      const wrapper = await mountReady()
      await click(wrapper)
      await wrapper.findAll('[data-testid="signature-choice"]')[1]!.setValue(true)
      await wrapper.get('[data-testid="signature-choice-form"]').trigger('submit')
      await flushPromises()

      // Le second lecteur est à clavier : aucune saisie du PIN dans la page.
      expect(wrapper.find('[data-testid="signature-pin"]').exists()).toBe(false)
      expect(api.prepareCardSignature).toHaveBeenCalledWith(TOKEN, CERT2)
      expect(bridge.sign).toHaveBeenCalledWith(expect.objectContaining({ id: '1:0' }), 'RElH', 'SHA-256', null)
      expect(api.completeCardSignature).toHaveBeenCalledWith(TOKEN, expect.objectContaining({ certificate: CERT2.certificate, certificateChain: CERT2.certificateChain }))
      expect(wrapper.find('[data-testid="signature-signed"]').exists()).toBe(true)
    })

    it('asks for the PIN after choosing a certificate on a standard reader', async () => {
      withTwo()
      const wrapper = await mountReady()
      await click(wrapper)
      await wrapper.get('[data-testid="signature-choice-form"]').trigger('submit')
      await flushPromises()
      expect(wrapper.find('[data-testid="signature-pin"]').exists()).toBe(true)
      expect(api.prepareCardSignature).toHaveBeenCalledWith(TOKEN, CERT)
    })

    it('cancels from the choice step without any call to the server', async () => {
      const bridge = withTwo()
      const wrapper = await mountReady()
      await click(wrapper)
      await wrapper.get('[data-testid="signature-choice-cancel"]').trigger('click')
      await flushPromises()

      expect(wrapper.find('[data-testid="signature-choice-form"]').exists()).toBe(false)
      expect(wrapper.get('[data-testid="signature-error"]').text()).toContain('annulée')
      expect(api.prepareCardSignature).not.toHaveBeenCalled()
      expect(bridge.sign).not.toHaveBeenCalled()
      expect(wrapper.get('[data-testid="signature-card-sign"]').attributes('disabled')).toBeUndefined()
    })

    it('skips the step when there is a single choice', async () => {
      useBridge(fakeBridge({ id: 'beidconnect' }))
      const wrapper = await mountReady()
      await click(wrapper)
      expect(wrapper.find('[data-testid="signature-choice-form"]').exists()).toBe(false)
      expect(api.prepareCardSignature).toHaveBeenCalledTimes(1)
    })

    it('tells a Web eID user to choose the certificate in the software window', async () => {
      let finishRead!: () => void
      useBridge(fakeBridge({ id: 'web-eid', readCertificate: () => new Promise((r) => (finishRead = () => r(CERT))) }))
      const wrapper = await mountReady()
      await wrapper.get('[data-testid="signature-card-sign"]').trigger('click')
      await flushPromises()
      expect(wrapper.get('[data-testid="signature-card-status"]').text()).toContain('Choisissez votre certificat de signature dans la fenêtre du logiciel eID')
      expect(wrapper.find('[data-testid="signature-choice-form"]').exists()).toBe(false)
      finishRead()
      await flushPromises()
    })
  })

  describe('with a simulated BeIDConnect extension and the real HTTP client', () => {
    const extWindow = window as unknown as { EIDChromeExt?: unknown }

    afterEach(() => {
      delete extWindow.EIDChromeExt
      vi.unstubAllGlobals()
    })

    it('never sends the PIN to the server, nor keeps it in the page storage', async () => {
      const PIN = '987654'
      const signCalls: unknown[][] = []
      extWindow.EIDChromeExt = class {
        getVersion = () => Promise.resolve({ result: 'OK', version: '2.12' })
        getUserCertificates = () =>
          Promise.resolve({ result: 'OK', Readers: [{ ReaderType: 'standard', certificates: ['Q0VSVA=='] }] })
        getUserCertificateChain = () => Promise.resolve({ result: 'OK', certificateChain: { rootCA: 'Uk9PVA==', subCA: [] } })
        sign = (...args: unknown[]) => {
          signCalls.push(args)
          return Promise.resolve({ result: 'OK', signature: 'U0lH' })
        }
      }
      const fetchMock = vi.fn((url: string) =>
        Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve(String(url).endsWith('/prepare') ? PREPARED : String(url).endsWith('/complete') ? { status: 'SIGNED' } : { ...card, cardBridges: ['beidconnect'] }),
        }),
      )
      vi.stubGlobal('fetch', fetchMock)
      vi.restoreAllMocks() // retire les espions : l'API réelle parle à `fetch`
      URL.createObjectURL = vi.fn(() => 'blob:doc')
      vi.spyOn(api, 'fetchSignatureDocument').mockResolvedValue(new Blob(['pdf']))
      const wrapper = mount(PortalSignature, { props: { token: TOKEN } })
      await flushPromises()

      await wrapper.get('[data-testid="signature-card-sign"]').trigger('click')
      await flushPromises()
      await wrapper.get('[data-testid="signature-pin"]').setValue(PIN)
      await wrapper.get('[data-testid="signature-pin-form"]').trigger('submit')
      await flushPromises()

      expect(signCalls).toHaveLength(1)
      expect(signCalls[0]![5]).toBe(PIN) // le pont reçoit le PIN…
      const urls = fetchMock.mock.calls.map((c) => String(c[0]))
      expect(urls.some((u) => u.endsWith('/card-signature/prepare'))).toBe(true)
      expect(urls.some((u) => u.endsWith('/card-signature/complete'))).toBe(true)
      // … et le serveur n'en voit rien : ni URL, ni corps, ni en-têtes.
      expect(JSON.stringify(fetchMock.mock.calls.map((c) => [c[0], (c[1] as RequestInit | undefined)?.body, Array.from((c[1] as RequestInit | undefined)?.headers as Headers ?? [])]))).not.toContain(PIN)
      const complete = fetchMock.mock.calls.find((c) => String(c[0]).endsWith('/complete'))![1] as RequestInit
      expect(JSON.parse(complete.body as string)).toMatchObject({ signature: 'U0lH', certificateChain: ['Uk9PVA=='] })
      expect((complete.headers as Headers).get('X-Portal-Token')).toBe(TOKEN)
      expect(complete.credentials).toBe('omit')
      expect(JSON.stringify({ ...localStorage })).not.toContain(PIN)
      expect(JSON.stringify({ ...sessionStorage })).not.toContain(PIN)
      expect(window.location.href).not.toContain(PIN)
      expect(wrapper.html()).not.toContain(PIN)
      expect(wrapper.find('[data-testid="signature-signed"]').exists()).toBe(true)
    })
  })
})

/** Lot 3d — les quatre combinaisons `cardBridges` / `signedUpload` ; la carte ne dépend que de `cardBridges`. */
describe('PortalSignature, combinaisons cardBridges × signedUpload (lot 3d)', () => {
  const base: api.SignatureContext = { ...context, simulated: false }

  async function mountWith(ctx: Partial<api.SignatureContext>) {
    vi.restoreAllMocks()
    URL.createObjectURL = vi.fn(() => 'blob:doc')
    URL.revokeObjectURL = vi.fn()
    vi.spyOn(api, 'fetchSignatureDocument').mockResolvedValue(new Blob(['pdf']))
    vi.spyOn(api, 'fetchSignatureContext').mockResolvedValue({ ...base, ...ctx })
    const wrapper = mount(PortalSignature, { props: { token: TOKEN } })
    await flushPromises()
    return wrapper
  }
  const labels = (w: Awaited<ReturnType<typeof mountWith>>) => w.findAll('button').map((b) => b.text())

  it('default case: card only, no upload block, no click signature, document and decline kept', async () => {
    const w = await mountWith({ cardBridges: ['web-eid'], signedUpload: false })
    expect(w.find('[data-testid="signature-card"]').exists()).toBe(true)
    expect(w.find('[data-testid="signature-upload"]').exists()).toBe(false)
    expect(w.find('[data-testid="signature-download"]').exists()).toBe(false)
    expect(labels(w)).not.toContain('Signer')
    expect(labels(w)).toContain('Refuser')
    expect(w.find('[data-testid="signature-document"]').exists()).toBe(true)
  })

  it('without a bridge, the message does not point to another way', async () => {
    const w = await mountWith({ cardBridges: ['web-eid', 'beidconnect'], signedUpload: false })
    vi.spyOn(bridges, 'detectCardBridge').mockResolvedValue({ bridge: null, errors: [new CardBridgeError('unavailable')] })
    await w.get('[data-testid="signature-card-sign"]').trigger('click')
    await flushPromises()
    const text = w.get('[data-testid="signature-error"]').text()
    expect(text).toContain('Installez Web eID ou BeIDConnect')
    expect(text).toContain('contactez la personne qui vous a envoyé le lien')
    expect(text).not.toContain('autre moyen')
  })

  it('after a bridge error, the messages do not point to another way either', async () => {
    const w = await mountWith({ cardBridges: ['web-eid'], signedUpload: false })
    const bridge = { id: 'web-eid' as const, detect: () => Promise.resolve(), listChoices: () => Promise.reject(new CardBridgeError('failed')), certificateOf: vi.fn(), sign: vi.fn() }
    vi.spyOn(bridges, 'detectCardBridge').mockResolvedValue({ bridge, errors: [] })
    await w.get('[data-testid="signature-card-sign"]').trigger('click')
    await flushPromises()
    expect(w.get('[data-testid="signature-error"]').text()).not.toContain('autre moyen')
    expect(w.get('[data-testid="signature-error"]').text()).toContain('contactez la personne')
  })

  it('card and upload both offered: card first, upload as another way', async () => {
    const w = await mountWith({ cardBridges: ['web-eid'], signedUpload: true })
    expect(w.find('[data-testid="signature-card"]').exists()).toBe(true)
    expect(w.get('[data-testid="signature-upload"] h2').text()).toBe('Autre moyen : signer hors de cette page')
  })

  it('upload only (lot 3) and neither (unrelated providers) are unchanged', async () => {
    const upload = await mountWith({ cardBridges: [], signedUpload: true })
    expect(upload.find('[data-testid="signature-card"]').exists()).toBe(false)
    expect(upload.find('[data-testid="signature-upload"]').exists()).toBe(true)
    expect(labels(upload)).not.toContain('Signer')

    const neither = await mountWith({ cardBridges: [], signedUpload: false })
    expect(neither.find('[data-testid="signature-card"]').exists()).toBe(false)
    expect(neither.find('[data-testid="signature-upload"]').exists()).toBe(false)
    expect(labels(neither)).toContain('Signer')
  })

  it('explains a 409 SIGNED_UPLOAD_DISABLED on upload', async () => {
    const w = await mountWith({ cardBridges: [], signedUpload: true })
    vi.spyOn(api, 'uploadSignedDocument').mockRejectedValue(new api.PortalApiError(409, '', 'SIGNED_UPLOAD_DISABLED'))
    const input = w.get('[data-testid="signature-file"]')
    Object.defineProperty(input.element, 'files', { value: [new File(['%PDF'], 'a.pdf')], configurable: true })
    await input.trigger('change')
    await w.get('[data-testid="signature-send"]').trigger('click')
    await flushPromises()
    expect(w.get('[data-testid="signature-error"]').text()).toBe('Ce moyen n’est pas disponible. Signez avec votre carte eID depuis cette page.')
  })
})
