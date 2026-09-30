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

describe('diffDossierFields — structured types (TODO §76, D1)', () => {
  const money: FieldDefinition = { name: 'm', label: 'M', type: 'MONEY', required: false }
  const group: FieldDefinition = {
    name: 'g',
    label: 'G',
    type: 'REPEATABLE_GROUP',
    required: false,
    fields: [{ name: 'poste', label: 'Poste', type: 'STRING', required: false }],
  }

  it('does not report an unchanged amount typed without trailing zeros', () => {
    const snapshot = { m: { amount: '12.50', currency: 'EUR' } }
    expect(diffDossierFields(snapshot, { m: { amount: '12.5', currency: 'EUR' } }, [money])).toEqual({})
  })

  it('reports a changed amount or currency with a clean payload', () => {
    const snapshot = { m: { amount: '12.50', currency: 'EUR' } }
    expect(diffDossierFields(snapshot, { m: { amount: ' 15 ', currency: 'EUR' } }, [money])).toEqual({
      m: { amount: '15', currency: 'EUR' },
    })
    expect(diffDossierFields(snapshot, { m: { amount: '12.50', currency: 'USD' } }, [money])).toEqual({
      m: { amount: '12.50', currency: 'USD' },
    })
  })

  it('clears a structured field that became empty', () => {
    const snapshot = { m: { amount: '1.00', currency: 'EUR' }, g: [{ poste: 'A' }] }
    expect(diffDossierFields(snapshot, { m: { amount: '', currency: '' }, g: [] }, [money, group])).toEqual({
      m: null,
      g: null,
    })
  })

  it('ignores a never-filled structured field', () => {
    expect(diffDossierFields({}, { m: { amount: '', currency: '' }, g: [{}] }, [money, group])).toEqual({})
  })

  it('detects an added, edited or removed row of a group', () => {
    const snapshot = { g: [{ poste: 'A' }] }
    expect(diffDossierFields(snapshot, { g: [{ poste: 'A' }] }, [group])).toEqual({})
    expect(diffDossierFields(snapshot, { g: [{ poste: 'A' }, { poste: 'B' }] }, [group])).toEqual({
      g: [{ poste: 'A' }, { poste: 'B' }],
    })
    expect(diffDossierFields(snapshot, { g: [{ poste: 'Z' }] }, [group])).toEqual({ g: [{ poste: 'Z' }] })
  })

  it('reports a real change of a very large amount (revue adversariale D1, M3)', () => {
    const snapshot = { m: { amount: '999999999999999.98', currency: 'EUR' } }
    expect(diffDossierFields(snapshot, { m: { amount: '999999999999999.99', currency: 'EUR' } }, [money])).toEqual({
      m: { amount: '999999999999999.99', currency: 'EUR' },
    })
  })

  it('does not patch a stored group made of empty rows on every save (revue adversariale D1, B3)', () => {
    expect(diffDossierFields({ g: [{}] }, { g: [{}] }, [group])).toEqual({})
    expect(diffDossierFields({ g: [{ poste: 'A' }, {}] }, { g: [{ poste: 'A' }, {}] }, [group])).toEqual({})
  })
})
