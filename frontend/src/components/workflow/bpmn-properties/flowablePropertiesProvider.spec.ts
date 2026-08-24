import { describe, expect, it, vi } from 'vitest'
import { BpmnModdle } from 'bpmn-moddle'
import { useService } from 'bpmn-js-properties-panel'

// bpmn-js-properties-panel and @bpmn-io/properties-panel are preact-based UI
// packages (no TS types, deep ESM subpath imports) — mocked at the module
// boundary here, same convention as BpmnCanvas.spec.ts mocking
// bpmn-js/lib/NavigatedViewer. These unit tests exercise the provider's own
// getGroups() wiring (which entries it registers for which element types)
// AND the getValue()/setValue() read/write bridge each entry builds — not
// the panel's own rendering (CheckboxEntry/TextFieldEntry are mocked to
// simply capture the props they were called with, so the test can invoke
// getValue/setValue directly).
vi.mock('bpmn-js-properties-panel', () => ({
  useService: vi.fn(),
}))
vi.mock('@bpmn-io/properties-panel', () => ({
  TextFieldEntry: vi.fn((props: unknown) => props),
  CheckboxEntry: vi.fn((props: unknown) => props),
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

describe('FlowablePropertiesProvider entries — getValue/setValue', () => {
  function entryComponent(elementType: string, entryId: string) {
    const panel = fakePropertiesPanel()
    new (FlowablePropertiesProvider as unknown as new (p: unknown) => void)(panel)
    const provider = panel.registered[0]!.provider as {
      getGroups: (element: unknown) => (groups: unknown[]) => unknown[]
    }
    const element = fakeElement(elementType)
    const groups = provider.getGroups(element)([]) as Array<{ entries: Array<{ id: string; component: unknown }> }>
    const entry = groups[0]!.entries.find((e) => e.id === entryId)!
    return { element, component: entry.component as (props: { element: unknown }) => Record<string, unknown> }
  }

  it('flowable-async: getValue reads flowable:async as a boolean, setValue writes "true"/undefined', () => {
    const updateModdleProperties = vi.fn()
    vi.mocked(useService).mockImplementation(
      (name: string) => (name === 'modeling' ? { updateModdleProperties } : undefined) as never,
    )

    const truthyElement = fakeElement('bpmn:ServiceTask', { 'flowable:async': 'true' })
    let props = entryComponent('bpmn:ServiceTask', 'flowable-async').component({ element: truthyElement })
    expect((props.getValue as () => boolean)()).toBe(true)

    const falsyElement = fakeElement('bpmn:ServiceTask', { 'flowable:async': undefined })
    props = entryComponent('bpmn:ServiceTask', 'flowable-async').component({ element: falsyElement })
    expect((props.getValue as () => boolean)()).toBe(false)
    ;(props.setValue as (v: boolean) => void)(true)
    expect(updateModdleProperties).toHaveBeenCalledWith(falsyElement, falsyElement.businessObject, {
      'flowable:async': 'true',
    })
    ;(props.setValue as (v: boolean) => void)(false)
    expect(updateModdleProperties).toHaveBeenLastCalledWith(falsyElement, falsyElement.businessObject, {
      'flowable:async': undefined,
    })
  })

  it('flowable:delegateExpression: getValue reads the attribute, setValue writes it (undefined when cleared)', () => {
    const updateModdleProperties = vi.fn()
    vi.mocked(useService).mockImplementation(
      (name: string) =>
        (name === 'modeling'
          ? { updateModdleProperties }
          : name === 'debounceInput'
            ? (fn: unknown) => fn
            : undefined) as never,
    )

    const element = fakeElement('bpmn:ServiceTask', { 'flowable:delegateExpression': '${myBean}' })
    const { component } = entryComponent('bpmn:ServiceTask', 'flowable:delegateExpression')
    const props = component({ element })

    expect((props.getValue as () => string)()).toBe('${myBean}')

    ;(props.setValue as (v: string | undefined) => void)('${otherBean}')
    expect(updateModdleProperties).toHaveBeenCalledWith(element, element.businessObject, {
      'flowable:delegateExpression': '${otherBean}',
    })
    ;(props.setValue as (v: string | undefined) => void)('')
    expect(updateModdleProperties).toHaveBeenLastCalledWith(element, element.businessObject, {
      'flowable:delegateExpression': undefined,
    })
  })

  it('flowable:scope: getValue defaults to "" when unset', () => {
    vi.mocked(useService).mockImplementation(
      (name: string) => (name === 'debounceInput' ? (fn: unknown) => fn : undefined) as never,
    )

    const element = fakeElement('bpmn:Signal', {})
    const { component } = entryComponent('bpmn:Signal', 'flowable:scope')
    const props = component({ element })

    expect((props.getValue as () => string)()).toBe('')
  })
})
