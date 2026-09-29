import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MySignaturesView from './MySignaturesView.vue'
import * as api from '@/api/signatures'

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
})
