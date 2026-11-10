import { describe, expect, it } from 'vitest'
import { validateFieldDefinitions } from './validateFieldDefinitions'
import type { FieldDefinition } from '@/api/types'

function field(overrides: Partial<FieldDefinition> = {}): FieldDefinition {
  return {
    name: 'priority',
    label: 'Priority',
    type: 'STRING',
    required: false,
    ...overrides,
  }
}

describe('validateFieldDefinitions', () => {
  it('accepts an empty list', () => {
    expect(validateFieldDefinitions([])).toEqual([])
  })

  it('accepts a fully valid list', () => {
    const errors = validateFieldDefinitions([
      field({ name: 'priority' }),
      field({ name: 'amount', type: 'NUMBER', defaultValue: '42' }),
      field({ name: 'status', type: 'SELECT', options: ['A', 'B'] }),
    ])
    expect(errors).toEqual([])
  })

  it('rejects a duplicate name within the list', () => {
    const errors = validateFieldDefinitions([field({ name: 'priority' }), field({ name: 'priority' })])
    expect(errors.some((e) => e.includes('priority'))).toBe(true)
  })

  it('rejects a blank name', () => {
    const errors = validateFieldDefinitions([field({ name: '  ' })])
    expect(errors.length).toBeGreaterThan(0)
  })

  it('rejects a blank label', () => {
    const errors = validateFieldDefinitions([field({ label: '' })])
    expect(errors.length).toBeGreaterThan(0)
  })

  it('rejects a SELECT field with no options', () => {
    const errors = validateFieldDefinitions([field({ type: 'SELECT', options: [] })])
    expect(errors.some((e) => e.includes('priority'))).toBe(true)
  })

  it('accepts a SELECT field with at least one option', () => {
    const errors = validateFieldDefinitions([field({ type: 'SELECT', options: ['A'] })])
    expect(errors).toEqual([])
  })

  it('rejects a non-numeric defaultValue on a NUMBER field', () => {
    const errors = validateFieldDefinitions([field({ type: 'NUMBER', defaultValue: 'abc' })])
    expect(errors.length).toBeGreaterThan(0)
  })

  it('rejects a non-boolean defaultValue on a BOOLEAN field', () => {
    const errors = validateFieldDefinitions([field({ type: 'BOOLEAN', defaultValue: 'maybe' })])
    expect(errors.length).toBeGreaterThan(0)
  })

  it('accepts true/false defaultValue on a BOOLEAN field', () => {
    expect(validateFieldDefinitions([field({ type: 'BOOLEAN', defaultValue: 'true' })])).toEqual([])
    expect(validateFieldDefinitions([field({ type: 'BOOLEAN', defaultValue: 'false' })])).toEqual([])
  })

  it('rejects a non-ISO defaultValue on a DATE field', () => {
    const errors = validateFieldDefinitions([field({ type: 'DATE', defaultValue: 'not-a-date' })])
    expect(errors.length).toBeGreaterThan(0)
  })

  it('accepts an ISO defaultValue on a DATE field', () => {
    expect(validateFieldDefinitions([field({ type: 'DATE', defaultValue: '2026-01-15' })])).toEqual([])
  })

  it('accepts a null/empty defaultValue regardless of type', () => {
    expect(validateFieldDefinitions([field({ type: 'NUMBER', defaultValue: null })])).toEqual([])
    expect(validateFieldDefinitions([field({ type: 'NUMBER', defaultValue: '' })])).toEqual([])
  })
})

