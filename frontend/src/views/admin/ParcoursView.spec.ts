import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import ParcoursView from './ParcoursView.vue'
import * as api from '@/api/parcours'
import * as resolver from '@/components/workflow/bpmn-blocks/valueSourceResolver'
import * as dossierTypes from '@/api/dossierTypes'
import * as schemas from '@/api/taskQuestionSchemas'
import * as expressions from '@/api/expressions'
import type { DossierType } from '@/api/types'

vi.mock('@/api/parcours')
vi.mock('@/components/workflow/bpmn-blocks/valueSourceResolver')
vi.mock('@/api/dossierTypes')
vi.mock('@/api/taskQuestionSchemas')
vi.mock('@/api/expressions')
const importXML = vi.hoisted(() => vi.fn())
vi.mock('bpmn-js/lib/NavigatedViewer', () => ({
  default: class {
    importXML = importXML
    destroy = vi.fn()
    get = () => ({ zoom: vi.fn(), addMarker: vi.fn() })
  },
}))

const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/admin/drafts', component: { template: '<i/>' } }] })
const mountView = () => mount(ParcoursView, { global: { plugins: [router] } })

const definition: api.ParcoursDefinition = {
  statuses: [{ code: 'OUVERT', label: 'Ouvert', initial: true, outcome: 'NONE' }, { code: 'FIN', label: 'Fin', initial: false, outcome: 'COMPLETED' }],
  allowedDossierStatuses: ['OUVERT'],
  onStartDossierStatus: null,
  steps: [{ id: 'saisie', kind: 'QUESTION', params: { schemaKey: 'avis' }, next: null, branches: [], defaultNext: null }],
}

const permis: DossierType = {
  id: '1', tenantId: 't', code: 'PERMIS', label: 'Permis riverain', workflowKey: 'permisRiverain', active: true, allowUnknownFields: false,
  fieldDefinitions: [
    { name: 'adresse', label: 'Adresse', type: 'STRING', required: true },
    { name: 'montant', label: 'Montant', type: 'NUMBER', required: false },
    { name: 'iban', label: 'IBAN', type: 'STRING', required: false, sensitive: true },
    { name: 'total', label: 'Total', type: 'NUMBER', required: false, formula: 'dossier.fields.montant * 2' },
  ],
}

