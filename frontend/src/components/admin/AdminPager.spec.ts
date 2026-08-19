import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { ref } from 'vue'
import AdminPager from './AdminPager.vue'

describe('AdminPager', () => {
  it('disables previous on first page and next on last page', async () => {
    const page = ref(0)
    const wrapper = mount(AdminPager, {
      props: { totalPages: 2, modelValue: page.value },
      attrs: {
        'onUpdate:modelValue': (value: number) => {
          page.value = value
        },
      },
    })
    expect(wrapper.get('[data-testid="admin-pager-prev"]').attributes('disabled')).toBeDefined()
    await wrapper.get('[data-testid="admin-pager-next"]').trigger('click')
    expect(page.value).toBe(1)
  })
})
