import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import DynamicFields from './DynamicFields.vue'
import type { FieldDefinition } from '@/api/types'
import { useAuthStore } from '@/stores/auth'

const defs: FieldDefinition[] = [
  {
    name: 'priority',
    label: 'Priority',
    type: 'STRING',
    required: true,
    defaultValue: 'NORMAL',
    options: [],
    group: 'details',
    sensitive: false,
  },
  {
    name: 'ssn',
    label: 'SSN',
    type: 'STRING',
    required: false,
    sensitive: true,
  },
]

describe('DynamicFields', () => {
  it('applies defaults and emits model updates', async () => {
    setActivePinia(createPinia())
    const wrapper = mount(DynamicFields, {
      props: {
        definitions: defs,
        modelValue: {},
      },
    })
    expect(wrapper.emitted('update:modelValue')?.[0]?.[0]).toMatchObject({ priority: 'NORMAL' })
    const input = wrapper.find('input[type="text"]')
    await input.setValue('HIGH')
    const last = wrapper.emitted('update:modelValue')?.at(-1)?.[0] as Record<string, unknown>
    expect(last.priority).toBe('HIGH')
  })

  it('does not apply defaults when applyDefaults is false', () => {
    setActivePinia(createPinia())
    const wrapper = mount(DynamicFields, {
      props: {
        definitions: defs,
        modelValue: {},
        applyDefaults: false,
      },
    })
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })
})

describe('DynamicFields — structured fields (TODO §76, D1)', () => {
  const structured: FieldDefinition[] = [
    { name: 'note', label: 'Note', type: 'STRING', required: false },
    { name: 'montant', label: 'Montant', type: 'MONEY', required: false, options: ['EUR'] },
    {
      name: 'lignes',
      label: 'Lignes',
      type: 'REPEATABLE_GROUP',
      required: false,
      fields: [
        { name: 'poste', label: 'Poste', type: 'STRING', required: false },
        { name: 'ok', label: 'Ok', type: 'BOOLEAN', required: false },
      ],
    },
    {
      name: 'budget',
      label: 'Budget',
      type: 'REPEATABLE_GROUP',
      required: false,
      sensitive: true,
      fields: [{ name: 'poste', label: 'Poste', type: 'STRING', required: false }],
    },
  ]
  const values = {
    note: 'n',
    montant: { amount: '12.50', currency: 'EUR' },
    lignes: [{ poste: 'Toiture', ok: true }],
    budget: [{ poste: 'CONFIDENTIEL' }],
  }

  it('wraps structured fields in a group, not a <label> (a click must not activate the first control)', async () => {
    setActivePinia(createPinia())
    const wrapper = mount(DynamicFields, { props: { definitions: structured, modelValue: {}, applyDefaults: false } })
    const wrappers = wrapper.findAll('.dynamic-fields__field')
    expect(wrappers.map((w) => w.element.tagName)).toEqual(['LABEL', 'DIV', 'DIV', 'DIV'])
    expect(wrappers[2]!.attributes('role')).toBe('group')
    expect(wrappers[2]!.attributes('aria-label')).toBe('Lignes')
    // Un clic sur le libellé du groupe n'ajoute pas de ligne.
    await wrappers[2]!.find('span').trigger('click')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('reads structured values as text and as a table, never as [object Object]', () => {
    setActivePinia(createPinia())
    const auth = useAuthStore()
    auth.user = { id: 'u1', name: 'Ada', email: null, roles: ['DOSSIER_MANAGER'] }
    const wrapper = mount(DynamicFields, { props: { definitions: structured, modelValue: values, readonly: true } })
    expect(wrapper.text()).toContain('12.50 EUR')
    expect(wrapper.find('table').text()).toContain('Toiture')
    expect(wrapper.text()).not.toContain('[object Object]')
  })

  it('hides the rows of a sensitive group from a non-admin and shows them to a tenant admin', () => {
    setActivePinia(createPinia())
    const auth = useAuthStore()
    auth.user = { id: 'u1', name: 'Ada', email: null, roles: ['DOSSIER_MANAGER'] }
    const hidden = mount(DynamicFields, { props: { definitions: structured, modelValue: values, readonly: true } })
    expect(hidden.html()).not.toContain('CONFIDENTIEL')
    expect(hidden.text()).toContain('••••')

    auth.user = { id: 'u2', name: 'Admin', email: null, roles: ['TENANT_ADMIN'] }
    const revealed = mount(DynamicFields, { props: { definitions: structured, modelValue: values, readonly: true } })
    expect(revealed.html()).toContain('CONFIDENTIEL')
  })
})
