import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import PieceTypesEditor from './PieceTypesEditor.vue'
import type { PieceType } from '@/api/types'

describe('PieceTypesEditor', () => {
  it('adds, edits and removes piece types', async () => {
    const wrapper = mount(PieceTypesEditor, {
      props: {
        modelValue: [] as PieceType[],
        'onUpdate:modelValue': (value: PieceType[]) => wrapper.setProps({ modelValue: value }),
      },
    })
    expect(wrapper.text()).toContain('Aucun type de pièce déclaré.')

    await wrapper.get('[data-testid="piece-type-add"]').trigger('click')
    await wrapper.get('[data-testid="piece-type-key-0"]').setValue(' preuveDomicile ')
    await wrapper.get('[data-testid="piece-type-label-0"]').setValue('Preuve de domicile')
    await wrapper.get('[data-testid="piece-type-sensitive-0"]').setValue(true)
    expect(wrapper.props('modelValue')).toEqual([{ key: 'preuveDomicile', label: 'Preuve de domicile', sensitive: true }])

    expect(wrapper.get('[data-testid="piece-type-remove-0"]').attributes('aria-label')).toBe(
      'Retirer le type de pièce Preuve de domicile',
    )
    await wrapper.get('[data-testid="piece-type-remove-0"]').trigger('click')
    expect(wrapper.props('modelValue')).toEqual([])
  })

  it('disables adding beyond 30 types', () => {
    const full = Array.from({ length: 30 }, (_, i) => ({ key: `piece${i}`, label: `P${i}`, sensitive: false }))
    const wrapper = mount(PieceTypesEditor, { props: { modelValue: full } })
    expect(wrapper.get('[data-testid="piece-type-add"]').attributes('disabled')).toBeDefined()
  })
})
