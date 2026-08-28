import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import FieldDefinitionDefsEditor from './FieldDefinitionDefsEditor.vue'
import type { FieldDefinition } from '@/api/types'

describe('FieldDefinitionDefsEditor', () => {
  function mountEditor(modelValue: FieldDefinition[] = []) {
    return mount(FieldDefinitionDefsEditor, {
      props: { modelValue },
    })
  }

  it('renders an empty state with an add button', () => {
    const wrapper = mountEditor()
    expect(wrapper.find('[data-testid="field-def-add"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid="field-def-row"]')).toHaveLength(0)
  })

  it('adding a row appends an editable STRING field by default', async () => {
    const wrapper = mountEditor()
    await wrapper.get('[data-testid="field-def-add"]').trigger('click')

    expect(wrapper.emitted('update:modelValue')?.[0]?.[0]).toEqual([
      { name: '', label: '', type: 'STRING', required: false, defaultValue: '', options: [], group: '', sensitive: false },
    ])
  })

  it('removing a row removes it from the emitted list', async () => {
    const definitions: FieldDefinition[] = [
      { name: 'priority', label: 'Priority', type: 'STRING', required: false },
      { name: 'amount', label: 'Amount', type: 'NUMBER', required: false },
    ]
    const wrapper = mountEditor(definitions)

    await wrapper.findAll('[data-testid="field-def-remove"]')[0]!.trigger('click')

    const emitted = wrapper.emitted('update:modelValue')?.[0]?.[0] as FieldDefinition[]
    expect(emitted).toHaveLength(1)
    expect(emitted[0]!.name).toBe('amount')
  })

  it('editing label, defaultValue and group updates the emitted row', async () => {
    const definitions: FieldDefinition[] = [
      { name: 'priority', label: 'Priority', type: 'STRING', required: false },
    ]
    const wrapper = mountEditor(definitions)

    await wrapper.get('[data-testid="field-def-label"]').setValue('Urgency level')
    await wrapper.get('[data-testid="field-def-default"]').setValue('NORMAL')
    await wrapper.get('[data-testid="field-def-group"]').setValue('Details')

    const emitted = wrapper.emitted('update:modelValue')?.slice(-1)[0]?.[0] as FieldDefinition[]
    expect(emitted[0]).toMatchObject({ label: 'Urgency level', defaultValue: 'NORMAL', group: 'Details' })
  })

  it('editing the SELECT options field parses a comma-separated list', async () => {
    const definitions: FieldDefinition[] = [
      { name: 'status', label: 'Status', type: 'SELECT', required: false, options: [] },
    ]
    const wrapper = mountEditor(definitions)

    await wrapper.get('[data-testid="field-def-options-input"]').setValue('A, B ,C')

    const emitted = wrapper.emitted('update:modelValue')?.slice(-1)[0]?.[0] as FieldDefinition[]
    expect(emitted[0]!.options).toEqual(['A', 'B', 'C'])
  })

  it('toggling required updates the emitted flag', async () => {
    const definitions: FieldDefinition[] = [
      { name: 'priority', label: 'Priority', type: 'STRING', required: false },
    ]
    const wrapper = mountEditor(definitions)

    await wrapper.get('[data-testid="field-def-required"]').setValue(true)

    const emitted = wrapper.emitted('update:modelValue')?.[0]?.[0] as FieldDefinition[]
    expect(emitted[0]!.required).toBe(true)
  })

  it('moving a row up changes the emitted order', async () => {
    const definitions: FieldDefinition[] = [
      { name: 'priority', label: 'Priority', type: 'STRING', required: false },
      { name: 'amount', label: 'Amount', type: 'NUMBER', required: false },
    ]
    const wrapper = mountEditor(definitions)

    await wrapper.findAll('[data-testid="field-def-move-up"]')[1]!.trigger('click')

    const emitted = wrapper.emitted('update:modelValue')?.[0]?.[0] as FieldDefinition[]
    expect(emitted.map((d) => d.name)).toEqual(['amount', 'priority'])
  })

  it('the last row cannot move further down (button disabled)', () => {
    const definitions: FieldDefinition[] = [
      { name: 'priority', label: 'Priority', type: 'STRING', required: false },
    ]
    const wrapper = mountEditor(definitions)

    const downButton = wrapper.find('[data-testid="field-def-move-down"]')
    expect(downButton.attributes('disabled')).toBeDefined()
  })

  it('re-syncs local rows when the modelValue prop changes externally', async () => {
    const wrapper = mountEditor([{ name: 'priority', label: 'Priority', type: 'STRING', required: false }])

    await wrapper.setProps({
      modelValue: [{ name: 'amount', label: 'Amount', type: 'NUMBER', required: false }],
    })

    expect(wrapper.get('[data-testid="field-def-name"]').element.value).toBe('amount')
  })

  it('moving a row down changes the emitted order', async () => {
    const definitions: FieldDefinition[] = [
      { name: 'priority', label: 'Priority', type: 'STRING', required: false },
      { name: 'amount', label: 'Amount', type: 'NUMBER', required: false },
    ]
    const wrapper = mountEditor(definitions)

    await wrapper.findAll('[data-testid="field-def-move-down"]')[0]!.trigger('click')

    const emitted = wrapper.emitted('update:modelValue')?.[0]?.[0] as FieldDefinition[]
    expect(emitted.map((d) => d.name)).toEqual(['amount', 'priority'])
  })

  it('moving the first row up is a no-op (button disabled)', () => {
    const definitions: FieldDefinition[] = [
      { name: 'priority', label: 'Priority', type: 'STRING', required: false },
    ]
    const wrapper = mountEditor(definitions)

    const upButton = wrapper.find('[data-testid="field-def-move-up"]')
    expect(upButton.attributes('disabled')).toBeDefined()
  })

  it('selecting SELECT type reveals the options editor', async () => {
    const definitions: FieldDefinition[] = [
      { name: 'status', label: 'Status', type: 'STRING', required: false },
    ]
    const wrapper = mountEditor(definitions)

    expect(wrapper.find('[data-testid="field-def-options"]').exists()).toBe(false)
    await wrapper.get('[data-testid="field-def-type"]').setValue('SELECT')
    expect(wrapper.find('[data-testid="field-def-options"]').exists()).toBe(true)
  })

  it('toggling sensitive updates the emitted flag', async () => {
    const definitions: FieldDefinition[] = [
      { name: 'ssn', label: 'SSN', type: 'STRING', required: false, sensitive: false },
    ]
    const wrapper = mountEditor(definitions)

    await wrapper.get('[data-testid="field-def-sensitive"]').setValue(true)

    const emitted = wrapper.emitted('update:modelValue')?.[0]?.[0] as FieldDefinition[]
    expect(emitted[0]!.sensitive).toBe(true)
  })

  it('editing a field name emits the full list, not a partial diff', async () => {
    const definitions: FieldDefinition[] = [
      { name: 'priority', label: 'Priority', type: 'STRING', required: false },
      { name: 'amount', label: 'Amount', type: 'NUMBER', required: false },
    ]
    const wrapper = mountEditor(definitions)

    await wrapper.findAll('[data-testid="field-def-name"]')[0]!.setValue('urgency')

    const emitted = wrapper.emitted('update:modelValue')?.[0]?.[0] as FieldDefinition[]
    expect(emitted).toHaveLength(2)
    expect(emitted[0]!.name).toBe('urgency')
    expect(emitted[1]!.name).toBe('amount')
  })
})
