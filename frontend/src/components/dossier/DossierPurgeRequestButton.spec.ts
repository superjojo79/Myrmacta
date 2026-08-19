import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import DossierPurgeRequestButton from './DossierPurgeRequestButton.vue'
import * as api from '@/api/purgeOutbox'

vi.mock('@/api/purgeOutbox')

describe('DossierPurgeRequestButton', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('enqueues purge on click', async () => {
    vi.mocked(api.enqueueDossierPurge).mockResolvedValue()
    const wrapper = mount(DossierPurgeRequestButton, { props: { dossierId: 'd1' } })
    await wrapper.get('[data-testid="dossier-purge-request"]').trigger('click')
    await flushPromises()
    expect(api.enqueueDossierPurge).toHaveBeenCalledWith('d1')
    expect(wrapper.text()).toContain('Demande enregistrée')
  })

  it('surfaces ProblemDetail on 409', async () => {
    vi.mocked(api.enqueueDossierPurge).mockRejectedValue({
      response: { status: 409, data: { detail: 'Purge enqueue requires CLOTURE or ARCHIVE, was OUVERT' } },
    })
    const wrapper = mount(DossierPurgeRequestButton, { props: { dossierId: 'd1' } })
    await wrapper.get('[data-testid="dossier-purge-request"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Purge enqueue requires CLOTURE or ARCHIVE, was OUVERT')
  })
})
