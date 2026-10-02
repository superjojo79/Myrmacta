import { describe, expect, it } from 'vitest'
import type { FieldDefinition } from '@/api/types'
import { activeFieldFilters, fieldFilterInputs, fieldFiltersFromQuery, tooManyFieldFilters } from './fieldSearch'

const defs: FieldDefinition[] = [
  { name: 'contrat', label: 'Contrat', type: 'STRING', required: false, searchable: true },
  { name: 'montant', label: 'Montant', type: 'NUMBER', required: false, searchable: true },
  { name: 'echeance', label: 'Échéance', type: 'DATE', required: false, searchable: true },
  { name: 'cat', label: 'Catégorie', type: 'SELECT', required: false, searchable: true, options: ['A', 'B'] },
  { name: 'urgent', label: 'Urgent', type: 'BOOLEAN', required: false, searchable: true },
  { name: 'note', label: 'Note', type: 'STRING', required: false },
  { name: 'iban', label: 'IBAN', type: 'STRING', required: false, sensitive: true, searchable: true },
  { name: 'prix', label: 'Prix', type: 'MONEY', required: false, searchable: true },
]

describe('fieldSearch (TODO §81, D2)', () => {
  it('proposes inputs only for searchable, non-sensitive scalar fields', () => {
    const keys = fieldFilterInputs(defs).map((i) => i.key)
    expect(keys).toEqual([
      'contrat.prefix',
      'montant.gte',
      'montant.lte',
      'echeance.gte',
      'echeance.lte',
      'cat',
      'urgent',
    ])
  })

  it('gives a select its options and nothing for an undefined schema', () => {
    expect(fieldFilterInputs(defs).find((i) => i.key === 'cat')?.options).toEqual(['A', 'B'])
    expect(fieldFilterInputs(undefined)).toEqual([])
  })

  it('keeps only filled, trimmed values', () => {
    expect(activeFieldFilters({ a: ' x ', b: '', c: '   ' })).toEqual({ a: 'x' })
  })

  it('flags more filters than the server accepts', () => {
    const six = Object.fromEntries(['a', 'b', 'c', 'd', 'e', 'f'].map((k) => [k, '1']))
    expect(tooManyFieldFilters(six)).toBe(true)
    expect(tooManyFieldFilters({ a: '1' })).toBe(false)
  })

  it('reads f.* parameters from a route query', () => {
    expect(fieldFiltersFromQuery({ 'f.montant.gte': '5', page: '2', 'f.x': 3 })).toEqual({ 'montant.gte': '5' })
  })
})
