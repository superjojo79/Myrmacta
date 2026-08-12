import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import StatusSelect from './StatusSelect.vue'
import TaskList from '../workflow/TaskList.vue'
import HistoryTimeline from '../workflow/HistoryTimeline.vue'
import BpmnViewer from '../workflow/BpmnViewer.vue'
import { vi } from 'vitest'

vi.mock('bpmn-js/lib/NavigatedViewer', () => ({
  default: class {
    importXML = vi.fn().mockResolvedValue(undefined)
    destroy = vi.fn()
    get = () => ({ zoom: vi.fn(), addMarker: vi.fn() })
  },
}))

describe('shared components', () => {
  it('StatusSelect emits updates', async () => {
    const wrapper = mount(StatusSelect, { props: { modelValue: 'DRAFT' } })
    await wrapper.get('select').setValue('OUVERT')
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['OUVERT'])
  })

  it('TaskList emits complete and reassign', async () => {
    const wrapper = mount(TaskList, {
      props: {
        canReassign: true,
        tasks: [
          {
            taskId: 't1',
            name: 'Review',
            assignee: 'u',
            taskDefinitionKey: 'k',
            createTime: null,
          },
        ],
      },
    })
    const buttons = wrapper.findAll('button')
    await buttons[0].trigger('click')
    await buttons[1].trigger('click')
    expect(wrapper.emitted('complete')?.[0]).toEqual(['t1'])
    expect(wrapper.emitted('reassign')?.[0]).toEqual(['t1'])
  })

  it('HistoryTimeline renders entries', () => {
    const wrapper = mount(HistoryTimeline, {
      props: {
        history: [
          {
            activityId: 'a1',
            name: 'Start',
            assignee: null,
            startTime: '2026-01-01',
            endTime: null,
            durationMs: null,
          },
        ],
      },
    })
    expect(wrapper.text()).toContain('Start')
  })

  it('BpmnViewer shows unavailable message', () => {
    const wrapper = mount(BpmnViewer, { props: { diagram: null, unavailable: true } })
    expect(wrapper.text()).toContain('indisponible')
  })
})
