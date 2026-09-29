import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SignatureRequestsPanel from './SignatureRequestsPanel.vue'
import * as api from '@/api/signatures'

vi.mock('@/api/signatures')

function request(overrides: Partial<api.SignatureRequestView> = {}): api.SignatureRequestView {
  return {
    id: 'r1',
    status: 'PENDING',
    statusReason: null,
    mode: 'PARALLEL',
    simulated: true,
    providerCode: 'stub',
    sourceElementId: 'e1',
    deadlineAt: '2026-10-30T10:00:00Z',
    createdAt: '2026-09-30T10:00:00Z',
    completedAt: null,
    canManage: true,
    signers: [
      {
        id: 's1', position: 0, kind: 'EXTERNAL', required: true, displayName: 'Alice', contact: 'a***@example.com',
        status: 'PENDING', decidedAt: null, declineReason: null, invited: true,
      },
      {
        id: 's2', position: 1, kind: 'EXTERNAL', required: false, displayName: 'Bob', contact: 'b***@example.com',
        status: 'DECLINED', decidedAt: '2026-10-01T10:00:00Z', declineReason: 'Non merci', invited: true,
      },
    ],
    ...overrides,
  }
}

describe('SignatureRequestsPanel (S-VT-2)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('stays hidden without request or when the API is unavailable', async () => {
    vi.mocked(api.listSignatureRequests).mockResolvedValue({ canManage: true, requests: [] })
    const empty = mount(SignatureRequestsPanel, { props: { dossierId: 'd1' } })
    await flushPromises()
    expect(empty.find('[data-testid="signature-requests-panel"]').exists()).toBe(false)

    vi.mocked(api.listSignatureRequests).mockRejectedValue(new Error('404'))
    const down = mount(SignatureRequestsPanel, { props: { dossierId: 'd1' } })
    await flushPromises()
    expect(down.find('[data-testid="signature-requests-panel"]').exists()).toBe(false)
  })

  it('shows statuses, masked contacts, decline reason and the simulation banner', async () => {
    vi.mocked(api.listSignatureRequests).mockResolvedValue({ canManage: true, requests: [request()] })
    const wrapper = mount(SignatureRequestsPanel, { props: { dossierId: 'd1' } })
    await flushPromises()

    expect(wrapper.find('[data-testid="signature-simulation-banner"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('a***@example.com')
    expect(wrapper.text()).toContain('Non merci')
    expect(wrapper.text()).toContain('(facultatif)')
    expect(wrapper.text()).toContain('En cours')
  })

  it('offers cancel and resend to managers only, then reloads', async () => {
    vi.mocked(api.listSignatureRequests).mockResolvedValue({ canManage: true, requests: [request()] })
    vi.mocked(api.cancelSignatureRequest).mockResolvedValue(request({ status: 'CANCELLED' }))
    vi.mocked(api.resendSignatureInvite).mockResolvedValue(request())
    const wrapper = mount(SignatureRequestsPanel, { props: { dossierId: 'd1' } })
    await flushPromises()

    const resend = wrapper.findAll('button').find((b) => b.text() === 'Renvoyer')!
    await resend.trigger('click')
    await flushPromises()
    expect(api.resendSignatureInvite).toHaveBeenCalledWith('r1', 's1')

    await wrapper.find('[data-testid="signature-cancel"]').trigger('click')
    await flushPromises()
    expect(api.cancelSignatureRequest).toHaveBeenCalledWith('r1')
    expect(api.listSignatureRequests).toHaveBeenCalledTimes(3)
  })

  it('hides every action without the manage right and hides the banner for a real provider', async () => {
    vi.mocked(api.listSignatureRequests).mockResolvedValue({
      canManage: false,
      requests: [request({ simulated: false, canManage: false })],
    })
    const wrapper = mount(SignatureRequestsPanel, { props: { dossierId: 'd1' } })
    await flushPromises()

    expect(wrapper.findAll('button')).toHaveLength(0)
    expect(wrapper.find('[data-testid="signature-simulation-banner"]').exists()).toBe(false)
  })

  it('shows the server problem detail when an action fails', async () => {
    vi.mocked(api.listSignatureRequests).mockResolvedValue({ canManage: true, requests: [request()] })
    vi.mocked(api.cancelSignatureRequest).mockRejectedValue({ response: { data: { detail: 'Déjà close' } } })
    const wrapper = mount(SignatureRequestsPanel, { props: { dossierId: 'd1' } })
    await flushPromises()

    await wrapper.find('[data-testid="signature-cancel"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
  })
})
