import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import MobileTasksView from './MobileTasksView.vue'
import { useTasksStore } from '@/stores/tasks'
import { createMemoryHistory, createRouter } from 'vue-router'
import { routes } from '@/app/routes'

vi.mock('@/api/tasks', () => ({
  listMyTasks: vi.fn().mockResolvedValue([]),
  completeTask: vi.fn().mockResolvedValue(undefined),
}))

describe('MobileTasksView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('shows empty state', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/tasks')
    await router.isReady()
    const store = useTasksStore()
    store.tasks = []
    store.loading = false
    const wrapper = mount(MobileTasksView, { global: { plugins: [router] } })
    expect(wrapper.text()).toContain('Aucune tâche assignée')
  })
})
