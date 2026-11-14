import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PortalApp from './PortalApp.vue'
import PortalSignature from './PortalSignature.vue'
import * as api from './api'

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
