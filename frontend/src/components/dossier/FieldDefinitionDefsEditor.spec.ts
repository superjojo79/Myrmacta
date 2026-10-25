import { describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
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

describe('FieldDefinitionDefsEditor — structured types (TODO §76, D1)', () => {
  const base: FieldDefinition = { name: 'x', label: 'X', type: 'STRING', required: false, defaultValue: 'a', options: [] }
  const last = (w: ReturnType<typeof mount>) => w.emitted('update:modelValue')!.at(-1)![0] as FieldDefinition[]

  it('offers the structured types for dossier fields but not for task questions', () => {
    const fields = mount(FieldDefinitionDefsEditor, { props: { modelValue: [base] } })
    expect(fields.findAll('[data-testid="field-def-type"] option').map((o) => o.text())).toEqual(
      expect.arrayContaining(['MONEY', 'REPEATABLE_GROUP', 'EXTERNAL_REF']),
    )
    const task = mount(FieldDefinitionDefsEditor, { props: { modelValue: [base], mode: 'task' } })
    expect(task.findAll('[data-testid="field-def-type"] option').map((o) => o.text())).toEqual([
      'STRING', 'NUMBER', 'DATE', 'BOOLEAN', 'SELECT', 'FILE',
    ])
  })

  it('switching to MONEY clears the default value and options (the server refuses a default)', async () => {
    const wrapper = mount(FieldDefinitionDefsEditor, { props: { modelValue: [{ ...base, type: 'SELECT', options: ['A'] }] } })
    await wrapper.get('[data-testid="field-def-type"]').setValue('MONEY')
    expect(last(wrapper)[0]).toMatchObject({ type: 'MONEY', defaultValue: null, options: [], fields: [] })
    await nextTick()
    expect(wrapper.find('[data-testid="field-def-default"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="field-def-options"]').text()).toContain('Devises autorisées')
  })

  it('edits the allowed currencies of a MONEY field', async () => {
    const wrapper = mount(FieldDefinitionDefsEditor, { props: { modelValue: [{ ...base, type: 'MONEY', defaultValue: null }] } })
    await wrapper.get('[data-testid="field-def-options-input"]').setValue('EUR, USD')
    expect(last(wrapper)[0]!.options).toEqual(['EUR', 'USD'])
  })

  it('a group edits its row bounds and its own sub-fields in a nested editor', async () => {
    const group: FieldDefinition = { ...base, type: 'REPEATABLE_GROUP', defaultValue: null, fields: [], minRows: null, maxRows: null }
    const wrapper = mount(FieldDefinitionDefsEditor, { props: { modelValue: [group] } })
    await wrapper.get('[data-testid="field-def-min-rows"]').setValue('1')
    await wrapper.get('[data-testid="field-def-max-rows"]').setValue('10')
    expect(last(wrapper)[0]).toMatchObject({ minRows: 1, maxRows: 10 })

    const nested = wrapper.get('[data-testid="field-def-subfields"]')
    await nested.get('[data-testid="field-def-add"]').trigger('click')
    const fields = last(wrapper)[0]!.fields!
    expect(fields).toHaveLength(1)
    expect(fields[0]).toMatchObject({ type: 'STRING' })
  })

  it('sub-fields cannot be FILE or groups, nor sensitive, nor grouped', async () => {
    const group: FieldDefinition = { ...base, type: 'REPEATABLE_GROUP', defaultValue: null, fields: [{ ...base, name: 's' }] }
    const wrapper = mount(FieldDefinitionDefsEditor, { props: { modelValue: [group] } })
    const nested = wrapper.get('[data-testid="field-def-subfields"]')
    expect(nested.findAll('[data-testid="field-def-type"] option').map((o) => o.text())).toEqual([
      'STRING', 'NUMBER', 'DATE', 'BOOLEAN', 'SELECT', 'MONEY', 'EXTERNAL_REF',
    ])
    expect(nested.find('[data-testid="field-def-sensitive"]').exists()).toBe(false)
    expect(nested.find('[data-testid="field-def-default"]').exists()).toBe(false)
    expect(nested.find('[data-testid="field-def-group"]').exists()).toBe(false)
  })

  it('leaving a structured type resets its specific content', async () => {
    const group: FieldDefinition = { ...base, type: 'REPEATABLE_GROUP', defaultValue: null, fields: [base], minRows: 1, maxRows: 3 }
    const wrapper = mount(FieldDefinitionDefsEditor, { props: { modelValue: [group] } })
    await wrapper.get('[data-testid="field-def-type"]').setValue('STRING')
    expect(last(wrapper)[0]).toMatchObject({ type: 'STRING', fields: [], minRows: null, maxRows: null, defaultValue: '' })
  })

  describe('TODO §80 — règles', () => {
    const def: FieldDefinition = { name: 'ttc', label: 'TTC', type: 'NUMBER', required: false }

    it('offers the three rule inputs for a scalar field of a dossier type and emits them (blank = absent)', async () => {
      const wrapper = mount(FieldDefinitionDefsEditor, { props: { modelValue: [def] } })
      await wrapper.get('[data-testid="field-def-formula"]').setValue('dossier.fields.ht * 2')
      await wrapper.get('[data-testid="field-def-visible-when"]').setValue("dossier.fields.pays = 'FR'")
      await wrapper.get('[data-testid="field-def-required-when"]').setValue('dossier.fields.ht > 1000')
      const last = (wrapper.emitted('update:modelValue')?.at(-1)?.[0] as FieldDefinition[])[0]!
      expect(last).toMatchObject({ formula: 'dossier.fields.ht * 2', visibleWhen: "dossier.fields.pays = 'FR'", requiredWhen: 'dossier.fields.ht > 1000' })

      await wrapper.get('[data-testid="field-def-formula"]').setValue('')
      expect((wrapper.emitted('update:modelValue')?.at(-1)?.[0] as FieldDefinition[])[0]!.formula).toBeNull()
    })

    it('does not offer rules for groups, task questions nor group columns', () => {
      expect(mount(FieldDefinitionDefsEditor, { props: { modelValue: [{ ...def, type: 'REPEATABLE_GROUP', fields: [] }] } }).find('[data-testid="field-def-rules"]').exists()).toBe(false)
      expect(mount(FieldDefinitionDefsEditor, { props: { modelValue: [def], mode: 'task' } }).find('[data-testid="field-def-rules"]').exists()).toBe(false)
      expect(mount(FieldDefinitionDefsEditor, { props: { modelValue: [def], mode: 'subfields' } }).find('[data-testid="field-def-rules"]').exists()).toBe(false)
    })
  })

  describe('Cherchable (TODO §81, D2)', () => {
    it('is offered for scalar types only and toggles the emitted flag', async () => {
      const wrapper = mount(FieldDefinitionDefsEditor, { props: { modelValue: [
        { name: 'a', label: 'A', type: 'STRING', required: false },
        { name: 'm', label: 'M', type: 'MONEY', required: false },
      ] } })
      const boxes = wrapper.findAll('[data-testid="field-def-searchable"]')
      expect(boxes).toHaveLength(1)
      await boxes[0]!.setValue(true)
      const emitted = wrapper.emitted('update:modelValue')?.[0]?.[0] as FieldDefinition[]
      expect(emitted[0]!.searchable).toBe(true)
    })

    it('is disabled on a sensitive field and cleared when the field becomes sensitive', async () => {
      const wrapper = mount(FieldDefinitionDefsEditor, { props: { modelValue: [{ name: 'a', label: 'A', type: 'STRING', required: false, searchable: true }] } })
      await wrapper.get('[data-testid="field-def-sensitive"]').setValue(true)
      const emitted = wrapper.emitted('update:modelValue')?.[0]?.[0] as FieldDefinition[]
      expect(emitted[0]!.sensitive).toBe(true)
      expect(emitted[0]!.searchable).toBe(false)
      await nextTick()
      expect((wrapper.get('[data-testid="field-def-searchable"]').element as HTMLInputElement).disabled).toBe(true)
    })
  })

  describe('Valeur unique (TODO §102)', () => {
    const row = (over: Partial<FieldDefinition> = {}): FieldDefinition => ({
      name: 'plaque', label: 'Plaque', type: 'STRING', required: false, ...over,
    })

    it('offers the checkbox on text, number and date, including a computed field, and emits unique', async () => {
      const wrapper = mount(FieldDefinitionDefsEditor, {
        props: { modelValue: [row(), row({ name: 'n', type: 'NUMBER' }), row({ name: 'd', type: 'DATE' }), row({ name: 'c', formula: '1' })] },
      })
      const boxes = wrapper.findAll('[data-testid="field-def-unique"]')
      expect(boxes).toHaveLength(4)
      expect(boxes.every((box) => (box.element as HTMLInputElement).disabled)).toBe(false)
      expect(wrapper.text()).toContain('Valeur unique')
      await boxes[0]!.setValue(true)
      const emitted = wrapper.emitted('update:modelValue')?.[0]?.[0] as FieldDefinition[]
      expect(emitted[0]!.unique).toBe(true)
    })

    it('disables the checkbox outside STRING, NUMBER and DATE, and on a sensitive field', async () => {
      const wrapper = mount(FieldDefinitionDefsEditor, {
        props: {
          modelValue: [
            row({ type: 'BOOLEAN' }),
            row({ name: 'sel', type: 'SELECT' }),
            row({ name: 'f', type: 'FILE' }),
            row({ name: 'secret', sensitive: true }),
          ],
        },
      })
      const boxes = wrapper.findAll('[data-testid="field-def-unique"]')
      expect(boxes).toHaveLength(4)
      expect(boxes.every((box) => (box.element as HTMLInputElement).disabled)).toBe(true)
    })

    it('shows why the unique checkbox is inactive, not only in a title', () => {
      const wrapper = mount(FieldDefinitionDefsEditor, {
        props: { modelValue: [row({ type: 'BOOLEAN' }), row({ name: 'secret', sensitive: true })] },
      })
      const boxes = wrapper.findAll('[data-testid="field-def-unique"]')
      const reason = (box: (typeof boxes)[number]) => {
        const describedBy = box.attributes('aria-describedby')
        return describedBy ? wrapper.get(`[id="${describedBy}"]`).text() : ''
      }
      expect(reason(boxes[0]!)).toContain('Réservé aux champs texte, nombre ou date')
      expect(reason(boxes[1]!)).toContain('Un champ sensible ne peut pas être unique')
      expect(wrapper.text()).toContain('Réservé aux champs texte, nombre ou date')
      expect(wrapper.text()).toContain('Un champ sensible ne peut pas être unique')
      for (const box of boxes) {
        const describedBy = box.attributes('aria-describedby')
        expect(describedBy).toBeTruthy()
        expect(wrapper.get(`[id="${describedBy}"]`).element.closest('label')).toBeNull()
      }
    })

    it('clears unique when the field becomes sensitive or leaves the allowed types', async () => {
      const wrapper = mount(FieldDefinitionDefsEditor, {
        props: { modelValue: [row({ unique: true })] },
      })
      await wrapper.get('[data-testid="field-def-sensitive"]').setValue(true)
      expect((wrapper.emitted('update:modelValue')?.[0]?.[0] as FieldDefinition[])[0]!.unique).toBe(false)
      const typed = mount(FieldDefinitionDefsEditor, { props: { modelValue: [row({ unique: true })] } })
      await typed.get('[data-testid="field-def-type"]').setValue('BOOLEAN')
      expect((typed.emitted('update:modelValue')?.[0]?.[0] as FieldDefinition[])[0]!.unique).toBe(false)
    })

    it('hides the checkbox on group columns', () => {
      const wrapper = mount(FieldDefinitionDefsEditor, { props: { modelValue: [row()], mode: 'subfields' } })
      expect(wrapper.find('[data-testid="field-def-unique"]').exists()).toBe(false)
    })
  })
})
