import type { BuildingBlock } from './types'

// D-PALETTE-RECOGNITION : règles ordonnées, la première qui correspond l'emporte ; sinon `null` (élément brut).
// Délégués « propres » d'abord, puis `flowable:type="dmn"`, puis les tâches utilisateur.

interface Recognizable {
  $type: string
  get(name: string): unknown
}

function stripExpression(value: unknown): string | null {
  if (typeof value !== 'string') return null
  const match = /^\$\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*\}$/.exec(value.trim())
  return match ? match[1] : value.trim() || null
}

export function recognizeBlock(bo: Recognizable | undefined, blocks: BuildingBlock[]): BuildingBlock | null {
  if (!bo) return null
  const type = bo.$type
  if (type === 'bpmn:ServiceTask') {
    const delegate = stripExpression(bo.get('flowable:delegateExpression'))
    if (delegate) {
      return blocks.find((b) => b.element === 'SERVICE_TASK' && b.marker.delegate === delegate) ?? null
    }
    const flowableType = bo.get('flowable:type')
    if (typeof flowableType === 'string') {
      return blocks.find((b) => b.element === 'SERVICE_TASK' && b.marker.serviceTaskType === flowableType) ?? null
    }
    return null
  }
  if (type === 'bpmn:UserTask') {
    const formKey = bo.get('flowable:formKey')
    if (typeof formKey !== 'string') return null
    const candidates = blocks.filter((b) => b.element === 'USER_TASK' && b.marker.formKeyPrefix)
    const matching = candidates.filter((b) => formKey.startsWith(b.marker.formKeyPrefix as string))
    if (matching.length === 0) return null
    // Présence de l'attribut (même vide, à la création) : une tâche externe fraîchement posée reste reconnue.
    const wantsPortal = typeof bo.get('flowable:portalRecipient') === 'string'
    return matching.find((b) => b.marker.portalRecipient === wantsPortal) ?? null
  }
  return null
}
