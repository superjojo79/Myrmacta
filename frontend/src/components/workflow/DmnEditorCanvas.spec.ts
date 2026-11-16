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

// Feuilles de style de dmn-js : sans elles, la table de décision s'affiche sans grille ni contrôles (#58, C2 et S4).
const loadedStyles = vi.hoisted(() => [] as string[])
const DMN_STYLES = [
  'dmn-js/dist/assets/diagram-js.css',
  'dmn-js/dist/assets/dmn-js-shared.css',
  'dmn-js/dist/assets/dmn-js-drd.css',
  'dmn-js/dist/assets/dmn-js-decision-table.css',
  'dmn-js/dist/assets/dmn-js-decision-table-controls.css',
  'dmn-js/dist/assets/dmn-js-literal-expression.css',
  'dmn-js/dist/assets/dmn-font/css/dmn.css',
] as const
vi.mock('dmn-js/dist/assets/diagram-js.css', () => (loadedStyles.push('dmn-js/dist/assets/diagram-js.css'), {}))
vi.mock('dmn-js/dist/assets/dmn-js-shared.css', () => (loadedStyles.push('dmn-js/dist/assets/dmn-js-shared.css'), {}))
vi.mock('dmn-js/dist/assets/dmn-js-drd.css', () => (loadedStyles.push('dmn-js/dist/assets/dmn-js-drd.css'), {}))
vi.mock('dmn-js/dist/assets/dmn-js-decision-table.css', () => (loadedStyles.push('dmn-js/dist/assets/dmn-js-decision-table.css'), {}))
vi.mock('dmn-js/dist/assets/dmn-js-decision-table-controls.css', () => (loadedStyles.push('dmn-js/dist/assets/dmn-js-decision-table-controls.css'), {}))
vi.mock('dmn-js/dist/assets/dmn-js-literal-expression.css', () => (loadedStyles.push('dmn-js/dist/assets/dmn-js-literal-expression.css'), {}))
vi.mock('dmn-js/dist/assets/dmn-font/css/dmn.css', () => (loadedStyles.push('dmn-js/dist/assets/dmn-font/css/dmn.css'), {}))

describe('DmnEditorCanvas', () => {
  it('loads the dmn-js style sheets with the modeler, so the decision table has its grid and controls', async () => {
    mount(DmnEditorCanvas, { props: { dmnXml: '<definitions/>' } })
    await flushPromises()

    expect([...loadedStyles].sort()).toEqual([...DMN_STYLES].sort())
  })

  it('mounts a dmn-js Modeler and imports the given XML', async () => {
    mount(DmnEditorCanvas, { props: { dmnXml: '<definitions/>' } })
    await flushPromises()

    expect(importXML).toHaveBeenCalledWith('<definitions/>')
  })

  it('converts a DMN 1.1 namespace to DMN 1.3 before importXML (TODO §130)', async () => {
    const dmn11 =
      '<definitions xmlns="http://www.omg.org/spec/DMN/20151101" id="d"><decision id="x" name="Routage"/></definitions>'
    mount(DmnEditorCanvas, { props: { dmnXml: dmn11 } })
    await flushPromises()

    expect(importXML).toHaveBeenCalledWith(
      expect.stringContaining('https://www.omg.org/spec/DMN/20191111/MODEL/'),
    )
    expect(importXML).toHaveBeenCalledWith(expect.not.stringContaining('http://www.omg.org/spec/DMN/20151101'))
    expect(importXML).toHaveBeenCalledWith(expect.stringContaining('name="Routage"'))
  })

  it('falls back to a blank starter DMN template when dmnXml is empty', async () => {
    mount(DmnEditorCanvas, { props: { dmnXml: '' } })
    await flushPromises()

    expect(importXML).toHaveBeenCalledWith(expect.stringContaining('<decisionTable'))
    expect(importXML).toHaveBeenCalledWith(
      expect.stringContaining('https://www.omg.org/spec/DMN/20191111/MODEL/'),
    )
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
