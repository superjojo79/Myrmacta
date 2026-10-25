import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PortalApp from './PortalApp.vue'
import * as api from './api'

const TOKEN = 'k'.repeat(43)

const task: api.PortalTask = {
  dossierReference: 'DOS-2026-0042',
  taskName: 'Avis du tiers',
  instructions: 'Merci de joindre le devis.',
  form: {
    label: 'Votre avis',
    fields: [
      { name: 'disponible', label: 'Disponible ?', type: 'BOOLEAN', required: true },
      { name: 'commentaire', label: 'Commentaire', type: 'STRING', required: false },
    ],
  },
  expiresAt: '2026-10-01T10:00:00Z',
  attachments: [],
}

describe('PortalApp', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('shows the task, its form and instructions (V-PORTAL-3)', async () => {
    vi.spyOn(api, 'fetchTask').mockResolvedValue(task)
    const wrapper = mount(PortalApp, { props: { token: TOKEN } })
    await flushPromises()

    expect(wrapper.text()).toContain('Avis du tiers')
    expect(wrapper.text()).toContain('DOS-2026-0042')
    expect(wrapper.text()).toContain('Merci de joindre le devis.')
    expect(wrapper.find('#field-disponible').exists()).toBe(true)
  })

  it('shows the same neutral screen for any 404 (V-PORTAL-2)', async () => {
    vi.spyOn(api, 'fetchTask').mockRejectedValue(new api.PortalApiError(404, 'whatever the server says'))
    const wrapper = mount(PortalApp, { props: { token: TOKEN } })
    await flushPromises()

    expect(wrapper.find('[data-testid="portal-denied"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('whatever the server says')
  })

  it('asks to reopen the e-mail link when there is no token, without calling the API', async () => {
    const fetchSpy = vi.spyOn(api, 'fetchTask')
    const wrapper = mount(PortalApp, { props: { token: null } })
    await flushPromises()

    expect(wrapper.find('[data-testid="portal-no-token"]').exists()).toBe(true)
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it('submits the answers and shows the confirmation (V-PORTAL-3)', async () => {
    vi.spyOn(api, 'fetchTask').mockResolvedValue(task)
    const submit = vi.spyOn(api, 'submitAnswers').mockResolvedValue()
    const wrapper = mount(PortalApp, { props: { token: TOKEN } })
    await flushPromises()

    await wrapper.find('#field-disponible').setValue(true)
    await wrapper.find('#field-commentaire').setValue('Dispo lundi')
    await wrapper.find('.portal__primary').trigger('click')
    await flushPromises()

    expect(submit).toHaveBeenCalledWith(TOKEN, { disponible: true, commentaire: 'Dispo lundi' })
    expect(wrapper.find('[data-testid="portal-done"]').exists()).toBe(true)
  })

  it('shows validation errors and keeps the form (V-PORTAL-3)', async () => {
    vi.spyOn(api, 'fetchTask').mockResolvedValue(task)
    vi.spyOn(api, 'submitAnswers').mockRejectedValue(new api.PortalApiError(400, 'Le champ Disponible est requis'))
    const wrapper = mount(PortalApp, { props: { token: TOKEN } })
    await flushPromises()

    await wrapper.find('.portal__primary').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="portal-form-error"]').text()).toContain('Disponible est requis')
    expect(wrapper.find('#field-disponible').exists()).toBe(true)
  })

  it('uploads an attachment and lists it (V-PORTAL-4)', async () => {
    vi.spyOn(api, 'fetchTask').mockResolvedValue(task)
    const upload = vi.spyOn(api, 'uploadAttachment').mockResolvedValue({
      id: 'a1', label: 'Devis signé', filename: 'devis.pdf', addedAt: '2026-09-24T10:00:00Z',
    })
    const wrapper = mount(PortalApp, { props: { token: TOKEN } })
    await flushPromises()

    const input = wrapper.find('#portal-file').element as HTMLInputElement
    Object.defineProperty(input, 'files', { value: [new File(['%PDF'], 'devis.pdf', { type: 'application/pdf' })] })
    await wrapper.find('#portal-file-label').setValue('Devis signé')
    await wrapper.find('.portal__secondary').trigger('click')
    await flushPromises()

    expect(upload).toHaveBeenCalled()
    expect(wrapper.find('[data-testid="portal-attachments"]').text()).toContain('Devis signé')
  })

  it('reports an upload error without leaving the page (V-PORTAL-4)', async () => {
    vi.spyOn(api, 'fetchTask').mockResolvedValue(task)
    vi.spyOn(api, 'uploadAttachment').mockRejectedValue(new api.PortalApiError(413, ''))
    const wrapper = mount(PortalApp, { props: { token: TOKEN } })
    await flushPromises()

    const input = wrapper.find('#portal-file').element as HTMLInputElement
    Object.defineProperty(input, 'files', { value: [new File(['x'], 'big.pdf', { type: 'application/pdf' })] })
    await wrapper.find('.portal__secondary').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('taille autorisée')
  })

  it('explains the rate limit instead of the neutral screen (429)', async () => {
    vi.spyOn(api, 'fetchTask').mockResolvedValue(task)
    vi.spyOn(api, 'submitAnswers').mockRejectedValue(new api.PortalApiError(429, ''))
    const wrapper = mount(PortalApp, { props: { token: TOKEN } })
    await flushPromises()

    await wrapper.find('.portal__primary').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="portal-form-error"]').text()).toContain('Patientez une minute')
  })

  it('shows a transient unavailability screen on a network failure', async () => {
    vi.spyOn(api, 'fetchTask').mockRejectedValue(new TypeError('Failed to fetch'))
    const wrapper = mount(PortalApp, { props: { token: TOKEN } })
    await flushPromises()

    expect(wrapper.find('[data-testid="portal-unavailable"]').exists()).toBe(true)
  })

  it('asks for a file before uploading', async () => {
    vi.spyOn(api, 'fetchTask').mockResolvedValue(task)
    const upload = vi.spyOn(api, 'uploadAttachment')
    const wrapper = mount(PortalApp, { props: { token: TOKEN } })
    await flushPromises()

    await wrapper.find('.portal__secondary').trigger('click')

    expect(upload).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Choisissez un fichier.')
  })

  it('switches to the neutral screen when the link dies while the page is open (404 on submit)', async () => {
    vi.spyOn(api, 'fetchTask').mockResolvedValue(task)
    vi.spyOn(api, 'submitAnswers').mockRejectedValue(new api.PortalApiError(404, ''))
    const wrapper = mount(PortalApp, { props: { token: TOKEN } })
    await flushPromises()

    await wrapper.find('.portal__primary').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="portal-denied"]').exists()).toBe(true)
  })

  /** TODO §104 (T3b, D-BEAUTH-PROOF, T10) — authentification forte d'un tiers. */
  describe('strong authentication', () => {
    const reduced: api.PortalTask = {
      dossierReference: 'DOS-2026-0042',
      taskName: 'Avis du demandeur',
      instructions: null,
      form: { label: null, fields: [] },
      expiresAt: '2026-10-01T10:00:00Z',
      attachments: [],
      identity: {
        verified: false,
        name: null,
        providers: [
          { id: 'fas', label: 'eID, itsme et autres (FAS)' },
          { id: 'itsme', label: 'itsme' },
        ],
      },
    }

    it('shows only the task name, the reference and one button per provider', async () => {
      vi.spyOn(api, 'fetchTask').mockResolvedValue(reduced)
      const wrapper = mount(PortalApp, { props: { token: TOKEN } })
      await flushPromises()

      const screen = wrapper.get('[data-testid="portal-identity-required"]')
      expect(screen.text()).toContain('Avis du demandeur')
      expect(wrapper.text()).toContain('DOS-2026-0042')
      expect(screen.findAll('button').map((b) => b.text())).toEqual(['eID, itsme et autres (FAS)', 'itsme'])
      expect(wrapper.find('#portal-file').exists()).toBe(false)
      expect(wrapper.text()).not.toContain('Envoyer ma réponse')
    })

    it('starts the authentication with the link token and navigates to the provider', async () => {
      vi.spyOn(api, 'fetchTask').mockResolvedValue(reduced)
      const start = vi.spyOn(api, 'startIdentity').mockResolvedValue('https://idp.example/auth?state=s')
      const navigate = vi.spyOn(api, 'navigateTo').mockImplementation(() => {})
      const wrapper = mount(PortalApp, { props: { token: TOKEN } })
      await flushPromises()

      await wrapper.get('[data-testid="portal-identity-itsme"]').trigger('click')
      await flushPromises()

      expect(start).toHaveBeenCalledWith(TOKEN, 'itsme')
      expect(navigate).toHaveBeenCalledWith('https://idp.example/auth?state=s')
    })

    it('uses the identity proof once back from the provider, and shows who is verified', async () => {
      const fetchSpy = vi.spyOn(api, 'fetchTask').mockResolvedValue({
        ...task,
        identity: { verified: true, name: 'Alice Martin', providers: [] },
      })
      const submit = vi.spyOn(api, 'submitAnswers').mockResolvedValue()
      const wrapper = mount(PortalApp, { props: { token: TOKEN, kind: 'identity' } })
      await flushPromises()

      expect(fetchSpy).toHaveBeenCalledWith({ proof: TOKEN })
      expect(wrapper.get('[data-testid="portal-identity-verified"]').text()).toContain('Alice Martin')
      await wrapper.get('#field-disponible').setValue(true)
      await wrapper.find('.portal__actions .portal__primary').trigger('click')
      await flushPromises()
      expect(submit).toHaveBeenCalledWith({ proof: TOKEN }, expect.anything())
    })

    it('shows the neutral refusal without calling the API', async () => {
      const fetchSpy = vi.spyOn(api, 'fetchTask')
      const wrapper = mount(PortalApp, { props: { token: null, kind: 'identity-refused' } })
      await flushPromises()

      expect(wrapper.get('[data-testid="portal-identity-refused"]').text()).toContain('Identification non reconnue')
      expect(fetchSpy).not.toHaveBeenCalled()
    })

    it('shows the denied screen when the link is no longer valid at start', async () => {
      vi.spyOn(api, 'fetchTask').mockResolvedValue(reduced)
      vi.spyOn(api, 'startIdentity').mockRejectedValue(new api.PortalApiError(404, ''))
      const wrapper = mount(PortalApp, { props: { token: TOKEN } })
      await flushPromises()

      await wrapper.get('[data-testid="portal-identity-fas"]').trigger('click')
      await flushPromises()

      expect(wrapper.find('[data-testid="portal-denied"]').exists()).toBe(true)
    })
  })
})
