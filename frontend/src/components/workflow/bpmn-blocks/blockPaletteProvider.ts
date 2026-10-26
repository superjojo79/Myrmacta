import type { BuildingBlock } from './types'
import { getBlocks } from './blockCatalogHolder'
import { declareFlowableNamespace, type Moddled } from './blockValues'

// TODO §73 — groupe « Briques métier » de la palette bpmn-js. La palette native reste intégralement disponible
// (D-PALETTE-NATIVE) ; une brique n'est qu'un élément pré-configuré (D-PALETTE-GRANULARITY).

interface ModdleElement {
  set(name: string, value: unknown): void
  name?: string
}
interface Shape {
  businessObject: ModdleElement
}
interface ElementFactory {
  createShape(attrs: { type: string }): Shape
}
interface Create {
  start(event: unknown, shape: Shape): void
}
interface PaletteEntry {
  group: string
  className: string
  title: string
  action: { dragstart: (event: unknown) => void; click: (event: unknown) => void }
}
interface Palette {
  registerProvider(provider: unknown): void
}

const GROUP = 'business-blocks'

const ICONS: Record<BuildingBlock['element'], string> = {
  SERVICE_TASK: 'bpmn-icon-service-task',
  USER_TASK: 'bpmn-icon-user-task',
}

/** Crée l'élément BPMN pré-configuré d'une brique : type, nom, marqueur XML de reconnaissance. */
export function createBlockShape(block: BuildingBlock, elementFactory: ElementFactory): Shape {
  const shape = elementFactory.createShape({
    type: block.element === 'USER_TASK' ? 'bpmn:UserTask' : 'bpmn:ServiceTask',
  })
  const bo = shape.businessObject
  bo.name = block.label
  const marker = block.marker
  if (marker.delegate) bo.set('flowable:delegateExpression', `\${${marker.delegate}}`)
  if (marker.serviceTaskType) bo.set('flowable:type', marker.serviceTaskType)
  if (marker.formKeyPrefix) bo.set('flowable:formKey', marker.formKeyPrefix)
  // Vide à la création : la présence de l'attribut suffit à reconnaître la tâche externe (recognizeBlock).
  if (marker.portalRecipient) bo.set('flowable:portalRecipient', '')
  // TODO §106 (D-PRECHECK-BLOCK) — brique refusée au déploiement si elle n'est pas asynchrone.
  if (marker.async) bo.set('flowable:async', 'true')
  return shape
}

export class BlockPaletteProvider {
  static $inject = ['palette', 'create', 'elementFactory', 'bpmnjs']

  private readonly create: Create
  private readonly elementFactory: ElementFactory
  private readonly bpmnjs?: { getDefinitions(): Moddled | undefined }

  constructor(
    palette: Palette,
    create: Create,
    elementFactory: ElementFactory,
    bpmnjs?: { getDefinitions(): Moddled | undefined },
  ) {
    this.create = create
    this.elementFactory = elementFactory
    this.bpmnjs = bpmnjs
    palette.registerProvider(this)
  }

  getPaletteEntries(): Record<string, PaletteEntry> {
    const entries: Record<string, PaletteEntry> = {}
    for (const block of getBlocks()) {
      const start = (event: unknown) => {
        // Revue I3 : sans xmlns:flowable sur `definitions`, saveXML() supprimerait le marqueur de la brique.
        const definitions = this.bpmnjs?.getDefinitions()
        if (definitions) declareFlowableNamespace(definitions)
        this.create.start(event, createBlockShape(block, this.elementFactory))
      }
      entries[`create-block-${block.id}`] = {
        group: GROUP,
        className: `${ICONS[block.element]} fb-palette-block`,
        title: `${block.label} — ${block.description}`,
        action: { dragstart: start, click: start },
      }
    }
    return entries
  }
}

export const BlockPaletteModule = {
  __init__: ['blockPaletteProvider'],
  blockPaletteProvider: ['type', BlockPaletteProvider],
}
