import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import DynamicFields from './DynamicFields.vue'
import type { FieldDefinition } from '@/api/types'

const defs: FieldDefinition[] = [
  {
    name: 'priority',
    label: 'Priority',
    type: 'STRING',
    required: true,
    defaultValue: 'NORMAL',
    options: [],
    group: 'details',
    sensitive: false,
  },
  {
    name: 'ssn',
    label: 'SSN',
    type: 'STRING',
    required: false,
    sensitive: true,
  },
]

describe('DynamicFields', () => {
  it('applies defaults and emits model updates', async () => {
    setActivePinia(createPinia())
    const wrapper = mount(DynamicFields, {
      props: {
        definitions: defs,
        modelValue: {},
      },
    })
    expect(wrapper.emitted('update:modelValue')?.[0]?.[0]).toMatchObject({ priority: 'NORMAL' })
    const input = wrapper.find('input[type="text"]')
    await input.setValue('HIGH')
    const last = wrapper.emitted('update:modelValue')?.at(-1)?.[0] as Record<string, unknown>
    expect(last.priority).toBe('HIGH')
  })

  it('does not apply defaults when applyDefaults is false', () => {
    setActivePinia(createPinia())
    const wrapper = mount(DynamicFields, {
      props: {
        definitions: defs,
        modelValue: {},
        applyDefaults: false,
      },
    })
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })
})
