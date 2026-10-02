import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import DynamicFields from './DynamicFields.vue'
import type { FieldDefinition, FieldRulesPreview } from '@/api/types'
import { evaluateFieldRules } from '@/api/dossierTypes'

vi.mock('@/api/dossierTypes', () => ({ evaluateFieldRules: vi.fn() }))

const defs: FieldDefinition[] = [
  { name: 'pays', label: 'Pays', type: 'STRING', required: false },
  { name: 'ht', label: 'Montant HT', type: 'NUMBER', required: false },
  { name: 'ttc', label: 'Montant TTC', type: 'NUMBER', required: false, formula: 'dossier.fields.ht * 1.2' },
  { name: 'siret', label: 'SIRET', type: 'STRING', required: true, visibleWhen: "dossier.fields.pays = 'FR'" },
  { name: 'justificatif', label: 'Justificatif', type: 'STRING', required: false, requiredWhen: 'dossier.fields.ht > 1000' },
]
const preview = (over: Partial<FieldRulesPreview> = {}): FieldRulesPreview => ({ computed: {}, hidden: [], requiredByRule: [], errors: [], ...over })

function mountFields(props: Record<string, unknown> = {}) {
  return mount(DynamicFields, { props: { definitions: defs, modelValue: { pays: 'DE', siret: '123' }, typeCode: 'FACT', applyDefaults: false, ...props } })
}

async function settle() {
  await vi.advanceTimersByTimeAsync(350)
  await flushPromises()
}

describe('DynamicFields — règles du type (TODO §80)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useFakeTimers()
    vi.mocked(evaluateFieldRules).mockReset()
  })
  afterEach(() => vi.useRealTimers())

  it('hides a field that is not applicable, shows the server-computed value read-only and marks rule-required fields', async () => {
    vi.mocked(evaluateFieldRules).mockResolvedValue(preview({ computed: { ttc: 2400 }, hidden: ['siret'], requiredByRule: ['justificatif'] }))
    const wrapper = mountFields({ modelValue: { pays: 'DE', ht: 2000 } })
    await settle()

    expect(evaluateFieldRules).toHaveBeenCalledWith('FACT', { pays: 'DE', ht: 2000 })
    expect(wrapper.text()).not.toContain('SIRET')
    expect(wrapper.get('[data-testid="computed-ttc"]').text()).toBe('2400')
    expect(wrapper.text()).toContain('(calculé)')
    // le champ calculé n'a pas de saisie
    expect(wrapper.findAll('input').length).toBe(3) // pays, ht, justificatif
    const justification = wrapper.findAll('span').find((s) => s.text().startsWith('Justificatif'))!
    expect(justification.text()).toContain('*')
  })

  it('drops a typed value for a field that just became not applicable (the server refuses it)', async () => {
    vi.mocked(evaluateFieldRules).mockResolvedValue(preview({ hidden: ['siret'] }))
    const wrapper = mountFields({ modelValue: { pays: 'DE', siret: 'SAISI' } })
    await settle()

    const emitted = wrapper.emitted('update:modelValue')?.at(-1)?.[0] as Record<string, unknown>
    expect(emitted).toEqual({ pays: 'DE' })
  })

  it('debounces: one call for a burst of edits, and ignores a stale response', async () => {
    let resolveFirst: (v: FieldRulesPreview) => void = () => {}
    vi.mocked(evaluateFieldRules)
      .mockImplementationOnce(() => new Promise((resolve) => { resolveFirst = resolve }))
      .mockResolvedValueOnce(preview({ hidden: [] }))
    const wrapper = mountFields({ modelValue: { pays: 'FR' } })
    await vi.advanceTimersByTimeAsync(350) // 1er appel parti, réponse en attente
    await wrapper.setProps({ modelValue: { pays: 'DE' } })
    await settle() // 2e appel, réponse immédiate : siret visible
    resolveFirst(preview({ hidden: ['siret'] })) // réponse périmée du 1er appel
    await flushPromises()

    expect(evaluateFieldRules).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('SIRET') // la réponse périmée n'a pas masqué le champ
  })

  it('does nothing without a type code, for a read-only form, or for a type without rules', async () => {
    mountFields({ typeCode: undefined })
    mountFields({ readonly: true })
    mountFields({ definitions: defs.slice(0, 2) })
    await settle()
    expect(evaluateFieldRules).not.toHaveBeenCalled()
  })

  it('stays usable when the preview is unavailable (the server validates on save)', async () => {
    vi.mocked(evaluateFieldRules).mockRejectedValue(new Error('network'))
    const wrapper = mountFields()
    await settle()
    expect(wrapper.text()).toContain('SIRET')
  })

  it('a read-only form shows the stored values, computed fields included, and every stored field', async () => {
    const wrapper = mountFields({ readonly: true, modelValue: { pays: 'FR', ttc: 120, siret: '1' } })
    await settle()
    expect(wrapper.text()).toContain('Montant TTC')
    expect(wrapper.text()).toContain('120')
    expect(wrapper.text()).toContain('SIRET')
  })
})
