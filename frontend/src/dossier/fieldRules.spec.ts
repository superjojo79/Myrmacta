import { describe, expect, it } from 'vitest'
import type { FieldDefinition, FieldRulesPreview } from '@/api/types'
import { hasFieldRules, isComputedField, isFieldApplicable, isFieldRequired } from './fieldRules'

const plain: FieldDefinition = { name: 'a', label: 'A', type: 'STRING', required: false }
const required: FieldDefinition = { name: 'r', label: 'R', type: 'STRING', required: true }
const computed: FieldDefinition = { name: 'c', label: 'C', type: 'NUMBER', required: false, formula: '1' }
const preview = (over: Partial<FieldRulesPreview> = {}): FieldRulesPreview => ({ computed: {}, hidden: [], requiredByRule: [], errors: [], ...over })

describe('fieldRules', () => {
  it('detects whether a type carries any rule', () => {
    expect(hasFieldRules([plain])).toBe(false)
    expect(hasFieldRules([plain, { ...plain, name: 'v', visibleWhen: 'true' }])).toBe(true)
    expect(hasFieldRules([{ ...plain, requiredWhen: 'false' }])).toBe(true)
    expect(hasFieldRules([computed])).toBe(true)
    expect(hasFieldRules([{ ...plain, formula: '' }])).toBe(false)
  })

  it('a field is applicable until a preview says it is hidden', () => {
    expect(isFieldApplicable(plain, null)).toBe(true)
    expect(isFieldApplicable(plain, preview())).toBe(true)
    expect(isFieldApplicable(plain, preview({ hidden: ['a'] }))).toBe(false)
  })

  it('required = static flag while applicable, or made required by a rule; never for a computed or hidden field', () => {
    expect(isFieldRequired(required, null)).toBe(true)
    expect(isFieldRequired(required, preview({ hidden: ['r'] }))).toBe(false)
    expect(isFieldRequired(plain, preview({ requiredByRule: ['a'] }))).toBe(true)
    expect(isFieldRequired(plain, preview())).toBe(false)
    expect(isFieldRequired({ ...computed, required: true }, preview({ requiredByRule: ['c'] }))).toBe(false)
    expect(isComputedField(computed)).toBe(true)
    expect(isComputedField(plain)).toBe(false)
  })
})
