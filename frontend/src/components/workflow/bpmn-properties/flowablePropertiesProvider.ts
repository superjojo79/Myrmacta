import {
  CheckboxEntry,
  TextFieldEntry,
  isCheckboxEntryEdited,
  isTextFieldEntryEdited,
} from '@bpmn-io/properties-panel'
import { useService } from 'bpmn-js-properties-panel'
import { is } from 'bpmn-js/lib/util/ModelUtil'
import type { Element as BpmnElement } from 'bpmn-js/lib/model/Types'
import { getBlocks } from '../bpmn-blocks/blockCatalogHolder'
import { recognizeBlock } from '../bpmn-blocks/recognizeBlock'

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
// TODO §68 (portail-externe) D-PORTAL-BPMN-MARKER : présence de portalRecipient = tâche externe,
// répondue par un tiers via le portail (formKey user-question:<schéma> exigé par le backend).
const PortalRecipient = textFieldEntry(
  'flowable:portalRecipient',
  'Tiers externe — adresse (dossierField:<champ> ou variable:<nom>)',
)
const PortalLinkTtlDays = textFieldEntry('flowable:portalLinkTtlDays', 'Tiers externe — validité du lien (jours)')
const Type = textFieldEntry('flowable:type', 'Type (ex. "dmn")')

const FLOWABLE_NS_URI = 'http://flowable.org/bpmn'
const DECISION_TABLE_REFERENCE_KEY_FIELD = 'decisionTableReferenceKey'

interface ModdleService {
  createAny: (name: string, nsUri: string, properties?: Record<string, unknown>) => AnyModdleElement
}

interface AnyModdleElement {
  $type: string
  name?: string
  $body?: string
  // moddle-xml's generic-element writer (ElementSerializer.collectGenericContainments) looks
  // specifically at $body (text content) and $children (nested elements) -- any other own,
  // non-$-prefixed property on a createAny() element is written as a plain XML attribute
  // instead, stringified (verified by a real round-trip failure using an ordinary "values"
  // property name for this: it serialized as values="[object Object]", not as child elements).
  $children?: AnyModdleElement[]
  [key: string]: unknown
}

interface ExtensionElementsModdle {
  get: (key: 'values') => AnyModdleElement[]
  set: (key: 'values', value: AnyModdleElement[]) => void
  values?: AnyModdleElement[]
}

// TODO §61 (introduction-dmn), M8 -- exposes decisionTableReferenceKey on a serviceTask
// flowable:type="dmn" (FlowableBpmnParserAdapter D1, backend already accepts this shape).
// No moddle extension descriptor is registered for the "flowable" namespace in this app (see
// BpmnEditorCanvas.vue header) -- flat flowable:* attributes above work via bpmn-moddle's generic
// "any attribute" handling, but a *child element* like <flowable:field><flowable:string>...
// </flowable:string></flowable:field> needs Moddle#createAny (not bpmnFactory.create, which only
// constructs registered types) to build a serializable generic element tree. Proven by two real
// round-trip failures (E2E-EDIT-BPMN-5, not just a mocked unit test) before landing on the
// working shape: (1) the *container* bpmn:ExtensionElements must be the real registered type via
// bpmnFactory.create, not createAny -- only its schema-declared "values" property is recognized
// as child elements; (2) each createAny() child's own nested children must be assigned to
// "$children" specifically (moddle-xml's ElementSerializer.collectGenericContainments convention
// for generic elements), not an arbitrary property name like "values" -- anything else is
// serialized as a plain XML attribute (stringified), silently producing
// values="[object Object]" instead of a real nested element.
function DecisionTableReferenceKey(props: EntryProps) {
  const { element } = props
  const modeling = useService<{
    updateModdleProperties: (el: unknown, mo: unknown, props: Record<string, unknown>) => void
  }>('modeling')
  const moddle = useService<ModdleService>('moddle')
  const bpmnFactory = useService<{ create: (type: string, attrs?: Record<string, unknown>) => ExtensionElementsModdle }>(
    'bpmnFactory',
  )
  const debounce = useService<Debounce>('debounceInput')

  function findField(): AnyModdleElement | null {
    const extensionElements = element.businessObject.get('extensionElements') as
      | ExtensionElementsModdle
      | undefined
    const values = extensionElements?.get('values') ?? []
    return (
      values.find(
        (value) => value.$type === 'flowable:field' && value.name === DECISION_TABLE_REFERENCE_KEY_FIELD,
      ) ?? null
    )
  }

  const getValue = () => {
    const field = findField()
    const stringChild = field?.$children?.find((value) => value.$type === 'flowable:string')
    return stringChild?.$body ?? ''
  }

  const setValue = (value: string | undefined) => {
    let extensionElements = element.businessObject.get('extensionElements') as
      | ExtensionElementsModdle
      | undefined
    if (!extensionElements) {
      // bpmn:ExtensionElements *is* a registered BPMN type (unlike flowable:field/flowable:string
      // below) -- its schema declares "values" as a child-element array, which is what makes
      // moddle-xml serialize real <extensionElements>...</extensionElements> children instead of
      // stringifying the array as a single attribute (verified by a real round-trip failure: using
      // moddle.createAny for this container produced values="[object Object]").
      extensionElements = bpmnFactory.create('bpmn:ExtensionElements', { values: [] })
    }
    const remaining = (extensionElements.get('values') ?? []).filter(
      (existing) =>
        !(existing.$type === 'flowable:field' && existing.name === DECISION_TABLE_REFERENCE_KEY_FIELD),
    )
    if (value) {
      const stringChild = moddle.createAny('flowable:string', FLOWABLE_NS_URI, { $body: value })
      const field = moddle.createAny('flowable:field', FLOWABLE_NS_URI, {
        name: DECISION_TABLE_REFERENCE_KEY_FIELD,
        $children: [stringChild],
      })
      remaining.push(field)
    }
    extensionElements.set('values', remaining)
    modeling.updateModdleProperties(element, element.businessObject, { extensionElements })
  }

  return TextFieldEntry({
    element,
    id: 'flowable:decisionTableReferenceKey',
    label: 'Clé de la table de décision',
    getValue,
    setValue,
    debounce,
  })
}

