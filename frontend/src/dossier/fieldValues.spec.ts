import { describe, expect, it } from 'vitest'
import type { FieldDefinition } from '@/api/types'
import { canonicalDecimal, cleanStructuredFields, comparable, deepEqual, formatFieldValue, isBlankValue, isStructured, toPayload } from './fieldValues'

const money: FieldDefinition = { name: 'm', label: 'M', type: 'MONEY', required: false }
const ref: FieldDefinition = { name: 'r', label: 'R', type: 'EXTERNAL_REF', required: false }
const group: FieldDefinition = {
  name: 'g',
  label: 'G',
  type: 'REPEATABLE_GROUP',
  required: false,
  fields: [
    { name: 'poste', label: 'Poste', type: 'STRING', required: true },
    { name: 'qte', label: 'Qté', type: 'NUMBER', required: false },
    money,
  ],
}

describe('fieldValues (TODO §76, D1)', () => {
  it('knows which types are structured', () => {
    expect(isStructured('MONEY')).toBe(true)
    expect(isStructured('REPEATABLE_GROUP')).toBe(true)
    expect(isStructured('EXTERNAL_REF')).toBe(true)
    expect(isStructured('STRING')).toBe(false)
    expect(isStructured(undefined)).toBe(false)
  })

  it('formats structured values readably and never as [object Object]', () => {
    expect(formatFieldValue(money, { amount: '12.50', currency: 'EUR' })).toBe('12.50 EUR')
    expect(formatFieldValue(ref, { system: 'd365fo', id: 'V-1', label: 'Contoso' })).toBe('d365fo · V-1 — Contoso')
    expect(formatFieldValue(ref, { system: 'd365fo', id: 'V-1' })).toBe('d365fo · V-1')
    expect(formatFieldValue(group, [{}, {}])).toBe('2 lignes')
    expect(formatFieldValue(group, [{}])).toBe('1 ligne')
    expect(formatFieldValue({ type: 'STRING' }, 'x')).toBe('x')
    expect(formatFieldValue({ type: 'BOOLEAN' }, false)).toBe('false')
    expect(formatFieldValue(money, null)).toBe('')
    expect(formatFieldValue(money, 'pas un objet')).toBe('')
  })

  it('detects blank structured values', () => {
    expect(isBlankValue(money, undefined)).toBe(true)
    expect(isBlankValue(money, { amount: '', currency: '' })).toBe(true)
    expect(isBlankValue(money, { amount: '', currency: 'EUR' })).toBe(false)
    expect(isBlankValue(ref, { system: ' ', id: '', label: '' })).toBe(true)
    expect(isBlankValue(ref, { system: 'a', id: '', label: '' })).toBe(false)
    expect(isBlankValue(group, [])).toBe(true)
    expect(isBlankValue(group, [{}])).toBe(true)
    expect(isBlankValue({ type: 'BOOLEAN' }, false)).toBe(false)
    expect(isBlankValue({ type: 'STRING' }, '')).toBe(true)
  })

  it('compares 12.5 and 12.50 as the same amount and ignores key order', () => {
    expect(deepEqual(comparable(money, { amount: '12.5', currency: 'EUR' }), comparable(money, { currency: 'EUR', amount: '12.50' }))).toBe(true)
    expect(deepEqual(comparable(money, { amount: '12.5', currency: 'EUR' }), comparable(money, { amount: '12.5', currency: 'USD' }))).toBe(false)
    expect(deepEqual(comparable(ref, { system: 's', id: 'i ', label: '' }), comparable(ref, { system: 's', id: 'i' }))).toBe(true)
  })

  it('compares group rows ignoring empty cells and normalizing amounts', () => {
    const a = [{ poste: 'A', qte: '', m: { amount: '1', currency: 'EUR' } }]
    const b = [{ poste: 'A', m: { amount: '1.00', currency: 'EUR' } }]
    expect(deepEqual(comparable(group, a), comparable(group, b))).toBe(true)
    expect(deepEqual(comparable(group, a), comparable(group, [{ poste: 'B' }]))).toBe(false)
  })

  it('builds clean payloads: trimmed, numbers coerced, empty rows and cells dropped', () => {
    expect(toPayload(money, { amount: ' 12.5 ', currency: 'EUR' })).toEqual({ amount: '12.5', currency: 'EUR' })
    expect(toPayload(ref, { system: ' d365fo ', id: ' V-1 ', label: '  ' })).toEqual({ system: 'd365fo', id: 'V-1' })
    expect(
      toPayload(group, [
        { poste: 'A', qte: '3', prix: { amount: '', currency: '' } },
        {},
        { poste: '', qte: '' },
      ]),
    ).toEqual([{ poste: 'A', qte: 3 }])
    expect(toPayload({ name: 'n', label: 'n', type: 'NUMBER', required: false }, '4')).toBe(4)
  })

  it('deepEqual handles primitives, arrays and objects', () => {
    expect(deepEqual(1, 1)).toBe(true)
    expect(deepEqual([1, { a: 2 }], [1, { a: 2 }])).toBe(true)
    expect(deepEqual([1], [1, 2])).toBe(false)
    expect(deepEqual({ a: 1 }, { a: 1, b: 2 })).toBe(false)
    expect(deepEqual({ a: 1 }, [1])).toBe(false)
    expect(deepEqual(null, {})).toBe(false)
  })

  it('strips computed and managedByWorkflow fields from a create payload (TODO §126)', () => {
    const out = cleanStructuredFields(
      [
        { name: 'nom', label: 'Nom', type: 'STRING', required: false },
        { name: 'ttc', label: 'TTC', type: 'NUMBER', required: false, formula: '1' },
        { name: 'id', label: 'Id', type: 'STRING', required: false, managedByWorkflow: true },
      ],
      { nom: 'Dupont', ttc: 12, id: 'forged' },
    )
    expect(out).toEqual({ nom: 'Dupont' })
  })

  it('cleans only the structured fields of a creation payload', () => {
    const scalar: FieldDefinition = { name: 'note', label: 'N', type: 'STRING', required: false }
    const out = cleanStructuredFields([scalar, money, ref, group], {
      note: '',
      m: { amount: '', currency: '' },
      r: { system: ' d365fo ', id: ' 1 ', label: '' },
      g: [{}, { poste: 'A' }],
      inconnu: 'x',
    })
    expect(out).toEqual({ note: '', r: { system: 'd365fo', id: '1' }, g: [{ poste: 'A' }], inconnu: 'x' })
  })

  it('compares large amounts as decimal text, never through Number (revue adversariale D1, M3)', () => {
    const a = { amount: '999999999999999.98', currency: 'EUR' }
    const b = { amount: '999999999999999.99', currency: 'EUR' }
    expect(deepEqual(comparable(money, a), comparable(money, b))).toBe(false)
    expect(deepEqual(comparable(money, { amount: '99999999999999.98', currency: 'EUR' }), comparable(money, { amount: '99999999999999.99', currency: 'EUR' }))).toBe(false)
    expect(deepEqual(comparable(money, { amount: '007.10', currency: 'EUR' }), comparable(money, { amount: '7.1', currency: 'EUR' }))).toBe(true)
  })

  it('canonicalDecimal normalizes plain decimals and refuses anything else', () => {
    expect(canonicalDecimal('12.50')).toBe('12.5')
    expect(canonicalDecimal('007.10')).toBe('7.1')
    expect(canonicalDecimal('-0.0')).toBe('0')
    expect(canonicalDecimal('-12.30')).toBe('-12.3')
    expect(canonicalDecimal(' 5 ')).toBe('5')
    expect(canonicalDecimal('0')).toBe('0')
    for (const bad of ['', 'abc', '1,5', '1e3', '.5', '1.', '--1']) expect(canonicalDecimal(bad)).toBeNull()
  })

  it('a group whose rows are all empty is blank (no spontaneous patch), and blank rows do not count in comparisons', () => {
    expect(isBlankValue(group, [{}])).toBe(true)
    expect(isBlankValue(group, [{ poste: '', qte: null }, { poste: '  ' }])).toBe(true)
    expect(isBlankValue(group, [{ poste: 'A' }])).toBe(false)
    expect(deepEqual(comparable(group, [{ poste: 'A' }, {}]), comparable(group, [{ poste: 'A' }]))).toBe(true)
  })
})
