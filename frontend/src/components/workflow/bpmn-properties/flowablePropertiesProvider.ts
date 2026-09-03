import {
  CheckboxEntry,
  TextFieldEntry,
  isCheckboxEntryEdited,
  isTextFieldEntryEdited,
} from '@bpmn-io/properties-panel'
import { useService } from 'bpmn-js-properties-panel'
import { is } from 'bpmn-js/lib/util/ModelUtil'
import type { Element as BpmnElement } from 'bpmn-js/lib/model/Types'

// Custom bpmn-js-properties-panel provider exposing only the flowable:*
// attributes actually used elsewhere in this repository (see the .bpmn20.xml
// process definitions under src/main/resources/processes/): `async`,
// `delegateExpression`, `expression` on a serviceTask, `scope` on a signal,
// and `formKey` on a userTask (TODO §52). Nothing else (no
// assignee/candidateGroups) — D-EDIT-SCOPE.
//
// Any other flowable:* attribute present in an imported diagram (e.g. one
// authored outside this editor) is left untouched: bpmn-moddle preserves
// unknown-namespace attributes generically (read into the moddle element and
// re-serialized verbatim on saveXML()) without needing a dedicated provider
// entry or moddle extension descriptor for it.

const LOW_PRIORITY = 500

interface EntryProps {
  element: BpmnElement
}

type Debounce = <A extends unknown[]>(fn: (...args: A) => void) => (...args: A) => void

function Async(props: EntryProps) {
  const { element } = props
  const modeling = useService<{
    updateModdleProperties: (el: unknown, mo: unknown, props: Record<string, unknown>) => void
  }>('modeling')

  const getValue = () => element.businessObject.get('flowable:async') === 'true' ||
    element.businessObject.get('flowable:async') === true

  const setValue = (value: boolean) => {
    modeling.updateModdleProperties(element, element.businessObject, {
      'flowable:async': value ? 'true' : undefined,
    })
  }

  return CheckboxEntry({
    element,
    id: 'flowable-async',
    label: 'Asynchrone',
    getValue,
    setValue,
  })
}

function textFieldEntry(attribute: string, label: string) {
  return function TextField(props: EntryProps) {
    const { element } = props
    const modeling = useService<{
      updateModdleProperties: (el: unknown, mo: unknown, props: Record<string, unknown>) => void
    }>('modeling')
    const debounce = useService<Debounce>('debounceInput')

    const getValue = () => (element.businessObject.get(attribute) as string | undefined) ?? ''

    const setValue = (value: string | undefined) => {
      modeling.updateModdleProperties(element, element.businessObject, {
        [attribute]: value === '' ? undefined : value,
      })
    }

    return TextFieldEntry({
      element,
      id: attribute,
      label,
      getValue,
      setValue,
      debounce,
    })
  }
}

const DelegateExpression = textFieldEntry('flowable:delegateExpression', 'Delegate expression')
const Expression = textFieldEntry('flowable:expression', 'Expression')
const Scope = textFieldEntry('flowable:scope', 'Scope')
const FormKey = textFieldEntry('flowable:formKey', 'Form key')

function serviceTaskGroup() {
  return {
    id: 'flowable-service-task',
    label: 'Flowable',
    entries: [
      { id: 'flowable-async', component: Async, isEdited: isCheckboxEntryEdited },
      {
        id: 'flowable:delegateExpression',
        component: DelegateExpression,
        isEdited: isTextFieldEntryEdited,
      },
      { id: 'flowable:expression', component: Expression, isEdited: isTextFieldEntryEdited },
    ],
  }
}

function signalGroup() {
  return {
    id: 'flowable-signal',
    label: 'Flowable',
    entries: [{ id: 'flowable:scope', component: Scope, isEdited: isTextFieldEntryEdited }],
  }
}

// TODO §52 (formulaires-dynamiques-taches) : flowable:formKey déclare qu'un userTask attend un
// formulaire de tâche avant complétion. Convention FlowForge interprétée uniquement côté frontend
// (taskFormRegistry.ts) — le moteur ne fait que stocker/restituer cette chaîne opaque.
function userTaskGroup() {
  return {
    id: 'flowable-user-task',
    label: 'Flowable',
    entries: [{ id: 'flowable:formKey', component: FormKey, isEdited: isTextFieldEntryEdited }],
  }
}

interface FlowablePropertiesProviderInstance {
  getGroups: (element: BpmnElement) => (groups: unknown[]) => unknown[]
}

export default function FlowablePropertiesProvider(
  this: FlowablePropertiesProviderInstance,
  propertiesPanel: { registerProvider: (priority: number, provider: unknown) => void },
) {
  this.getGroups = (element: BpmnElement) => {
    return (groups: unknown[]) => {
      if (is(element, 'bpmn:ServiceTask')) {
        groups.push(serviceTaskGroup())
      }
      if (is(element, 'bpmn:Signal')) {
        groups.push(signalGroup())
      }
      if (is(element, 'bpmn:UserTask')) {
        groups.push(userTaskGroup())
      }
      return groups
    }
  }

  propertiesPanel.registerProvider(LOW_PRIORITY, this)
}

FlowablePropertiesProvider.$inject = ['propertiesPanel']

export const FlowablePropertiesProviderModule = {
  __init__: ['flowablePropertiesProvider'],
  flowablePropertiesProvider: ['type', FlowablePropertiesProvider],
}