function serviceTaskGroup(shouldOpen: boolean) {
  return {
    id: 'flowable-service-task',
    label: 'Avancé',
    shouldOpen,
    entries: [
      { id: 'flowable-async', component: Async, isEdited: isCheckboxEntryEdited },
      {
        id: 'flowable:delegateExpression',
        component: DelegateExpression,
        isEdited: isTextFieldEntryEdited,
      },
      { id: 'flowable:expression', component: Expression, isEdited: isTextFieldEntryEdited },
      { id: 'flowable:type', component: Type, isEdited: isTextFieldEntryEdited },
      {
        id: 'flowable:decisionTableReferenceKey',
        component: DecisionTableReferenceKey,
        isEdited: isTextFieldEntryEdited,
      },
    ],
  }
}

function signalGroup() {
  return {
    id: 'flowable-signal',
    label: 'Avancé',
    entries: [{ id: 'flowable:scope', component: Scope, isEdited: isTextFieldEntryEdited }],
  }
}

// TODO §52 (formulaires-dynamiques-taches) : flowable:formKey déclare qu'un userTask attend un
// formulaire de tâche avant complétion. Convention FlowForge interprétée uniquement côté frontend
// (taskFormRegistry.ts) — le moteur ne fait que stocker/restituer cette chaîne opaque.
function userTaskGroup(shouldOpen: boolean) {
  return {
    id: 'flowable-user-task',
    label: 'Avancé',
    shouldOpen,
    entries: [
      { id: 'flowable:formKey', component: FormKey, isEdited: isTextFieldEntryEdited },
      { id: 'flowable:portalRecipient', component: PortalRecipient, isEdited: isTextFieldEntryEdited },
      { id: 'flowable:portalLinkTtlDays', component: PortalLinkTtlDays, isEdited: isTextFieldEntryEdited },
    ],
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
      // TODO §73 : section « Avancé » (entrées brutes) repliée pour une brique reconnue, ouverte sinon (S-UI-4/5/8).
      const shouldOpen = !recognizeBlock(element.businessObject as never, getBlocks())
      if (is(element, 'bpmn:ServiceTask')) {
        groups.push(serviceTaskGroup(shouldOpen))
      }
      if (is(element, 'bpmn:Signal')) {
        groups.push(signalGroup())
      }
      if (is(element, 'bpmn:UserTask')) {
        groups.push(userTaskGroup(shouldOpen))
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
