import { describe, expect, it, vi } from 'vitest'
import { BpmnModdle } from 'bpmn-moddle'

// bpmn-js-properties-panel and @bpmn-io/properties-panel are preact-based UI
// packages (no TS types, deep ESM subpath imports) — mocked at the module
// boundary here, same convention as BpmnCanvas.spec.ts mocking
// bpmn-js/lib/NavigatedViewer. These unit tests exercise the provider's own
// getGroups() wiring (which entries it registers for which element types),
// not the panel's rendering.
vi.mock('bpmn-js-properties-panel', () => ({
  useService: vi.fn(),
}))
vi.mock('@bpmn-io/properties-panel', () => ({
  TextFieldEntry: vi.fn(),
  CheckboxEntry: vi.fn(),
  isTextFieldEntryEdited: vi.fn(),
  isCheckboxEntryEdited: vi.fn(),
}))

const { default: FlowablePropertiesProvider } = await import('./flowablePropertiesProvider')

function fakePropertiesPanel() {
  const registered: Array<{ priority: number; provider: unknown }> = []
  return {
    registerProvider: (priority: number, provider: unknown) => {
      registered.push({ priority, provider })
    },
    registered,
  }
}

function fakeElement(type: string, businessObjectOverrides: Record<string, unknown> = {}) {
  return {
    type,
    businessObject: {
      $type: type,
      $instanceOf: (candidate: string) => candidate === type,
      get: (key: string) => businessObjectOverrides[key],
      ...businessObjectOverrides,
    },
  }
}

describe('FlowablePropertiesProvider', () => {
  it('registers itself with the properties panel', () => {
    const panel = fakePropertiesPanel()
    new (FlowablePropertiesProvider as unknown as new (p: unknown) => void)(panel)

    expect(panel.registered).toHaveLength(1)
  })

  it('exposes async, delegateExpression and expression entries for a serviceTask', () => {
    const panel = fakePropertiesPanel()
    new (FlowablePropertiesProvider as unknown as new (p: unknown) => void)(panel)
    const provider = panel.registered[0]!.provider as {
      getGroups: (element: unknown) => (groups: unknown[]) => unknown[]
    }

    const element = fakeElement('bpmn:ServiceTask')
    const groups = provider.getGroups(element)([])

    expect(groups).toHaveLength(1)
    const entryIds = (groups[0] as { entries: Array<{ id: string }> }).entries.map((e) => e.id)
    expect(entryIds).toEqual(
      expect.arrayContaining(['flowable-async', 'flowable:delegateExpression', 'flowable:expression']),
    )
  })

  it('exposes a scope entry for a signal element', () => {
    const panel = fakePropertiesPanel()
    new (FlowablePropertiesProvider as unknown as new (p: unknown) => void)(panel)
    const provider = panel.registered[0]!.provider as {
      getGroups: (element: unknown) => (groups: unknown[]) => unknown[]
    }

    const element = fakeElement('bpmn:Signal')
    const groups = provider.getGroups(element)([])

    expect(groups).toHaveLength(1)
    const entryIds = (groups[0] as { entries: Array<{ id: string }> }).entries.map((e) => e.id)
    expect(entryIds).toEqual(['flowable:scope'])
  })

  it('adds no custom group for an element without a relevant flowable:* attribute', () => {
    const panel = fakePropertiesPanel()
    new (FlowablePropertiesProvider as unknown as new (p: unknown) => void)(panel)
    const provider = panel.registered[0]!.provider as {
      getGroups: (element: unknown) => (groups: unknown[]) => unknown[]
    }

    const element = fakeElement('bpmn:StartEvent')
    const groups = provider.getGroups(element)([])

    expect(groups).toHaveLength(0)
  })

  it('preserves a flowable:* attribute not covered by the provider through an import/saveXML round-trip', async () => {
    // Empirical check of bpmn-moddle's own behaviour (not the provider's logic):
    // unknown-namespace attributes are read into the moddle element generically
    // and re-serialized verbatim, with no moddle extension descriptor required.
    const xml = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://flowforge/processes">
  <process id="testProcess" isExecutable="true">
    <userTask id="task1" flowable:formKey="myForm"/>
  </process>
</definitions>`

    const moddle = new BpmnModdle()
    const { rootElement } = await moddle.fromXML(xml)
    const { xml: outXml } = await moddle.toXML(rootElement)

    expect(outXml).toContain('flowable:formKey="myForm"')
  })
})
