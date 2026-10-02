import { describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import FormDesigner from './FormDesigner.vue'
import type { FieldDefinition } from '@/api/types'

const defs: FieldDefinition[] = [
  { name: 'nom', label: 'Nom', type: 'STRING', required: true, group: 'Identité', helpText: 'Nom de famille', width: 'HALF' },
  { name: 'prenom', label: 'Prénom', type: 'STRING', required: false, group: 'Identité', width: 'HALF' },
  { name: 'notes', label: 'Notes', type: 'STRING', required: false, group: 'Suivi' },
]

function mountDesigner(modelValue: FieldDefinition[] = defs) {
  setActivePinia(createPinia())
  return mount(FormDesigner, { props: { modelValue } })
}

describe('FormDesigner (LC4a)', () => {
  it('previews the form with the real input engine: sections, half width, help text', () => {
    const wrapper = mountDesigner()
    const preview = wrapper.get('[data-testid="form-designer-preview"]')
    expect(preview.findAll('h3').map((h) => h.text())).toEqual(['Identité', 'Suivi'])
    expect(preview.findAll('.dynamic-fields__field--half')).toHaveLength(2)
    expect(preview.get('[data-testid="help-nom"]').text()).toBe('Nom de famille')
  })

  it('does not preview a field that has no name yet, and says so when nothing is previewable', () => {
    const wrapper = mountDesigner([{ name: '', label: '', type: 'STRING', required: false }])
    expect(wrapper.get('[data-testid="form-designer-preview"]').text()).toContain('Ajoutez un champ')
  })

  it('the preview can be hidden', async () => {
    const wrapper = mountDesigner()
    await wrapper.get('[data-testid="form-designer-preview-toggle"]').setValue(false)
    expect(wrapper.find('[data-testid="form-designer-preview"]').exists()).toBe(false)
  })

  it('editing the help text and the width goes through the editor as plain fieldDefinitions', async () => {
    const wrapper = mountDesigner()
    await flushPromises()
    const rows = wrapper.findAll('[data-testid="field-def-row"]')
    await rows[2]!.get('[data-testid="field-def-help"]').setValue('Texte libre')
    await rows[2]!.get('[data-testid="field-def-width"]').setValue('HALF')
    const emitted = wrapper.emitted('update:modelValue')!.at(-1)![0] as FieldDefinition[]
    expect(emitted[2]).toMatchObject({ name: 'notes', helpText: 'Texte libre', width: 'HALF' })
    // Pleine largeur = valeur par défaut : rien n'est stocké.
    await rows[2]!.get('[data-testid="field-def-width"]').setValue('FULL')
    const back = wrapper.emitted('update:modelValue')!.at(-1)![0] as FieldDefinition[]
    expect(back[2]!.width ?? null).toBeNull()
  })

  it('reorders by drag and drop', async () => {
    const wrapper = mountDesigner()
    const rows = wrapper.findAll('[data-testid="field-def-row"]')
    const data: Record<string, string> = {}
    const dataTransfer = { setData: (k: string, v: string) => (data[k] = v), effectAllowed: '' }
    await rows[0]!.get('[data-testid="field-def-handle"]').trigger('dragstart', { dataTransfer })
    await rows[2]!.trigger('drop', { dataTransfer })
    const emitted = wrapper.emitted('update:modelValue')!.at(-1)![0] as FieldDefinition[]
    expect(emitted.map((d) => d.name)).toEqual(['prenom', 'notes', 'nom'])
  })

  it('offers the sections already in use when typing a section', () => {
    const wrapper = mountDesigner()
    const options = wrapper.findAll('datalist option').map((o) => o.attributes('value'))
    expect(options).toEqual(['Identité', 'Suivi'])
  })

  // ---- Revue adversariale LC4a ----
  it('does not offer a help text (nor width) for the columns of a repeatable group (Medium)', () => {
    const wrapper = mountDesigner([
      { name: 'g', label: 'G', type: 'REPEATABLE_GROUP', required: false, fields: [{ name: 'c', label: 'C', type: 'STRING', required: false }] },
    ])
    const sub = wrapper.get('[data-testid="field-def-subfields"]')
    expect(sub.find('[data-testid="field-def-help"]').exists()).toBe(false)
    expect(sub.find('[data-testid="field-def-width"]').exists()).toBe(false)
    // le groupe lui-même : une aide, mais jamais de demi-largeur
    expect(wrapper.find('[data-testid="field-def-width"]').exists()).toBe(false)
  })

  it('changing a field to a repeatable group resets its width', async () => {
    const wrapper = mountDesigner([{ name: 'a', label: 'A', type: 'STRING', required: false, width: 'HALF' }])
    await wrapper.get('[data-testid="field-def-type"]').setValue('REPEATABLE_GROUP')
    const emitted = wrapper.emitted('update:modelValue')!.at(-1)![0] as FieldDefinition[]
    expect(emitted[0]!.width ?? null).toBeNull()
  })

  it('shows no input hint when the form is read-only, and merges sections that differ by spaces only', async () => {
    const { default: DynamicFields } = await import('./DynamicFields.vue')
    setActivePinia(createPinia())
    const readonly = mount(DynamicFields, { props: { definitions: defs, modelValue: {}, readonly: true } })
    expect(readonly.find('[data-testid="help-nom"]').exists()).toBe(false)
    const merged = mount(DynamicFields, {
      props: {
        definitions: [
          { name: 'a', label: 'A', type: 'STRING', required: false, group: 'Identité' },
          { name: 'b', label: 'B', type: 'STRING', required: false, group: 'Identité ' },
        ],
        modelValue: {},
      },
    })
    expect(merged.findAll('h3').map((h) => h.text())).toEqual(['Identité'])
  })
})