describe('ParcoursView (LC5)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listParcours).mockResolvedValue([])
    vi.mocked(resolver.resolveValueSource).mockResolvedValue([{ value: 'avis', label: 'Avis' }])
    vi.mocked(dossierTypes.listAllDossierTypes).mockResolvedValue([permis])
    vi.mocked(schemas.listTaskQuestionSchemas).mockResolvedValue({
      content: [{ id: 's', schemaKey: 'avis', label: 'Avis', createdBy: 'u', updatedBy: 'u',
        fieldDefinitions: [{ name: 'decision', label: 'Décision', type: 'STRING', required: true }] }],
      page: 0, size: 100, totalElements: 1, totalPages: 1,
    })
    vi.mocked(expressions.validateExpression).mockResolvedValue({ valid: true })
    importXML.mockResolvedValue(undefined)
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

  it('deletes with the version it read, so a concurrent edit is reported instead of lost', async () => {
    vi.mocked(api.getParcours).mockResolvedValue({ key: 'p', label: 'P', version: 4, definition, updatedAt: 't', updatedBy: 'u' })
    vi.mocked(api.listParcours).mockResolvedValue([{ key: 'p', label: 'P', version: 4, steps: 1, updatedAt: 't', updatedBy: 'u' }])
    vi.mocked(api.deleteParcours).mockResolvedValue()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="parcours-open-p"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="parcours-delete"]').trigger('click')
    await flushPromises()
    expect(api.deleteParcours).toHaveBeenCalledWith('p', 4)
  })

  it('does not carry the replace-draft choice or a stale result into another parcours', async () => {
    vi.mocked(api.getParcours).mockResolvedValue({ key: 'p', label: 'P', version: 1, definition, updatedAt: 't', updatedBy: 'u' })
    vi.mocked(api.listParcours).mockResolvedValue([{ key: 'p', label: 'P', version: 1, steps: 1, updatedAt: 't', updatedBy: 'u' }])
    vi.mocked(api.previewParcours).mockResolvedValue({ xml: '<x/>', sha256: 'f'.repeat(64), warnings: [], draftWritten: false, replacedExistingDraft: false })
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="parcours-open-p"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="parcours-replace"]').setValue(true)
    await wrapper.get('[data-testid="parcours-preview"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="parcours-result"]').exists()).toBe(true)
    await wrapper.get('[data-testid="parcours-label"]').setValue('Modifié')
    expect(wrapper.find('[data-testid="parcours-result"]').exists()).toBe(false)
    await wrapper.findAll('button').find((b) => b.text() === 'Fermer')!.trigger('click')
    await wrapper.get('[data-testid="parcours-open-p"]').trigger('click')
    await flushPromises()
    expect((wrapper.get('[data-testid="parcours-replace"]').element as HTMLInputElement).checked).toBe(false)
  })

  it('draws the previewed BPMN as a diagram, the XML staying available as text', async () => {
    vi.mocked(api.getParcours).mockResolvedValue({ key: 'p', label: 'P', version: 1, definition, updatedAt: 't', updatedBy: 'u' })
    vi.mocked(api.listParcours).mockResolvedValue([{ key: 'p', label: 'P', version: 1, steps: 1, updatedAt: 't', updatedBy: 'u' }])
    vi.mocked(api.previewParcours).mockResolvedValue({ xml: '<definitions id="d"/>', sha256: 'c'.repeat(64), warnings: [], draftWritten: false, replacedExistingDraft: false })
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="parcours-open-p"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="parcours-preview"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="parcours-diagram"]').exists()).toBe(true)
    expect(importXML).toHaveBeenCalledWith('<definitions id="d"/>')
    expect(wrapper.get('[data-testid="parcours-xml"]').text()).toContain('<definitions id="d"/>')
  })

  it('works on a chosen dossier type: saved with the parcours, key suggested, mismatch with the type workflow reported', async () => {
    vi.mocked(api.saveParcours).mockResolvedValue({ key: 'permisRiverain', label: 'P', version: 1, definition, updatedAt: 't', updatedBy: 'u' })
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="parcours-new"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="parcours-dossier-type"]').setValue('PERMIS')
    expect((wrapper.get('[data-testid="parcours-key"]').element as HTMLInputElement).value).toBe('permisRiverain')
    expect(wrapper.find('[data-testid="parcours-type-mismatch"]').exists()).toBe(false)
    await wrapper.get('[data-testid="parcours-key"]').setValue('autreCle')
    expect(wrapper.get('[data-testid="parcours-type-mismatch"]').text()).toContain('permisRiverain')
    await wrapper.get('[data-testid="parcours-label"]').setValue('P')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()
    expect(vi.mocked(api.saveParcours).mock.calls[0][2].dossierTypeCode).toBe('PERMIS')
  })

  it('edits field mappings as rows: targets come from the dossier type (no computed field), values can insert its fields', async () => {
    vi.mocked(api.saveParcours).mockResolvedValue({ key: 'p1', label: 'P', version: 1, definition, updatedAt: 't', updatedBy: 'u' })
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="parcours-new"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="parcours-key"]').setValue('p1')
    await wrapper.get('[data-testid="parcours-label"]').setValue('P')
    await wrapper.get('[data-testid="parcours-dossier-type"]').setValue('PERMIS')
    await wrapper.get('[data-testid="step-add"]').trigger('click')
    await wrapper.get('[data-testid="step-kind"]').setValue('MAP_FIELDS')
    const targets = wrapper.get('[data-testid="mapping-field"]').findAll('option').map((o) => o.element.value)
    expect(targets).toEqual(['', 'adresse', 'montant', 'iban'])
    await wrapper.get('[data-testid="mapping-field"]').setValue('adresse')
    expect(wrapper.get('[data-testid="mapping-warning"]').text()).toContain('pas de valeur')
    const insert = wrapper.get('[data-testid="mapping-expression-insert"]')
    const offered = insert.findAll('option').map((o) => o.element.value)
    expect(offered).toContain('dossier.fields.montant')
    expect(offered).not.toContain('dossier.fields.iban')
    await insert.setValue('dossier.fields.montant')
    await flushPromises()
    expect(wrapper.find('[data-testid="mapping-warning"]').exists()).toBe(false)
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()
    expect(vi.mocked(api.saveParcours).mock.calls[0][2].steps[0].params).toEqual({ fieldMapping: 'adresse = dossier.fields.montant' })
  })

  it('reads an existing text mapping into rows and flags a field the dossier type does not declare', async () => {
    const mapped: api.ParcoursDefinition = {
      ...definition,
      dossierTypeCode: 'PERMIS',
      steps: [{ id: 'maj', kind: 'MAP_FIELDS', params: { fieldMapping: 'adresse = "x"\ninconnu = 1' }, next: null, branches: [], defaultNext: null }],
    }
    vi.mocked(api.getParcours).mockResolvedValue({ key: 'p', label: 'P', version: 1, definition: mapped, updatedAt: 't', updatedBy: 'u' })
    vi.mocked(api.listParcours).mockResolvedValue([{ key: 'p', label: 'P', version: 1, steps: 1, updatedAt: 't', updatedBy: 'u' }])
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="parcours-open-p"]').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('[data-testid="mapping-row"]')).toHaveLength(2)
    expect(wrapper.get('[data-testid="mapping-warning"]').text()).toContain('inconnu')
    await wrapper.get('[data-testid="mapping-raw-toggle"]').setValue(true)
    expect((wrapper.get('[data-testid="mapping-raw"]').element as HTMLTextAreaElement).value).toBe('adresse = "x"\ninconnu = 1')
  })

  it('validates a formula while typing and offers the variables of the parcours to formulas and decisions', async () => {
    vi.useFakeTimers()
    try {
      vi.mocked(expressions.validateExpression).mockResolvedValue({ valid: false, message: 'unexpected token', position: 4 })
      const wrapper = mountView()
      await flushPromises()
      await wrapper.get('[data-testid="parcours-new"]').trigger('click')
      await flushPromises()
      await wrapper.get('[data-testid="step-add"]').trigger('click')
      await wrapper.get('[data-testid="param-schemaKey"]').setValue('avis')
      await wrapper.get('[data-testid="step-add"]').trigger('click')
      await wrapper.findAll('[data-testid="step-kind"]')[1].setValue('EXPRESSION')
      await wrapper.get('[data-testid="param-formula"]').setValue('1 +')
      await vi.advanceTimersByTimeAsync(500)
      await flushPromises()
      expect(expressions.validateExpression).toHaveBeenCalledWith('1 +')
      expect(wrapper.get('[data-testid="param-formula-status"]').text()).toContain('unexpected token')
      expect(wrapper.get('[data-testid="param-formula-insert"]').findAll('option').map((o) => o.element.value)).toContain('variables.decision')
      await wrapper.get('[data-testid="param-formulaResult"]').setValue('score')
      expect(wrapper.findAll('#parcours-variables option').map((o) => o.element.value)).toEqual(['decision', 'score'])
    } finally {
      vi.useRealTimers()
    }
  })
})
