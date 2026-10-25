import type { BlockParameter, BuildingBlock } from './types'

// Lecture / écriture des valeurs d'une brique dans le modèle moddle (D-PALETTE-BINDING). Aucune dépendance UI :
// les entrées du panneau (blockPropertiesProvider) s'appuient sur ces fonctions, testées sur un vrai bpmn-moddle.

export const FLOWABLE_NS_URI = 'http://flowable.org/bpmn'
const FORM_KEY = 'flowable:formKey'

export interface Moddled {
  $type: string
  $parent?: Moddled
  $attrs?: Record<string, unknown>
  get(name: string): unknown
}

interface ModdleAny {
  $type: string
  name?: string
  $body?: string
  $children?: ModdleAny[]
  [key: string]: unknown
}

interface ExtensionElementsModdle {
  get(key: 'values'): ModdleAny[]
  set(key: 'values', value: ModdleAny[]): void
}

export interface BlockEditorServices {
  modeling: { updateModdleProperties(el: unknown, mo: unknown, props: Record<string, unknown>): void }
  moddle: { createAny(name: string, nsUri: string, properties?: Record<string, unknown>): ModdleAny }
  bpmnFactory: { create(type: string, attrs?: Record<string, unknown>): ExtensionElementsModdle }
}

export interface SignerValue {
  source: string
  kind: string
  required: boolean
  name: string
}

/**
 * D-FLOWABLE-NS : un diagramme importé sans `xmlns:flowable` perdrait silencieusement les attributs `flowable:*`
 * à l'export ; la déclaration est posée sur `definitions` avant toute écriture de brique.
 */
export function ensureFlowableNamespace(bo: Moddled): void {
  let node: Moddled | undefined = bo
  while (node && node.$type !== 'bpmn:Definitions') node = node.$parent
  if (node) declareFlowableNamespace(node)
}

/** Déclare `xmlns:flowable` sur `definitions` (revue I3 : aussi avant la création d'une brique depuis la palette). */
export function declareFlowableNamespace(node: Moddled): void {
  // $attrs est un accesseur moddle (objet créé à la demande) : on le mute, on ne l'affecte pas.
  const attrs = node.$attrs
  if (attrs && !attrs['xmlns:flowable']) attrs['xmlns:flowable'] = FLOWABLE_NS_URI
}

function extensionValues(bo: Moddled): ModdleAny[] {
  const ext = bo.get('extensionElements') as ExtensionElementsModdle | undefined
  return ext?.get('values') ?? []
}

function fieldValue(bo: Moddled, fieldName: string): string {
  const field = extensionValues(bo).find((v) => v.$type === 'flowable:field' && v.name === fieldName)
  return field?.$children?.find((c) => c.$type === 'flowable:string')?.$body ?? ''
}

function formKeyPrefix(block: BuildingBlock): string {
  return block.marker.formKeyPrefix ?? ''
}

export function readValue(bo: Moddled, block: BuildingBlock, param: BlockParameter): string {
  switch (param.binding) {
    case 'FIELD_ELEMENT':
      return fieldValue(bo, param.name)
    case 'FORM_KEY_SUFFIX': {
      const formKey = bo.get(FORM_KEY)
      const prefix = formKeyPrefix(block)
      return typeof formKey === 'string' && formKey.startsWith(prefix) ? formKey.slice(prefix.length) : ''
    }
    default: {
      const value = bo.get(`flowable:${param.name}`)
      return typeof value === 'string' ? value : ''
    }
  }
}

/** Écrit (ou retire, si vide) la valeur d'un paramètre ; jamais de chaîne vide dans le XML (S-UI-3). */
export function writeValue(
  services: BlockEditorServices,
  element: unknown,
  bo: Moddled,
  block: BuildingBlock,
  param: BlockParameter,
  value: string | undefined,
): void {
  ensureFlowableNamespace(bo)
  // Revue I2 : le déploiement refuse les espaces de bord ; on n'en écrit jamais (sauf le corps multi-lignes).
  if (value !== undefined && !(param.multiline ?? ['notificationBody', 'formula', 'fieldMapping', 'searchCriteria'].includes(param.name))) {
    value = value.trim()
  }
  const empty = value === undefined || value === ''
  switch (param.binding) {
    case 'FIELD_ELEMENT':
      writeField(services, element, bo, param.name, empty ? undefined : value)
      return
    case 'FORM_KEY_SUFFIX':
      // Le préfixe reste : il est le marqueur de reconnaissance de la brique.
      services.modeling.updateModdleProperties(element, bo, { [FORM_KEY]: formKeyPrefix(block) + (value ?? '') })
      return
    default: {
      // L'attribut portalRecipient, même vide, distingue la tâche externe d'une question utilisateur.
      const keepEmpty = block.marker.portalRecipient && param.name === 'portalRecipient'
      services.modeling.updateModdleProperties(element, bo, {
        [`flowable:${param.name}`]: empty && !keepEmpty ? undefined : (value ?? ''),
      })
    }
  }
}

function extensionContainer(services: BlockEditorServices, bo: Moddled): ExtensionElementsModdle {
  return (
    (bo.get('extensionElements') as ExtensionElementsModdle | undefined) ??
    services.bpmnFactory.create('bpmn:ExtensionElements', { values: [] })
  )
}

// Même construction que DecisionTableReferenceKey (flowablePropertiesProvider) : conteneur enregistré via
// bpmnFactory, enfants génériques via moddle.createAny avec `$children`.
function writeField(
  services: BlockEditorServices,
  element: unknown,
  bo: Moddled,
  fieldName: string,
  value: string | undefined,
): void {
  const container = extensionContainer(services, bo)
  const remaining = (container.get('values') ?? []).filter(
    (v) => !(v.$type === 'flowable:field' && v.name === fieldName),
  )
  if (value) {
    const stringChild = services.moddle.createAny('flowable:string', FLOWABLE_NS_URI, { $body: value })
    remaining.push(
      services.moddle.createAny('flowable:field', FLOWABLE_NS_URI, { name: fieldName, $children: [stringChild] }),
    )
  }
  container.set('values', remaining)
  services.modeling.updateModdleProperties(element, bo, { extensionElements: container })
}

export function readSigners(bo: Moddled): SignerValue[] {
  return extensionValues(bo)
    .filter((v) => v.$type === 'flowable:signer')
    .map((v) => ({
      source: String(v.source ?? ''),
      kind: String(v.kind ?? 'EXTERNAL'),
      required: v.required !== 'false' && v.required !== false,
      name: String(v.name ?? ''),
    }))
}

export function writeSigners(
  services: BlockEditorServices,
  element: unknown,
  bo: Moddled,
  signers: SignerValue[],
): void {
  ensureFlowableNamespace(bo)
  const container = extensionContainer(services, bo)
  const remaining = (container.get('values') ?? []).filter((v) => v.$type !== 'flowable:signer')
  for (const s of signers) {
    const attrs: Record<string, unknown> = { source: s.source, kind: s.kind, required: String(s.required) }
    if (s.name) attrs.name = s.name
    remaining.push(services.moddle.createAny('flowable:signer', FLOWABLE_NS_URI, attrs))
  }
  container.set('values', remaining)
  services.modeling.updateModdleProperties(element, bo, { extensionElements: container })
}

/** ENUM_MULTI : valeur `A,B` ; l'ordre suit celui du catalogue (sortie stable). */
export function toggleMulti(current: string, value: string, checked: boolean, allowed: string[]): string {
  const selected = new Set(current.split(',').filter(Boolean))
  if (checked) selected.add(value)
  else selected.delete(value)
  return allowed.filter((v) => selected.has(v)).join(',')
}
