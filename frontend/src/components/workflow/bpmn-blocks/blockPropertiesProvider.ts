import {
  CheckboxEntry,
  ListGroup,
  SelectEntry,
  TextFieldEntry,
  isCheckboxEntryEdited,
  isSelectEntryEdited,
  isTextFieldEntryEdited,
} from '@bpmn-io/properties-panel'
import { useEffect, useState } from '@bpmn-io/properties-panel/preact/hooks'
import { useService } from 'bpmn-js-properties-panel'
import { is } from 'bpmn-js/lib/util/ModelUtil'
import type { Element as BpmnElement } from 'bpmn-js/lib/model/Types'
import { diagnoseParameter, priorityHint, type DiagnosticContext } from './blockDiagnostics'
import { getBlocks, getOpenProcessKey } from './blockCatalogHolder'
import { diagnosticContext } from './blockContext'
import {
  readSigners,
  readValue,
  toggleMulti,
  writeSigners,
  writeValue,
  type BlockEditorServices,
  type Moddled,
  type SignerValue,
} from './blockValues'
import { recognizeBlock } from './recognizeBlock'
import type { BlockParameter, BuildingBlock, ValueOption } from './types'
import { resolveValueSourceCached } from './valueSourceResolver'

// TODO §73 — groupe « Brique » du panneau de propriétés : champs typés du catalogue, insérés juste après « General »
// (D-PALETTE-PANEL). Le groupe existant « Flowable » est renommé « Avancé » et replié pour une brique reconnue.

const LOW_PRIORITY = 500
const AFTER_GROUP = 'general'

interface EntryProps {
  element: BpmnElement
}
type Debounce = <A extends unknown[]>(fn: (...args: A) => void) => (...args: A) => void

interface Registry {
  getAll(): Array<{ businessObject?: unknown }>
}

function useBlockServices(): BlockEditorServices {
  return {
    modeling: useService('modeling'),
    moddle: useService('moddle'),
    bpmnFactory: useService('bpmnFactory'),
  }
}

/** Message d'aide d'un paramètre : aide du catalogue, rappel de priorité, puis avertissements. */
function describe(param: BlockParameter, warnings: string[]): string {
  const parts = [param.help, priorityHint(param), ...warnings.map((w) => `⚠ ${w}`)].filter(Boolean)
  return parts.join(' ')
}

function usePreparedEntry(props: EntryProps, block: BuildingBlock, param: BlockParameter) {
  const { element } = props
  const services = useBlockServices()
  const debounce = useService<Debounce>('debounceInput')
  const registry = useService<Registry>('elementRegistry')
  const bo = element.businessObject as unknown as Moddled
  const value = readValue(bo, block, param)
  const ctx: DiagnosticContext = diagnosticContext(registry)
  const warnings = diagnoseParameter(bo, block, param, ctx).map((d) => d.message)
  const setValue = (next: string | undefined) => writeValue(services, element, bo, block, param, next)
  return { element, value, setValue, debounce, description: describe(param, warnings), warnings }
}

function textEntry(block: BuildingBlock, param: BlockParameter) {
  return function BlockText(props: EntryProps) {
    const { element, value, setValue, debounce, description, warnings } = usePreparedEntry(props, block, param)
    return TextFieldEntry({
      element,
      id: `block-${param.name}`,
      label: param.label,
      description,
      getValue: () => value,
      setValue,
      debounce,
      validate: (v: string) => rangeError(param, v) ?? warnings[0],
    })
  }
}

function rangeError(param: BlockParameter, raw: string): string | undefined {
  if (!raw) return undefined
  if (param.type === 'INTEGER') {
    const n = Number(raw)
    if (!Number.isInteger(n) || n < (param.min ?? 0) || n > (param.max ?? Number.MAX_SAFE_INTEGER)) {
      return `Entier entre ${param.min} et ${param.max} attendu.`
    }
  }
  if (raw.length > param.maxLength) return `${param.maxLength} caractères au plus.`
  return undefined
}

