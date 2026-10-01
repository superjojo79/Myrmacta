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
})
