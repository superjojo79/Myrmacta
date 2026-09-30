import type { BuildingBlock } from './types'
import { resetValueSourceCache } from './valueSourceResolver'

// Le Modeler bpmn-js instancie ses modules de façon synchrone : le catalogue est donc chargé avant sa
// création (BpmnEditorCanvas) puis déposé ici. Vide ⇒ les providers de briques ne s'activent pas
// (repli sur l'éditeur brut, D-PALETTE-FALLBACK).
let blocks: BuildingBlock[] = []
let processKey: string | null = null

export function setBlockCatalog(next: BuildingBlock[]): void {
  blocks = next
  resetValueSourceCache()
}

export function getBlocks(): BuildingBlock[] {
  return blocks
}

/** Clé du process ouvert (D-PALETTE-STATUSES : les statuts proposés viennent de sa configuration). */
export function setOpenProcessKey(next: string | null): void {
  processKey = next
}

export function getOpenProcessKey(): string | null {
  return processKey
}