function enumEntry(block: BuildingBlock, param: BlockParameter) {
  return function BlockEnum(props: EntryProps) {
    const { element, value, setValue, description } = usePreparedEntry(props, block, param)
    return SelectEntry({
      element,
      id: `block-${param.name}`,
      label: param.label,
      description,
      getValue: () => value,
      setValue,
      getOptions: () => [
        { value: '', label: '— non renseigné —' },
        // Revue I4 : une valeur importée hors liste reste visible (et signalée) au lieu d'apparaître « non renseigné ».
        ...(value && !param.enumValues.includes(value) ? [{ value, label: `${value} (invalide)` }] : []),
        ...param.enumValues.map((v) => ({ value: v, label: v })),
      ],
    })
  }
}

function multiEntry(block: BuildingBlock, param: BlockParameter, option: string) {
  return function BlockMulti(props: EntryProps) {
    const { element, value, setValue, description } = usePreparedEntry(props, block, param)
    return CheckboxEntry({
      element,
      id: `block-${param.name}-${option}`,
      label: `${param.label} : ${option}`,
      description: option === param.enumValues[0] ? description : undefined,
      getValue: () => value.split(',').includes(option),
      setValue: (checked: boolean) => setValue(toggleMulti(value, option, checked, param.enumValues) || undefined),
    })
  }
}

function referenceEntry(block: BuildingBlock, param: BlockParameter) {
  return function BlockReference(props: EntryProps) {
    const { element, value, setValue, debounce, description } = usePreparedEntry(props, block, param)
    const [options, setOptions] = useState<ValueOption[] | null | undefined>(undefined)
    useEffect(() => {
      let alive = true
      resolveValueSourceCached(param.valueSource as string, { processKey: getOpenProcessKey() }).then((o) => {
        if (alive) setOptions(o)
      })
      return () => {
        alive = false
      }
    }, [element.id])
    // Source non listable ou indisponible : saisie libre, l'existence est vérifiée au déploiement.
    if (!options) {
      const hint =
        param.valueSource === 'workflowStatuses'
          ? 'Aucune configuration de workflow pour ce process : saisie libre, statut vérifié au déploiement.'
          : 'Liste indisponible : saisie libre, existence vérifiée au déploiement.'
      return TextFieldEntry({
        element,
        id: `block-${param.name}`,
        label: param.label,
        description: `${description} ${hint}`.trim(),
        getValue: () => value,
        setValue,
        debounce,
      })
    }
    return SelectEntry({
      element,
      id: `block-${param.name}`,
      label: param.label,
      description,
      getValue: () => value,
      setValue,
      getOptions: () => {
        const known = options.some((o) => o.value === value)
        return [
          { value: '', label: '— non renseigné —' },
          ...(value && !known ? [{ value, label: `${value} (introuvable)` }] : []),
          ...options.map((o) => ({ value: o.value, label: o.label })),
        ]
      },
    })
  }
}

// --- Signataires (SIGNER_ELEMENTS) -----------------------------------------------------------------------------

function signerEntries(index: number, field: keyof SignerValue) {
  const labels: Record<keyof SignerValue, string> = {
    source: 'Source (dossierField:<champ> ou variable:<nom>)',
    kind: 'Type',
    required: 'Obligatoire',
    name: 'Nom affiché (facultatif)',
  }
  return function SignerField(props: EntryProps) {
    const { element } = props
    const services = useBlockServices()
    const debounce = useService<Debounce>('debounceInput')
    const bo = element.businessObject as unknown as Moddled
    const signers = readSigners(bo)
    const current = signers[index]
    const update = (patch: Partial<SignerValue>) =>
      writeSigners(services, element, bo, signers.map((s, i) => (i === index ? { ...s, ...patch } : s)))
    const id = `block-signer-${index}-${field}`
    if (field === 'required') {
      return CheckboxEntry({
        element,
        id,
        label: labels.required,
        getValue: () => current?.required ?? true,
        setValue: (v: boolean) => update({ required: v }),
      })
    }
    if (field === 'kind') {
      return SelectEntry({
        element,
        id,
        label: labels.kind,
        getValue: () => current?.kind ?? 'EXTERNAL',
        setValue: (v: string) => update({ kind: v }),
        getOptions: () => [
          { value: 'EXTERNAL', label: 'Externe (portail)' },
          { value: 'INTERNAL', label: 'Interne (utilisateur)' },
        ],
      })
    }
    return TextFieldEntry({
      element,
      id,
      label: labels[field],
      getValue: () => (current?.[field] as string) ?? '',
      setValue: (v: string | undefined) => update({ [field]: v ?? '' }),
      debounce,
    })
  }
}

