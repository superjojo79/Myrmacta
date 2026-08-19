import { describe, expect, it } from 'vitest'
import { defineComponent } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { useAdminListQuery } from './useAdminListQuery'

const Probe = defineComponent({
  setup() {
    return useAdminListQuery()
  },
  template: '<div>{{ page }}-{{ size }}</div>',
})

describe('useAdminListQuery', () => {
  it('hydrates from the route and writes page to the query', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: Probe }],
    })
    await router.push('/?page=2')
    await router.isReady()
    const wrapper = mount(Probe, { global: { plugins: [router] } })
    await flushPromises()
    expect(wrapper.text()).toContain('2-20')
  })
})
