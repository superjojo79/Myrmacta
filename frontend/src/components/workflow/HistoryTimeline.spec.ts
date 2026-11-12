import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import HistoryTimeline from './HistoryTimeline.vue'

describe('HistoryTimeline', () => {
  it('shows empty state', () => {
    const wrapper = mount(HistoryTimeline, { props: { history: [] } })
    expect(wrapper.text()).toContain('Pas encore d’activité.')
  })

  it('lists entries', () => {
    const wrapper = mount(HistoryTimeline, {
      props: {
        history: [
          {
            activityId: 'start',
            name: 'Start',
            assignee: null,
            startTime: '2026-08-12T00:00:00Z',
            endTime: null,
            durationMs: null,
          },
        ],
      },
    })
    expect(wrapper.text()).toContain('Start')
  })

  /** Contrat API §131 : le SPA reçoit déjà des étapes significatives nommées. */
  it('prefers the activity name over the technical id', () => {
    const wrapper = mount(HistoryTimeline, {
      props: {
        history: [
          {
            activityId: 'smokeTask',
            name: 'Smoke Task',
            assignee: null,
            startTime: '2026-11-12T00:00:00Z',
            endTime: null,
            durationMs: null,
          },
        ],
      },
    })
    expect(wrapper.text()).toContain('Smoke Task')
    expect(wrapper.text()).not.toContain('flow_')
  })

  it('shows the pager when there is more than one page and emits update:page', async () => {
    const wrapper = mount(HistoryTimeline, { props: { history: [], page: 0, totalPages: 2 } })
    expect(wrapper.find('[data-testid="admin-pager-next"]').exists()).toBe(true)
    await wrapper.get('[data-testid="admin-pager-next"]').trigger('click')
    expect(wrapper.emitted('update:page')?.[0]).toEqual([1])
  })

  it('hides the pager when there is a single page', () => {
    const wrapper = mount(HistoryTimeline, { props: { history: [], page: 0, totalPages: 1 } })
    expect(wrapper.find('[data-testid="admin-pager-next"]').exists()).toBe(false)
  })
})