function signersGroup(element: BpmnElement, services: BlockEditorServices | null) {
  const bo = element.businessObject as unknown as Moddled
  const signers = readSigners(bo)
  return {
    id: 'block-signers',
    label: 'Signataires',
    component: ListGroup,
    shouldOpen: true,
    add: () => {
      if (!services) return
      writeSigners(services, element, bo, [
        ...readSigners(bo),
        { source: '', kind: 'EXTERNAL', required: true, name: '' },
      ])
    },
    items: signers.map((s, index) => ({
      id: `block-signer-${index}`,
      label: s.name || s.source || `Signataire ${index + 1}`,
      entries: (['source', 'kind', 'required', 'name'] as const).map((field) => ({
        id: `block-signer-${index}-${field}`,
        component: signerEntries(index, field),
        isEdited: field === 'required' ? isCheckboxEntryEdited : field === 'kind' ? isSelectEntryEdited : isTextFieldEntryEdited,
      })),
      remove: () => {
        if (!services) return
        writeSigners(services, element, bo, readSigners(bo).filter((_, i) => i !== index))
      },
    })),
  }
}

// --- Groupe « Brique » -----------------------------------------------------------------------------------------

export function blockEntries(block: BuildingBlock) {
  const entries: Array<{ id: string; component: unknown; isEdited: unknown }> = []
  for (const param of block.parameters) {
    switch (param.type) {
      case 'SIGNERS':
        break // liste dédiée, hors de ce groupe
      case 'ENUM':
        entries.push({ id: `block-${param.name}`, component: enumEntry(block, param), isEdited: isSelectEntryEdited })
        break
      case 'ENUM_MULTI':
        for (const option of param.enumValues) {
          entries.push({
            id: `block-${param.name}-${option}`,
            component: multiEntry(block, param, option),
            isEdited: isCheckboxEntryEdited,
          })
        }
        break
      case 'REFERENCE':
        entries.push({ id: `block-${param.name}`, component: referenceEntry(block, param), isEdited: isSelectEntryEdited })
        break
      default:
        entries.push({ id: `block-${param.name}`, component: textEntry(block, param), isEdited: isTextFieldEntryEdited })
    }
  }
  return entries
}

export function blockGroups(element: BpmnElement, block: BuildingBlock, services: BlockEditorServices | null) {
  const groups: unknown[] = [
    { id: 'block', label: block.label, entries: blockEntries(block), shouldOpen: true },
  ]
  if (block.parameters.some((p) => p.type === 'SIGNERS')) groups.push(signersGroup(element, services))
  return groups
}

interface GroupLike {
  id: string
}

interface BlockPropertiesProviderInstance {
  getGroups: (element: BpmnElement) => (groups: GroupLike[]) => GroupLike[]
}

export default function BlockPropertiesProvider(
  this: BlockPropertiesProviderInstance,
  propertiesPanel: { registerProvider: (priority: number, provider: unknown) => void },
  injector: { get: (name: string, strict?: boolean) => unknown },
) {
  this.getGroups = (element: BpmnElement) => (groups: GroupLike[]) => {
    if (!is(element, 'bpmn:ServiceTask') && !is(element, 'bpmn:UserTask')) return groups
    const block = recognizeBlock(element.businessObject as never, getBlocks())
    if (!block) return groups
    const services: BlockEditorServices | null = (() => {
      try {
        return {
          modeling: injector.get('modeling') as BlockEditorServices['modeling'],
          moddle: injector.get('moddle') as BlockEditorServices['moddle'],
          bpmnFactory: injector.get('bpmnFactory') as BlockEditorServices['bpmnFactory'],
        }
      } catch {
        return null
      }
    })()
    const at = groups.findIndex((g) => g.id === AFTER_GROUP)
    groups.splice(at < 0 ? 0 : at + 1, 0, ...(blockGroups(element, block, services) as GroupLike[]))
    return groups
  }
  propertiesPanel.registerProvider(LOW_PRIORITY, this)
}

BlockPropertiesProvider.$inject = ['propertiesPanel', 'injector']

export const BlockPropertiesProviderModule = {
  __init__: ['blockPropertiesProvider'],
  blockPropertiesProvider: ['type', BlockPropertiesProvider],
}
