import { describe, expect, it } from 'vitest'
import { diffDossierFields } from './diffDossierFields'
import type { FieldDefinition } from '@/api/types'

const defs: FieldDefinition[] = [
  { name: 'priority', label: 'Priority', type: 'STRING', required: true },
  { name: 'note', label: 'Note', type: 'STRING', required: false },
  { name: 'ssn', label: 'SSN', type: 'STRING', required: false, sensitive: true },
  { name: 'active', label: 'Active', type: 'BOOLEAN', required: false },
  { name: 'amount', label: 'Amount', type: 'NUMBER', required: false },
]

describe('diffDossierFields', () => {
  it('includes only changed fields', () => {
    const patch = diffDossierFields(
      { priority: 'NORMAL', note: 'a' },
      { priority: 'HIGH', note: 'a' },
      defs,
    )
    expect(patch).toEqual({ priority: 'HIGH' })
  })

  it('returns empty object when nothing changed', () => {
    expect(diffDossierFields({ priority: 'NORMAL' }, { priority: 'NORMAL' }, defs)).toEqual({})
  })

  it('sends null when an existing optional field is cleared', () => {
    const patch = diffDossierFields({ note: 'hello' }, { note: '' }, defs)
    expect(patch).toEqual({ note: null })
  })

  it('omits a blank draft key that was absent from the snapshot', () => {
    const patch = diffDossierFields({ priority: 'NORMAL' }, { priority: 'NORMAL', ssn: '' }, defs)
    expect(patch).toEqual({})
  })

  it('sends a newly typed value for a key absent from the snapshot', () => {
    const patch = diffDossierFields({ priority: 'NORMAL' }, { priority: 'NORMAL', ssn: '123' }, defs)
    expect(patch).toEqual({ ssn: '123' })
  })

  it('does not treat boolean false as blank', () => {
    const patch = diffDossierFields({ active: true }, { active: false }, defs)
    expect(patch).toEqual({ active: false })
  })

  it('omits an unchanged boolean', () => {
    expect(diffDossierFields({ active: true }, { active: true }, defs)).toEqual({})
  })

  it('coerces number input strings', () => {
    const patch = diffDossierFields({ amount: 10 }, { amount: '12' }, defs)
    expect(patch).toEqual({ amount: 12 })
  })
})
