import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import DossierFieldsEditor from './DossierFieldsEditor.vue'
import type { FieldDefinition } from '@/api/types'

const definitions: FieldDefinition[] = [
  { name: 'priority', label: 'Priority', type: 'STRING', required: true },
]

describe('DossierFieldsEditor', () => {
  function mountEditor(
    overrides: Partial<{
      canEdit: boolean
      locked: boolean
      fields: Record<string, unknown>
    }> = {},
  ) {
    setActivePinia(createPinia())
    return mount(DossierFieldsEditor, {
      props: {
        definitions,
        fields: overrides.fields ?? { priority: 'NORMAL' },
        canEdit: overrides.canEdit ?? true,
        locked: overrides.locked ?? false,
      },
    })
  }

  it('hides the save button and stays readonly for viewers', () => {
    const wrapper = mountEditor({ canEdit: false })
    expect(wrapper.text()).not.toContain('Enregistrer les champs')
    expect(wrapper.find('input').exists()).toBe(false)
  })

  it('hides the save button when the dossier is locked', () => {
    const wrapper = mountEditor({ locked: true })
    expect(wrapper.text()).not.toContain('Enregistrer les champs')
    expect(wrapper.find('input').exists()).toBe(false)
  })

  it('emits only the changed field on save', async () => {
    const wrapper = mountEditor()
    await wrapper.get('input').setValue('HIGH')
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('save')?.[0]).toEqual([{ priority: 'HIGH' }])
  })

  it('does not emit save when the diff is empty', async () => {
    const wrapper = mountEditor()
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('save')).toBeUndefined()
  })
})
