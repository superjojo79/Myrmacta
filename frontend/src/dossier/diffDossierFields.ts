import type { FieldDefinition } from '@/api/types'
import { comparable, deepEqual, isBlankValue, toPayload } from './fieldValues'

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
    const def = defByName.get(name)
    const before = snapshot[name]
    // TODO §76 : les valeurs structurées (montant, référence, lignes) sont nettoyées avant d'être comparées puis envoyées.
    const after = def ? toPayload(def, draft[name]) : draft[name]
    const afterBlank = isBlankValue(def, after)
    const beforeBlank = isBlankValue(def, before)
    if (afterBlank && beforeBlank) continue
    if (afterBlank && !beforeBlank) {
      patch[name] = null
      continue
    }
    // « 12.5 » et « 12.50 » désignent le même montant : comparaison sur la forme canonique, pas sur la saisie.
    if (deepEqual(comparable(def, before), comparable(def, after))) continue
    patch[name] = after
  }
  return patch
}