describe('validateFieldDefinitions — structured types (TODO §76, D1)', () => {
  const text = (name: string): FieldDefinition => ({ name, label: name, type: 'STRING', required: false })
  const group = (over: Partial<FieldDefinition> = {}): FieldDefinition => ({
    name: 'g',
    label: 'G',
    type: 'REPEATABLE_GROUP',
    required: false,
    fields: [text('a')],
    ...over,
  })

  it('accepts well-formed MONEY, EXTERNAL_REF and REPEATABLE_GROUP definitions', () => {
    expect(
      validateFieldDefinitions([
        { name: 'm', label: 'M', type: 'MONEY', required: false, options: ['EUR', 'USD'] },
        { name: 'r', label: 'R', type: 'EXTERNAL_REF', required: false, options: ['d365fo'] },
        group({ minRows: 0, maxRows: 5 }),
      ]),
    ).toEqual([])
  })

  it('checks currency codes and system identifiers', () => {
    const errors = validateFieldDefinitions([
      { name: 'm', label: 'M', type: 'MONEY', required: false, options: ['eur', 'EUR'] },
      { name: 'r', label: 'R', type: 'EXTERNAL_REF', required: false, options: ['mauvais système'] },
    ])
    expect(errors).toHaveLength(2)
    expect(errors[0]).toContain('ISO 4217')
    expect(errors[1]).toContain('identifiant valide')
  })

  it('refuses a default value on a structured type', () => {
    const errors = validateFieldDefinitions([{ name: 'm', label: 'M', type: 'MONEY', required: false, defaultValue: '1' }])
    expect(errors[0]).toContain('valeur par défaut')
  })

  it('bounds groups: sub-fields, types, sensitivity, rows', () => {
    expect(validateFieldDefinitions([group({ fields: [] })])[0]).toContain('au moins un sous-champ')
    expect(
      validateFieldDefinitions([group({ fields: [{ name: 'f', label: 'f', type: 'FILE', required: false }] })])[0],
    ).toContain('pas autorisé')
    expect(
      validateFieldDefinitions([group({ fields: [{ name: 'f', label: 'f', type: 'REPEATABLE_GROUP', required: false, fields: [text('x')] }] })])
        .some((e) => e.includes('pas autorisé')),
    ).toBe(true)
    expect(
      validateFieldDefinitions([group({ fields: [{ ...text('s'), sensitive: true }] })])[0],
    ).toContain('ne peut pas être sensible')
    expect(validateFieldDefinitions([group({ minRows: 5, maxRows: 2 })])[0]).toContain('dépasse')
    expect(validateFieldDefinitions([group({ maxRows: 501 })])[0]).toContain('500')
    expect(validateFieldDefinitions([group({ minRows: -1 })])[0]).toContain('négatif')
    const many = Array.from({ length: 21 }, (_, i) => text(`f${i}`))
    expect(validateFieldDefinitions([group({ fields: many })])[0]).toContain('au plus 20')
  })

  it('validates sub-fields recursively (names, labels, duplicates) and names the group', () => {
    const errors = validateFieldDefinitions([group({ fields: [text('a'), text('a'), { name: '', label: '', type: 'STRING', required: false }] })])
    expect(errors.some((e) => e.includes('utilisé plusieurs fois') && e.includes('groupe « g »'))).toBe(true)
    expect(errors.some((e) => e.includes('nom du champ est obligatoire'))).toBe(true)
  })

  describe('TODO §80 — règles (champ calculé, visibilité, obligation)', () => {
    const base = { name: 'x', label: 'X', type: 'NUMBER' as const, required: false }

    it('accepts well-formed rules', () => {
      expect(
        validateFieldDefinitions([
          { ...base, name: 'a' },
          { ...base, name: 'b', formula: 'dossier.fields.a * 2' },
          { ...base, name: 'c', visibleWhen: 'dossier.fields.a > 1', requiredWhen: 'dossier.fields.a > 5' },
        ]),
      ).toEqual([])
    })

    it('a computed field is read-only: no required, default, sensitivity, other rule, group or file', () => {
      const messages = (over: Partial<FieldDefinition>) => validateFieldDefinitions([{ ...base, formula: '1', ...over }]).join(' | ')
      expect(messages({ required: true })).toContain('lecture seule')
      expect(messages({ defaultValue: '5' })).toContain('lecture seule')
      expect(messages({ sensitive: true })).toContain('ne peut pas être sensible')
      expect(messages({ visibleWhen: 'true' })).toContain('lecture seule')
      expect(messages({ requiredWhen: 'true' })).toContain('lecture seule')
      expect(messages({ type: 'FILE' })).toContain('ne peut pas être calculé')
    })

    it('managedByWorkflow rejects required, default, formula and group sub-fields (TODO §126)', () => {
      expect(validateFieldDefinitions([field({ managedByWorkflow: true })])).toEqual([])
      expect(validateFieldDefinitions([field({ managedByWorkflow: true, required: true })]).join(' ')).toContain(
        'géré par le workflow',
      )
      expect(validateFieldDefinitions([field({ managedByWorkflow: true, defaultValue: 'x' })]).join(' ')).toContain(
        'géré par le workflow',
      )
      expect(validateFieldDefinitions([field({ managedByWorkflow: true, formula: '1' })]).join(' ')).toContain(
        'calculé et géré',
      )
      const group = {
        name: 'g',
        label: 'G',
        type: 'REPEATABLE_GROUP' as const,
        required: false,
        fields: [field({ managedByWorkflow: true })],
      }
      expect(validateFieldDefinitions([group]).join(' ')).toContain('ne peut pas être géré')
    })

    it('bounds an expression and refuses rules on a group sub-field', () => {
      expect(validateFieldDefinitions([{ ...base, formula: '1 + '.repeat(600) + '1' }]).join(' ')).toContain('dépasse 2000')
      const group = { name: 'g', label: 'G', type: 'REPEATABLE_GROUP' as const, required: false, fields: [{ ...base, visibleWhen: 'true' }] }
      expect(validateFieldDefinitions([group]).join(' ')).toContain('ne peut pas porter de règle')
    })
  })

  it('refuses a searchable sensitive or structured field (TODO §81, D2)', () => {
    expect(validateFieldDefinitions([field({ searchable: true })])).toEqual([])
    expect(validateFieldDefinitions([field({ searchable: true, sensitive: true })]).join(' ')).toContain('cherchable')
    expect(validateFieldDefinitions([field({ type: 'MONEY', searchable: true })]).join(' ')).toContain('cherchable')
  })

  it('mirrors unique: STRING, NUMBER, DATE and a computed field; not a sensitive field, another type, a long name or a group column (TODO §102)', () => {
    expect(validateFieldDefinitions([field({ unique: true })])).toEqual([])
    expect(validateFieldDefinitions([field({ type: 'NUMBER', unique: true })])).toEqual([])
    expect(validateFieldDefinitions([field({ type: 'DATE', unique: true })])).toEqual([])
    expect(validateFieldDefinitions([field({ unique: true, formula: 'dossier.fields.x' })])).toEqual([])
    expect(validateFieldDefinitions([field({ unique: true, sensitive: true })]).join(' ')).toContain('unique')
    expect(validateFieldDefinitions([field({ type: 'BOOLEAN', unique: true })]).join(' ')).toContain('unique')
    expect(validateFieldDefinitions([field({ type: 'SELECT', unique: true, options: ['A'] })]).join(' ')).toContain('unique')
    expect(validateFieldDefinitions([field({ name: 'n'.repeat(101), unique: true })]).join(' ')).toContain('100')
    const group = {
      name: 'g',
      label: 'G',
      type: 'REPEATABLE_GROUP' as const,
      required: false,
      fields: [{ name: 'a', label: 'A', type: 'STRING' as const, required: false, unique: true }],
    }
    expect(validateFieldDefinitions([group]).join(' ')).toContain('unique')
  })
  it('refuses a help text longer than 500 characters (LC4a)', () => {
    const errors = validateFieldDefinitions([{ name: 'a', label: 'A', type: 'STRING', required: false, helpText: 'x'.repeat(501) }])
    expect(errors.join(' ')).toContain('aide')
    expect(validateFieldDefinitions([{ name: 'a', label: 'A', type: 'STRING', required: false, helpText: 'x'.repeat(500) }])).toEqual([])
  })

  it('mirrors the server: no control, bidirectional or zero-width character in a help text; no half width on a group', () => {
    for (const bad of ['a\tb', 'a\rb', 'a\u202Eb', 'a\u200Bb']) {
      const errors = validateFieldDefinitions([{ name: 'a', label: 'A', type: 'STRING', required: false, helpText: bad }])
      expect(errors.join(' '), JSON.stringify(bad)).toContain('caractère interdit')
    }
    expect(validateFieldDefinitions([{ name: 'a', label: 'A', type: 'STRING', required: false, helpText: 'ligne 1\nligne 2' }])).toEqual([])
    const group = validateFieldDefinitions([
      { name: 'g', label: 'G', type: 'REPEATABLE_GROUP', required: false, width: 'HALF', fields: [{ name: 'c', label: 'C', type: 'STRING', required: false }] },
    ])
    expect(group.join(' ')).toContain('toute la largeur')
  })
})
