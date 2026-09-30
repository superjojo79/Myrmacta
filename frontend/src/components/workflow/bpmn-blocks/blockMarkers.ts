import { diagnoseBlock } from './blockDiagnostics'
import { getBlocks, getOpenProcessKey } from './blockCatalogHolder'
import { diagnosticContext } from './blockContext'
import type { Moddled } from './blockValues'
import { recognizeBlock } from './recognizeBlock'
import { resolveValueSourceCached } from './valueSourceResolver'

// TODO §73 — marqueur visuel (classe CSS `fb-block-warning`) sur les briques dont un paramètre appelle un
// avertissement. Non bloquant : n'agit que sur le rendu du canevas, jamais sur le XML (S-DIAG-4).

export const WARNING_MARKER = 'fb-block-warning'

interface DiagramElement {
  id: string
  businessObject?: Moddled
}
interface EventBus {
  on(events: string | string[], callback: () => void): void
}
interface Canvas {
  addMarker(element: string, marker: string): void
  removeMarker(element: string, marker: string): void
}
interface Registry {
  getAll(): DiagramElement[]
}

export class BlockMarkers {
  static $inject = ['eventBus', 'canvas', 'elementRegistry']

  private readonly canvas: Canvas
  private readonly registry: Registry
  private prefetching = false

  constructor(eventBus: EventBus, canvas: Canvas, registry: Registry) {
    this.canvas = canvas
    this.registry = registry
    eventBus.on('import.done', () => {
      this.refresh()
      void this.prefetchThenRefresh()
    })
    eventBus.on(['commandStack.changed', 'elements.changed'], () => this.refresh())
  }

  refresh(): void {
    const blocks = getBlocks()
    if (blocks.length === 0) return
    const ctx = diagnosticContext(this.registry)
    for (const element of this.registry.getAll()) {
      const bo = element.businessObject
      const block = bo ? recognizeBlock(bo as never, blocks) : null
      if (!bo) continue
      if (!block) {
        // Élément qui a cessé d'être une brique (ex. délégué modifié dans « Avancé ») : plus d'avertissement.
        if (bo.$type === 'bpmn:ServiceTask' || bo.$type === 'bpmn:UserTask') {
          this.canvas.removeMarker(element.id, WARNING_MARKER)
        }
        continue
      }
      const warn = diagnoseBlock(bo, block, ctx).length > 0
      if (warn) this.canvas.addMarker(element.id, WARNING_MARKER)
      else this.canvas.removeMarker(element.id, WARNING_MARKER)
    }
  }

  /** Charge les listes de références utilisées par les briques du diagramme, puis recalcule les marqueurs. */
  private async prefetchThenRefresh(): Promise<void> {
    if (this.prefetching) return
    this.prefetching = true
    try {
      const blocks = getBlocks()
      const sources = new Set<string>()
      for (const element of this.registry.getAll()) {
        const block = element.businessObject ? recognizeBlock(element.businessObject as never, blocks) : null
        block?.parameters.forEach((p) => p.valueSource && sources.add(p.valueSource))
      }
      const processKey = getOpenProcessKey()
      await Promise.all([...sources].map((source) => resolveValueSourceCached(source, { processKey })))
      this.refresh()
    } finally {
      this.prefetching = false
    }
  }
}

export const BlockMarkersModule = {
  __init__: ['blockMarkers'],
  blockMarkers: ['type', BlockMarkers],
}
