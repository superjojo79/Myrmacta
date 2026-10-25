import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PortalLinksPanel from './PortalLinksPanel.vue'
import * as api from '@/api/portalLinks'

vi.mock('@/api/portalLinks')

function link(overrides: Partial<api.PortalLinkView>): api.PortalLinkView {
  return {
    id: 'l1',
    taskId: 't1',
    status: 'ACTIVE',
    statusReason: null,
    recipient: 'alice@example.org',
    ttlDays: 7,
    createdAt: '2026-09-24T10:00:00Z',
    sentAt: '2026-09-24T10:00:10Z',
    expiresAt: '2026-10-01T10:00:10Z',
    lastAccessedAt: null,
    completedAt: null,
    revokedAt: null,
    createdBy: 'flowable-task-created',
    revokedBy: null,
    ...overrides,
  }
}

describe('PortalLinksPanel (V-INT-1)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('stays hidden when the dossier has no portal link', async () => {
    vi.mocked(api.listPortalLinks).mockResolvedValue({ canManage: true, links: [] })
    const wrapper = mount(PortalLinksPanel, { props: { dossierId: 'd1' } })
    await flushPromises()
    expect(wrapper.find('[data-testid="portal-links-panel"]').exists()).toBe(false)
  })

  it('stays hidden when the API is unavailable', async () => {
    vi.mocked(api.listPortalLinks).mockRejectedValue(new Error('404'))
    const wrapper = mount(PortalLinksPanel, { props: { dossierId: 'd1' } })
    await flushPromises()
    expect(wrapper.find('[data-testid="portal-links-panel"]').exists()).toBe(false)
  })

  it('shows status, reason and recipient, with actions for managers only on the latest link', async () => {
    vi.mocked(api.listPortalLinks).mockResolvedValue({
      canManage: true,
      links: [
        link({ id: 'l2', status: 'NOT_SENT', statusReason: 'NO_ADDRESS', recipient: null, sentAt: null }),
        link({ id: 'l1', status: 'REVOKED', statusReason: 'RESENT' }),
      ],
    })
    const wrapper = mount(PortalLinksPanel, { props: { dossierId: 'd1' } })
    await flushPromises()

    const rows = wrapper.findAll('tbody tr')
    expect(rows[0]!.text()).toContain('Non envoyé — aucune adresse e-mail renseignée')
    expect(rows[0]!.findAll('button').map((b) => b.text())).toEqual(['Envoyer', 'Révoquer'])
    expect(rows[1]!.text()).toContain('Révoqué — remplacé par un nouvel envoi')
    expect(rows[1]!.findAll('button')).toHaveLength(0)
  })

  it('hides actions for read-only viewers (masked recipient comes from the API)', async () => {
    vi.mocked(api.listPortalLinks).mockResolvedValue({
      canManage: false,
      links: [link({ recipient: 'a***@example.org' })],
    })
    const wrapper = mount(PortalLinksPanel, { props: { dossierId: 'd1' } })
    await flushPromises()

    expect(wrapper.text()).toContain('a***@example.org')
    expect(wrapper.findAll('button')).toHaveLength(0)
  })

  it('resends then reloads, and explains a NOT_SENT outcome', async () => {
    vi.mocked(api.listPortalLinks).mockResolvedValue({ canManage: true, links: [link({ status: 'EXPIRED' })] })
    vi.mocked(api.sendPortalLink).mockResolvedValue(
      link({ id: 'l3', status: 'NOT_SENT', statusReason: 'INVALID_ADDRESS' }),
    )
    const wrapper = mount(PortalLinksPanel, { props: { dossierId: 'd1' } })
    await flushPromises()

    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(api.sendPortalLink).toHaveBeenCalledWith('d1', 't1')
    expect(api.listPortalLinks).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[role="alert"]').text()).toContain('adresse e-mail invalide')
  })

  it('revokes a live link', async () => {
    vi.mocked(api.listPortalLinks).mockResolvedValue({ canManage: true, links: [link({})] })
    vi.mocked(api.revokePortalLink).mockResolvedValue(link({ status: 'REVOKED' }))
    const wrapper = mount(PortalLinksPanel, { props: { dossierId: 'd1' } })
    await flushPromises()

    const revoke = wrapper.findAll('button').find((b) => b.text() === 'Révoquer')!
    await revoke.trigger('click')
    await flushPromises()

    expect(api.revokePortalLink).toHaveBeenCalledWith('d1', 'l1')
  })

  /** TODO §103 (T8, T2) — trace de l'authentification forte et raison INVALID_IDENTITY_FIELD. */
  it('shows the strong authentication trace and the identity field reason', async () => {
    vi.mocked(api.listPortalLinks).mockResolvedValue({
      canManage: true,
      links: [
        link({
          id: 'l2',
          status: 'COMPLETED',
          identity: {
            provider: 'fas',
            level: 'HIGH',
            givenName: 'Alice',
            familyName: 'Martin',
            result: 'PREMIER_ENREGISTREMENT',
            verifiedAt: '2026-10-24T10:00:00Z',
          },
        }),
        link({ id: 'l1', taskId: 't2', status: 'NOT_SENT', statusReason: 'INVALID_IDENTITY_FIELD', sentAt: null }),
      ],
    })
    const wrapper = mount(PortalLinksPanel, { props: { dossierId: 'd1' } })
    await flushPromises()

    expect(wrapper.get('[data-testid="portal-link-identity"]').text()).toBe(
      'Identité vérifiée (FAS, niveau élevé) : Alice Martin — NRN enregistré dans le dossier',
    )
    expect(wrapper.findAll('tbody tr')[1]!.text()).toContain('champ d’identité absent, non textuel ou non sensible')
  })
})
