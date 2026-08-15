import { describe, expect, it } from 'vitest'
import { relationLabel } from './relationLabels'

describe('relationLabel', () => {
  it('inverts PARENT_CHILD and SUBSEQUENT by direction', () => {
    expect(relationLabel('PARENT_CHILD', 'OUTGOING')).toBe('Enfant')
    expect(relationLabel('PARENT_CHILD', 'INCOMING')).toBe('Parent')
    expect(relationLabel('SUBSEQUENT', 'OUTGOING')).toBe('Dossier de suite')
    expect(relationLabel('SUBSEQUENT', 'INCOMING')).toBe('Précédent')
  })

  it('keeps symmetric types and maps merge direction', () => {
    expect(relationLabel('LINKED', 'OUTGOING')).toBe('Lié')
    expect(relationLabel('LINKED', 'INCOMING')).toBe('Lié')
    expect(relationLabel('DUPLICATE', 'OUTGOING')).toBe('Doublon')
    expect(relationLabel('MERGED_INTO', 'OUTGOING')).toBe('Fusionné vers')
    expect(relationLabel('MERGED_INTO', 'INCOMING')).toBe('Fusionné depuis')
  })
})
