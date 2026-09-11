import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import DmnEditorCanvas from './DmnEditorCanvas.vue'

const importXML = vi.fn().mockResolvedValue({ warnings: [] })
const saveXML = vi.fn().mockResolvedValue({ xml: '<definitions edited="true"/>' })
const destroy = vi.fn()
const viewerOn = vi.fn()
let viewerCreatedHandler: (() => void) | undefined

const viewer = { on: viewerOn }

vi.mock('dmn-js/lib/Modeler', () => ({
  default: class {
    importXML = importXML
    saveXML = saveXML
    destroy = destroy
    getActiveViewer = () => viewer
    on = (event: string, handler: () => void) => {
      if (event === 'viewer.created') viewerCreatedHandler = handler
    }
  },
}))

describe('DmnEditorCanvas', () => {
  it('mounts a dmn-js Modeler and imports the given XML', async () => {
    mount(DmnEditorCanvas, { props: { dmnXml: '<definitions/>' } })
    await flushPromises()

    expect(importXML).toHaveBeenCalledWith('<definitions/>')
  })

  it('falls back to a blank starter DMN template when dmnXml is empty', async () => {
    mount(DmnEditorCanvas, { props: { dmnXml: '' } })
    await flushPromises()

    expect(importXML).toHaveBeenCalledWith(expect.stringContaining('<decisionTable'))
  })

  it('emits dirty after a user command changes the commandStack on the active viewer', async () => {
    viewerOn.mockClear()
    const wrapper = mount(DmnEditorCanvas, { props: { dmnXml: '<definitions/>' } })
    await flushPromises()

    expect(viewerOn).toHaveBeenCalledWith('commandStack.changed', expect.any(Function))
    const [, handler] = viewerOn.mock.calls[viewerOn.mock.calls.length - 1] as [string, () => void]

    handler()

    expect(wrapper.emitted('dirty')).toHaveLength(1)
  })

  it('re-attaches the dirty listener when dmn-js switches sub-views (viewer.created)', async () => {
    mount(DmnEditorCanvas, { props: { dmnXml: '<definitions/>' } })
    await flushPromises()

    expect(viewerCreatedHandler).toBeTypeOf('function')
  })

  it('exposes saveXML() returning the well-formed XML after edition', async () => {
    const wrapper = mount(DmnEditorCanvas, { props: { dmnXml: '<definitions/>' } })
    await flushPromises()

    const xml = await (wrapper.vm as unknown as { saveXML: () => Promise<string> }).saveXML()

    expect(saveXML).toHaveBeenCalled()
    expect(xml).toBe('<definitions edited="true"/>')
  })
})
