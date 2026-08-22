import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import BpmnCanvas from './BpmnCanvas.vue'

const importXML = vi.fn().mockResolvedValue(undefined)
const addMarker = vi.fn()
const zoom = vi.fn()
const destroy = vi.fn()

vi.mock('bpmn-js/lib/NavigatedViewer', () => ({
  default: class {
    importXML = importXML
    destroy = destroy
    get = () => ({ zoom, addMarker })
  },
}))

describe('BpmnCanvas', () => {
  it('renders the given XML via bpmn-js importXML', async () => {
    mount(BpmnCanvas, { props: { bpmnXml: '<definitions/>' } })
    await flushPromises()

    expect(importXML).toHaveBeenCalledWith('<definitions/>')
    expect(zoom).toHaveBeenCalledWith('fit-viewport')
  })

  it('applies optional markers when provided, none when absent', async () => {
    addMarker.mockClear()
    mount(BpmnCanvas, {
      props: {
        bpmnXml: '<definitions/>',
        activeIds: ['taskA'],
        completedIds: ['start'],
        failedIds: ['svc'],
      },
    })
    await flushPromises()

    expect(addMarker).toHaveBeenCalledWith('taskA', 'highlight-active')
    expect(addMarker).toHaveBeenCalledWith('start', 'highlight-completed')
    expect(addMarker).toHaveBeenCalledWith('svc', 'highlight-failed')

    addMarker.mockClear()
    mount(BpmnCanvas, { props: { bpmnXml: '<definitions/>' } })
    await flushPromises()

    expect(addMarker).not.toHaveBeenCalled()
  })
})
