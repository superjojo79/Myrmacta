import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import BpmnEditorCanvas from './BpmnEditorCanvas.vue'

const importXML = vi.fn().mockResolvedValue(undefined)
const saveXML = vi.fn().mockResolvedValue({ xml: '<definitions edited="true"/>' })
const zoom = vi.fn()
const destroy = vi.fn()
let commandStackChangedHandler: (() => void) | undefined

vi.mock('bpmn-js/lib/Modeler', () => ({
  default: class {
    importXML = importXML
    saveXML = saveXML
    destroy = destroy
    get = (name: string) => {
      if (name === 'canvas') return { zoom }
      if (name === 'eventBus') {
        return {
          on: (event: string, handler: () => void) => {
            if (event === 'commandStack.changed') commandStackChangedHandler = handler
          },
        }
      }
      return {}
    }
  },
}))

vi.mock('bpmn-js-properties-panel', () => ({
  BpmnPropertiesPanelModule: {},
  BpmnPropertiesProviderModule: {},
  useService: vi.fn(),
}))

vi.mock('./bpmn-properties/flowablePropertiesProvider', () => ({
  FlowablePropertiesProviderModule: {},
}))

describe('BpmnEditorCanvas', () => {
  it('mounts a bpmn-js Modeler (not a read-only viewer) and imports the given XML', async () => {
    mount(BpmnEditorCanvas, { props: { bpmnXml: '<definitions/>' } })
    await flushPromises()

    expect(importXML).toHaveBeenCalledWith('<definitions/>')
    expect(zoom).toHaveBeenCalledWith('fit-viewport')
  })

  it('emits dirty after a user command changes the commandStack', async () => {
    commandStackChangedHandler = undefined
    const wrapper = mount(BpmnEditorCanvas, { props: { bpmnXml: '<definitions/>' } })
    await flushPromises()

    expect(commandStackChangedHandler).toBeTypeOf('function')
    commandStackChangedHandler?.()

    expect(wrapper.emitted('dirty')).toHaveLength(1)
  })

  it('exposes saveXML() returning the well-formed XML after edition', async () => {
    const wrapper = mount(BpmnEditorCanvas, { props: { bpmnXml: '<definitions/>' } })
    await flushPromises()

    const xml = await (wrapper.vm as unknown as { saveXML: () => Promise<string> }).saveXML()

    expect(saveXML).toHaveBeenCalled()
    expect(xml).toBe('<definitions edited="true"/>')
  })

  it('the blank starter template declares xmlns:flowable (D-FLOWABLE-NS, TODO §52)', async () => {
    // Regression guard: without this, the *first* flowable:* attribute ever set on a brand-new
    // diagram (async/delegateExpression/expression/scope/formKey) round-trips through
    // getValue()/setValue() but is silently dropped by saveXML() -- moddle-xml can't emit a
    // prefix it never saw a URI for. Proven against the real (unmocked) engine in
    // e2e/pilotage.spec.ts E2E-FORM-4.
    mount(BpmnEditorCanvas, { props: { bpmnXml: '' } })
    await flushPromises()

    expect(importXML).toHaveBeenCalledWith(expect.stringContaining('xmlns:flowable="http://flowable.org/bpmn"'))
  })
})
