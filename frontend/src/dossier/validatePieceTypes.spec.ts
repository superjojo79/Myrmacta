import { describe, expect, it } from 'vitest'
import { validatePieceTypes } from './validatePieceTypes'

describe('validatePieceTypes', () => {
  it('accepts valid, distinct piece types', () => {
    expect(
      validatePieceTypes([
        { key: 'preuveDomicile', label: 'Preuve de domicile', sensitive: false },
        { key: 'identite_1', label: 'Identité', sensitive: true },
      ]),
    ).toEqual([])
  })

  it('reports invalid keys, duplicates, blank labels and too many types', () => {
    expect(
      validatePieceTypes([
        { key: 'Preuve-domicile', label: 'A', sensitive: false },
        { key: 'preuve', label: 'B', sensitive: false },
        { key: 'preuve', label: '  ', sensitive: false },
      ]),
    ).toEqual([
      { code: 'key', key: 'Preuve-domicile' },
      { code: 'duplicate', key: 'preuve' },
      { code: 'label', key: 'preuve' },
    ])
    const many = Array.from({ length: 31 }, (_, i) => ({ key: `piece${i}`, label: `P${i}`, sensitive: false }))
    expect(validatePieceTypes(many)).toEqual([{ code: 'tooMany' }])
  })
})
