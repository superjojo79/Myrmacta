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
})
