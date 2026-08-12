import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import AppShell from './AppShell.vue'
import { routes } from '@/app/routes'

describe('AppShell', () => {
  it('renders FlowForge brand and pilotage nav without Rapports', async () => {
    setActivePinia(createPinia())
    const router = createRouter({ history: createMemoryHistory(), routes })
    router.push('/supervision')
    await router.isReady()

    const wrapper = mount(AppShell, {
      global: { plugins: [router] },
    })

    expect(wrapper.text()).toContain('FlowForge')
    expect(wrapper.text()).toContain('Supervision')
    expect(wrapper.text()).toContain('Tâches')
    expect(wrapper.text()).not.toContain('Rapports')
  })
})
