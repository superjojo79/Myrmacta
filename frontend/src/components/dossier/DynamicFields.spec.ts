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

  it('shows a translated UNIQUE error under the field and the server message for another code', () => {
    setActivePinia(createPinia())
    const wrapper = mount(DynamicFields, {
      props: {
        definitions: [{ name: 'plaque', label: 'Plaque', type: 'STRING', required: false }],
        modelValue: { plaque: 'AB-123-CD' },
        applyDefaults: false,
        serverErrors: [
          {
            field: 'plaque',
            code: 'UNIQUE',
            message: 'Value already used by dossier DOS-9 (value AB-123-CD)',
            reference: 'DOS-9',
          },
          { field: 'plaque', code: 'REQUIRED', message: 'Field is required', reference: 'DOS-HIDDEN' },
        ],
      },
    })
    const alert = wrapper.get('[data-testid="field-error"]')
    expect(alert.attributes('role')).toBe('alert')
    expect(alert.text()).toContain('Déjà utilisé par le dossier DOS-9')
    expect(alert.text()).toContain('Field is required')
    expect(alert.text()).not.toContain('AB-123-CD')
    expect(alert.text()).not.toContain('Value already used')
    expect(alert.text()).not.toContain('DOS-HIDDEN')
    const input = wrapper.get('input')
    expect(input.attributes('aria-invalid')).toBe('true')
    expect(input.attributes('aria-describedby')).toBe(alert.attributes('id'))
    expect(alert.element.closest('label')).toBeNull()
  })

  it('shows a managedByWorkflow field as a read-only output with a distinct hint', () => {
    setActivePinia(createPinia())
    const wrapper = mount(DynamicFields, {
      props: {
        definitions: [
          { name: 'identifiantIam', label: 'IAM', type: 'STRING', required: false, managedByWorkflow: true },
          { name: 'nom', label: 'Nom', type: 'STRING', required: false },
        ],
        modelValue: { identifiantIam: 'iam-1', nom: 'Dupont' },
        applyDefaults: false,
      },
    })
    expect(wrapper.get('[data-testid="managed-identifiantIam"]').text()).toContain('iam-1')
    expect(wrapper.text()).toContain('géré par le workflow')
    expect(wrapper.findAll('input')).toHaveLength(1)
  })

  it('marks a computed output and structured fields invalid, with the error outside the label', () => {
    setActivePinia(createPinia())
    const unique = (field: string) => ({
      field,
      code: 'UNIQUE',
      message: 'Value already used by dossier DOS-9',
      reference: 'DOS-9',
    })
    const wrapper = mount(DynamicFields, {
      props: {
        definitions: [
          { name: 'plaqueNormalisee', label: 'Plaque normalisée', type: 'STRING', required: false, formula: 'dossier.fields.plaque' },
          { name: 'montant', label: 'Montant', type: 'MONEY', required: false, options: ['EUR'] },
          { name: 'lien', label: 'Lien', type: 'EXTERNAL_REF', required: false },
          {
            name: 'lignes',
            label: 'Lignes',
            type: 'REPEATABLE_GROUP',
            required: false,
            fields: [{ name: 'poste', label: 'Poste', type: 'STRING', required: false }],
          },
        ],
        modelValue: { plaqueNormalisee: 'AB123CD' },
        applyDefaults: false,
        serverErrors: [unique('plaqueNormalisee'), unique('montant'), unique('lien'), unique('lignes')],
      },
    })
    const output = wrapper.get('[data-testid="computed-plaqueNormalisee"]')
    const outputError = wrapper.get(`#${output.attributes('aria-describedby')}`)
    expect(output.attributes('aria-invalid')).toBe('true')
    expect(outputError.attributes('data-testid')).toBe('field-error')
    expect(outputError.element.closest('label')).toBeNull()

    const field = (label: string) => wrapper.findAll('.dynamic-fields__field').find((item) => item.text().includes(label))!
    for (const label of ['Montant', 'Lien']) {
      const controls = field(label).findAll('input, select')
      expect(controls.length).toBeGreaterThan(0)
      expect(controls.every((control) => control.attributes('aria-invalid') === 'true')).toBe(true)
      expect(controls.every((control) => control.attributes('aria-describedby'))).toBe(true)
      expect(wrapper.get(`#${controls[0]!.attributes('aria-describedby')}`).element.closest('label')).toBeNull()
    }
    const group = field('Lignes').get('.structured-input--group')
    expect(group.attributes('aria-invalid')).toBe('true')
    expect(wrapper.get(`#${group.attributes('aria-describedby')}`).element.closest('label')).toBeNull()
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
    expect(wrappers.map((w) => w.element.tagName)).toEqual(['DIV', 'DIV', 'DIV', 'DIV'])
    expect(wrappers[0]!.find('label').exists()).toBe(true)
    expect(wrappers[1]!.find('label').exists()).toBe(false)
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
