import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import ParcoursView from './ParcoursView.vue'
import * as api from '@/api/parcours'
import * as resolver from '@/components/workflow/bpmn-blocks/valueSourceResolver'

vi.mock('@/api/parcours')
vi.mock('@/components/workflow/bpmn-blocks/valueSourceResolver')

const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/admin/drafts', component: { template: '<i/>' } }] })
const mountView = () => mount(ParcoursView, { global: { plugins: [router] } })

const definition: api.ParcoursDefinition = {
  statuses: [{ code: 'OUVERT', label: 'Ouvert', initial: true, outcome: 'NONE' }, { code: 'FIN', label: 'Fin', initial: false, outcome: 'COMPLETED' }],
  allowedDossierStatuses: ['OUVERT'],
  onStartDossierStatus: null,
  steps: [{ id: 'saisie', kind: 'QUESTION', params: { schemaKey: 'avis' }, next: null, branches: [], defaultNext: null }],
}

describe('ParcoursView (LC5)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listParcours).mockResolvedValue([])
    vi.mocked(resolver.resolveValueSource).mockResolvedValue([{ value: 'avis', label: 'Avis' }])
  })

  it('lists the parcours and opens one for editing', async () => {
    vi.mocked(api.listParcours).mockResolvedValue([{ key: 'sinistre', label: 'Sinistre', version: 2, steps: 1, updatedAt: 't', updatedBy: 'u' }])
    vi.mocked(api.getParcours).mockResolvedValue({ key: 'sinistre', label: 'Sinistre', version: 2, definition, updatedAt: 't', updatedBy: 'u' })
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.findAll('[data-testid="parcours-row"]')).toHaveLength(1)
    await wrapper.get('[data-testid="parcours-open-sinistre"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="parcours-editor"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid="parcours-step"]')).toHaveLength(1)
    expect((wrapper.get('[data-testid="parcours-key"]').element as HTMLInputElement).disabled).toBe(true)
  })

  it('creates a parcours with a step and saves it without a version', async () => {
    vi.mocked(api.saveParcours).mockResolvedValue({ key: 'p1', label: 'P', version: 1, definition, updatedAt: 't', updatedBy: 'u' })
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="parcours-new"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="parcours-key"]').setValue('p1')
    await wrapper.get('[data-testid="parcours-label"]').setValue('P')
    await wrapper.get('[data-testid="step-add"]').trigger('click')
    await wrapper.get('[data-testid="param-schemaKey"]').setValue('avis')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()
    const call = vi.mocked(api.saveParcours).mock.calls[0]
    expect(call[0]).toBe('p1')
    expect(call[2].steps[0].params).toEqual({ schemaKey: 'avis' })
    expect(call[3]).toBeNull()
    expect(wrapper.get('[data-testid="parcours-notice"]').text()).toContain('version 1')
  })

  it('refuses to compile an unsaved parcours and shows the server message when saving fails', async () => {
    vi.mocked(api.saveParcours).mockRejectedValue({ response: { status: 400, data: { detail: 'step a: unknown next nowhere' } } })
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="parcours-new"]').trigger('click')
    await wrapper.get('[data-testid="parcours-key"]').setValue('p1')
    await wrapper.get('[data-testid="parcours-label"]').setValue('P')
    expect((wrapper.get('[data-testid="parcours-compile"]').element as HTMLButtonElement).disabled).toBe(true)
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()
    expect(wrapper.get('[data-testid="parcours-error"]').text()).toContain('nowhere')
  })

  it('compiles into a draft, links to the drafts and shows warnings and the XML as plain text', async () => {
    vi.mocked(api.getParcours).mockResolvedValue({ key: 'p', label: 'P', version: 3, definition, updatedAt: 't', updatedBy: 'u' })
    vi.mocked(api.listParcours).mockResolvedValue([{ key: 'p', label: 'P', version: 3, steps: 1, updatedAt: 't', updatedBy: 'u' }])
    vi.mocked(api.compileParcours).mockResolvedValue({
      xml: '<definitions><script>alert(1)</script></definitions>', sha256: 'a'.repeat(64),
      warnings: ['step saisie: variable x is never written'], draftWritten: true, replacedExistingDraft: false,
    })
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="parcours-open-p"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="parcours-compile"]').trigger('click')
    await flushPromises()
    expect(api.compileParcours).toHaveBeenCalledWith('p', false, false)
    expect(wrapper.find('[data-testid="parcours-draft-link"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="parcours-warnings"]').text()).toContain('never written')
    expect(wrapper.get('[data-testid="parcours-xml"]').text()).toContain('<script>alert(1)</script>')
    expect(wrapper.find('[data-testid="parcours-xml"] script').exists()).toBe(false)
  })

  it('a dry-run validates without announcing a draft', async () => {
    vi.mocked(api.getParcours).mockResolvedValue({ key: 'p', label: 'P', version: 1, definition, updatedAt: 't', updatedBy: 'u' })
    vi.mocked(api.listParcours).mockResolvedValue([{ key: 'p', label: 'P', version: 1, steps: 1, updatedAt: 't', updatedBy: 'u' }])
    vi.mocked(api.compileParcours).mockResolvedValue({ xml: '<x/>', sha256: 'b'.repeat(64), warnings: [], draftWritten: false, replacedExistingDraft: false })
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="parcours-open-p"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="parcours-validate"]').trigger('click')
    await flushPromises()
    expect(api.compileParcours).toHaveBeenCalledWith('p', true, false)
    expect(wrapper.find('[data-testid="parcours-draft-link"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="parcours-notice"]').text()).toContain('rien n’a été écrit')
  })

  it('a decision step edits conditions and a default destination', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="parcours-new"]').trigger('click')
    await wrapper.get('[data-testid="step-add"]').trigger('click')
    await wrapper.get('[data-testid="step-kind"]').setValue('DECISION')
    expect(wrapper.findAll('[data-testid="branch"]')).toHaveLength(1)
    expect(wrapper.find('[data-testid="step-default"]').exists()).toBe(true)
    await wrapper.get('button[aria-label="Retirer la condition"]').trigger('click')
    expect(wrapper.findAll('[data-testid="branch"]')).toHaveLength(0)
  })

  it('blocks compilation while the form has unsaved changes (the server compiles the saved version)', async () => {
    vi.mocked(api.getParcours).mockResolvedValue({ key: 'p', label: 'P', version: 1, definition, updatedAt: 't', updatedBy: 'u' })
    vi.mocked(api.listParcours).mockResolvedValue([{ key: 'p', label: 'P', version: 1, steps: 1, updatedAt: 't', updatedBy: 'u' }])
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="parcours-open-p"]').trigger('click')
    await flushPromises()
    expect((wrapper.get('[data-testid="parcours-compile"]').element as HTMLButtonElement).disabled).toBe(false)
    await wrapper.get('[data-testid="parcours-label"]').setValue('Autre libellé')
    expect((wrapper.get('[data-testid="parcours-compile"]').element as HTMLButtonElement).disabled).toBe(true)
    expect((wrapper.get('[data-testid="parcours-validate"]').element as HTMLButtonElement).disabled).toBe(true)
  })

  it('shows why the deployment would refuse a previewed BPMN and can ask to replace another author\'s draft', async () => {
    vi.mocked(api.getParcours).mockResolvedValue({ key: 'p', label: 'P', version: 1, definition, updatedAt: 't', updatedBy: 'u' })
    vi.mocked(api.listParcours).mockResolvedValue([{ key: 'p', label: 'P', version: 1, steps: 1, updatedAt: 't', updatedBy: 'u' }])
    vi.mocked(api.previewParcours).mockResolvedValue({
      xml: '<x/>', sha256: 'd'.repeat(64), warnings: [], draftWritten: false, replacedExistingDraft: false, problem: 'unknown document template x',
    })
    vi.mocked(api.compileParcours).mockResolvedValue({ xml: '<x/>', sha256: 'e'.repeat(64), warnings: [], draftWritten: true, replacedExistingDraft: true })
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="parcours-open-p"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="parcours-preview"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="parcours-problem"]').text()).toContain('unknown document template x')
    await wrapper.get('[data-testid="parcours-replace"]').setValue(true)
    await wrapper.get('[data-testid="parcours-compile"]').trigger('click')
    await flushPromises()
    expect(api.compileParcours).toHaveBeenCalledWith('p', false, true)
    expect(wrapper.get('[data-testid="parcours-draft-link"]').text()).toContain('remplace')
  })
})
