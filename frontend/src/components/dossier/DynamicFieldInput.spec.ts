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

describe('DynamicFieldInput — structured types (TODO §76, D1)', () => {
  const lastEmit = (w: ReturnType<typeof mount>) => w.emitted('update:modelValue')!.at(-1)![0]

  it('edits a money amount and picks a currency among the allowed ones', async () => {
    const wrapper = mount(DynamicFieldInput, {
      props: { field: { name: 'm', label: 'Montant', type: 'MONEY', required: false, options: ['EUR', 'USD'] }, modelValue: undefined },
    })
    await wrapper.get('input[aria-label="Montant — montant"]').setValue('12.5')
    expect(lastEmit(wrapper)).toEqual({ amount: '12.5', currency: '' })
    expect(wrapper.findAll('select option').map((o) => o.text())).toEqual(['—', 'EUR', 'USD'])
    await wrapper.get('select[aria-label="Montant — devise"]').setValue('USD')
    expect(lastEmit(wrapper)).toEqual({ amount: '', currency: 'USD' })
  })

  it('a single allowed currency is filled in automatically', async () => {
    const wrapper = mount(DynamicFieldInput, {
      props: { field: { name: 'm', label: 'M', type: 'MONEY', required: false, options: ['EUR'] }, modelValue: undefined },
    })
    await wrapper.get('input[aria-label="M — montant"]').setValue('5')
    expect(lastEmit(wrapper)).toEqual({ amount: '5', currency: 'EUR' })
  })

  it('without a currency list, the currency is a 3-letter upper-case input', async () => {
    const wrapper = mount(DynamicFieldInput, {
      props: { field: { name: 'm', label: 'M', type: 'MONEY', required: false }, modelValue: { amount: '1', currency: '' } },
    })
    expect(wrapper.find('select').exists()).toBe(false)
    await wrapper.get('input[aria-label="M — devise"]').setValue('eur')
    expect(lastEmit(wrapper)).toEqual({ amount: '1', currency: 'EUR' })
  })

  it('edits an external reference (system, id, label)', async () => {
    const wrapper = mount(DynamicFieldInput, {
      props: { field: { name: 'r', label: 'Fournisseur', type: 'EXTERNAL_REF', required: false, options: ['d365fo', 'sap'] }, modelValue: { system: 'sap', id: '7', label: 'X' } },
    })
    await wrapper.get('input[aria-label="Fournisseur — identifiant"]').setValue('8')
    expect(lastEmit(wrapper)).toEqual({ system: 'sap', id: '8', label: 'X' })
    await wrapper.get('select[aria-label="Fournisseur — système"]').setValue('d365fo')
    expect(lastEmit(wrapper)).toEqual({ system: 'd365fo', id: '7', label: 'X' })
    await wrapper.get('input[aria-label="Fournisseur — libellé"]').setValue('Contoso')
    expect(lastEmit(wrapper)).toEqual({ system: 'sap', id: '7', label: 'Contoso' })
  })

  const group = {
    name: 'g',
    label: 'Lignes',
    type: 'REPEATABLE_GROUP' as const,
    required: false,
    minRows: 1,
    maxRows: 2,
    fields: [
      { name: 'poste', label: 'Poste', type: 'STRING' as const, required: true },
      { name: 'qte', label: 'Qté', type: 'NUMBER' as const, required: false },
    ],
  }

  it('adds, edits and removes rows of a group within min/max bounds', async () => {
    const wrapper = mount(DynamicFieldInput, { props: { field: group, modelValue: [{ poste: 'A' }] } })
    expect(wrapper.findAll('[data-testid="group-row"]')).toHaveLength(1)
    expect((wrapper.get('button[aria-label="Supprimer la ligne 1"]').element as HTMLButtonElement).disabled).toBe(true) // min 1

    await wrapper.get('[data-testid="group-add-row"]').trigger('click')
    expect(lastEmit(wrapper)).toEqual([{ poste: 'A' }, {}])

    await wrapper.get('input[aria-label="Poste (ligne 1)"]').setValue('Toiture')
    expect(lastEmit(wrapper)).toEqual([{ poste: 'Toiture' }])
  })

  it('stops adding rows at maxRows and removes a row', async () => {
    const wrapper = mount(DynamicFieldInput, { props: { field: group, modelValue: [{ poste: 'A' }, { poste: 'B' }] } })
    expect((wrapper.get('[data-testid="group-add-row"]').element as HTMLButtonElement).disabled).toBe(true)
    expect(wrapper.text()).toContain('Maximum 2 lignes')
    await wrapper.get('button[aria-label="Supprimer la ligne 1"]').trigger('click')
    expect(lastEmit(wrapper)).toEqual([{ poste: 'B' }])
  })

  it('renders an empty group with just the add button', () => {
    const wrapper = mount(DynamicFieldInput, { props: { field: group, modelValue: undefined } })
    expect(wrapper.find('table').exists()).toBe(false)
    expect(wrapper.find('[data-testid="group-add-row"]').exists()).toBe(true)
  })
})
