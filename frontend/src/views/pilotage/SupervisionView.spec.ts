import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import SupervisionView from './SupervisionView.vue'
import { routes } from '@/app/routes'

vi.mock('@/stores/dossier', () => ({
  useDossierStore: () => ({
    dossier: { id: 'd1' },
    loadById: vi.fn().mockResolvedValue(undefined),
    loadByRef: vi.fn().mockResolvedValue(undefined),
  }),
}))

describe('SupervisionView', () => {
  it('renders open form without mock KPIs', async () => {
    setActivePinia(createPinia())
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/supervision')
    await router.isReady()
    const wrapper = mount(SupervisionView, { global: { plugins: [router] } })
    expect(wrapper.text()).toContain('Supervision')
    expect(wrapper.text()).toContain('Créer un dossier')
    expect(wrapper.text()).not.toContain('KPI')
    expect(wrapper.text()).not.toContain('SLA')
  })
})
