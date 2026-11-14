import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MySignaturesView from './MySignaturesView.vue'
import * as api from '@/api/signatures'
import * as lib from '@/lib/signedUpload'

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
