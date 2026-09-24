import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import DynamicFieldInput from './DynamicFieldInput.vue'

describe('DynamicFieldInput (TODO §68, extrait de DynamicFields)', () => {
  it('renders a select with an empty option and emits the chosen value', async () => {
    const wrapper = mount(DynamicFieldInput, {
      props: { field: { name: 'c', label: 'C', type: 'SELECT', required: false, options: ['A', 'B'] }, modelValue: null },
    })
    expect(wrapper.findAll('option').map((o) => o.text())).toEqual(['—', 'A', 'B'])
    await wrapper.find('select').setValue('B')
    expect(wrapper.emitted('update:modelValue')!.at(-1)).toEqual(['B'])
  })

  it('uses number and date inputs for NUMBER and DATE, text otherwise', () => {
    const type = (t: 'NUMBER' | 'DATE' | 'STRING') =>
      mount(DynamicFieldInput, { props: { field: { name: 'x', label: 'X', type: t, required: false }, modelValue: 3 } })
        .find('input').attributes('type')
    expect(type('NUMBER')).toBe('number')
    expect(type('DATE')).toBe('date')
    expect(type('STRING')).toBe('text')
  })

  // La règle ESLint ne couvre que portal/** : ce test garde la dépendance transitive du portail.
  it('never imports the authentication store (usable by the public portal)', async () => {
    const source = await import('./DynamicFieldInput.vue?raw').then((m) => m.default as string)
    const imports = source.split('\n').filter((line) => /^\s*import\s/.test(line))
    expect(imports.length).toBeGreaterThan(0)
    expect(imports.join('\n')).not.toMatch(/stores\/auth|SensitiveValue|axios|pinia|vue-router/)
  })
})
