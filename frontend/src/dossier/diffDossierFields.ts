import type { FieldDefinition, FieldType } from '@/api/types'

function isBlank(type: FieldType | undefined, value: unknown): boolean {
  if (value === undefined || value === null) return true
  if (type === 'BOOLEAN') return false
  return value === ''
}

function coerceDraft(type: FieldType | undefined, value: unknown): unknown {
  if (type === 'NUMBER' && typeof value === 'string' && value !== '') {
    const n = Number(value)
    return Number.isNaN(n) ? value : n
  }
  return value
}

function valuesEqual(a: unknown, b: unknown): boolean {
  if (Object.is(a, b)) return true
  if (a == null && b == null) return true
  return false
}

export function diffDossierFields(
  snapshot: Record<string, unknown>,
  draft: Record<string, unknown>,
  definitions: FieldDefinition[] = [],
): Record<string, unknown> {
  const defByName = new Map(definitions.map((definition) => [definition.name, definition]))
  const names = new Set([
    ...Object.keys(snapshot),
    ...Object.keys(draft),
    ...definitions.map((definition) => definition.name),
  ])
  const patch: Record<string, unknown> = {}
  for (const name of names) {
    const type = defByName.get(name)?.type
    const before = snapshot[name]
    const after = coerceDraft(type, draft[name])
    const afterBlank = isBlank(type, after)
    const beforeBlank = isBlank(type, before)
    if (afterBlank && beforeBlank) continue
    if (afterBlank && !beforeBlank) {
      patch[name] = null
      continue
    }
    if (valuesEqual(before, after)) continue
    patch[name] = after
  }
  return patch
}
