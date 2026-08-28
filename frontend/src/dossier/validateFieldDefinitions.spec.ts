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
