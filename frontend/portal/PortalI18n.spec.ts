import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PortalApp from './PortalApp.vue'
import * as api from './api'
import { setLocale } from '@/i18n'

const TOKEN = 'k'.repeat(43)

const task: api.PortalTask = {
  dossierReference: 'DOS-2026-0042',
  taskName: '',
  instructions: null,
  form: { label: 'Your opinion', fields: [] },
  expiresAt: '2026-10-01T10:00:00Z',
  attachments: [],
}

/** TODO §90 (D7) — le portail public suit la langue (lien d'e-mail ?lang=, choix, navigateur) et la change sans recharger. */
describe('PortalApp in English (D7)', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    setLocale('en')
  })

  it('shows the neutral screens in English', async () => {
    const wrapper = mount(PortalApp, { props: { token: null } })
    await flushPromises()
    expect(wrapper.get('[data-testid="portal-no-token"]').text()).toContain('Open the link you received by e-mail')

    vi.spyOn(api, 'fetchTask').mockRejectedValue(new api.PortalApiError(404, 'x'))
    const denied = mount(PortalApp, { props: { token: TOKEN } })
    await flushPromises()
    expect(denied.get('[data-testid="portal-denied"]').text()).toContain('Invalid or expired link')
  })

  it('shows the task with the English default title, the day-first English date and English errors', async () => {
    vi.spyOn(api, 'fetchTask').mockResolvedValue(task)
    vi.spyOn(api, 'uploadAttachment').mockRejectedValue(new api.PortalApiError(429, ''))
    const wrapper = mount(PortalApp, { props: { token: TOKEN } })
    await flushPromises()
    expect(wrapper.text()).toContain('Dossier DOS-2026-0042')
    expect(wrapper.text()).toContain('Request for information')
    expect(wrapper.text()).toMatch(/Link valid until 1 October 2026\./)
    await wrapper.findAll('button').find((b) => b.text() === 'Upload the file')!.trigger('click')
    expect(wrapper.text()).toContain('Choose a file.')
  })

  it('switching the language re-renders the page in place', async () => {
    const wrapper = mount(PortalApp, { props: { token: null } })
    await flushPromises()
    expect(wrapper.text()).toContain('Open the link you received by e-mail')
    await wrapper.get('[data-testid="locale-switcher"]').setValue('fr')
    expect(wrapper.text()).toContain('Ouvrez le lien reçu par e-mail')
    expect(document.documentElement.lang).toBe('fr')
  })
})
