import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import BpmnEditorCanvas from './BpmnEditorCanvas.vue'

const modelerOptions = vi.hoisted(() => ({ value: null as null | { additionalModules: unknown[] } }))
const importXML = vi.fn().mockResolvedValue(undefined)
const saveXML = vi.fn().mockResolvedValue({ xml: '<definitions edited="true"/>' })
const zoom = vi.fn()
const destroy = vi.fn()
let commandStackChangedHandler: (() => void) | undefined

vi.mock('bpmn-js/lib/Modeler', () => ({
  default: class {
    constructor(options: { additionalModules: unknown[] }) {
      modelerOptions.value = options
    }
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

vi.mock('bpmn-js/dist/assets/diagram-js.css', () => ({}))
vi.mock('bpmn-js/dist/assets/bpmn-js.css', () => ({}))
vi.mock('bpmn-js/dist/assets/bpmn-font/css/bpmn.css', () => ({}))
vi.mock('@bpmn-io/properties-panel/assets/properties-panel.css', () => ({}))
vi.mock('./bpmn-blocks/blockPaletteProvider', () => ({ BlockPaletteModule: {} }))
vi.mock('./bpmn-blocks/blockPropertiesProvider', () => ({ BlockPropertiesProviderModule: {} }))
vi.mock('./bpmn-blocks/blockMarkers', () => ({ BlockMarkersModule: {} }))

const catalog = vi.hoisted(() => ({ value: null as null | { version: number; blocks: unknown[] } }))
vi.mock('./bpmn-blocks/catalogClient', () => ({ loadBlockCatalog: () => Promise.resolve(catalog.value) }))

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

  describe('palette métier (TODO §73)', () => {
    it('ajoute les modules de briques quand le catalogue est disponible', async () => {
      catalog.value = { version: 1, blocks: [{ id: 'x' }] }
      mount(BpmnEditorCanvas, { props: { bpmnXml: '<definitions/>' } })
      await flushPromises()
      expect(modelerOptions.value!.additionalModules).toHaveLength(6)
    })

    it('reste sur la palette et le panneau natifs quand le catalogue est indisponible (S-PW-5)', async () => {
      catalog.value = null
      mount(BpmnEditorCanvas, { props: { bpmnXml: '<definitions/>' } })
      await flushPromises()
      expect(modelerOptions.value!.additionalModules).toHaveLength(3)
      expect(importXML).toHaveBeenCalled()
    })
  })
})
