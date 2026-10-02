import type { FieldDefinition } from '@/api/types'

/** TODO §81 (D2) — un champ de saisie de filtre sur un champ dynamique « cherchable ». */
export interface FieldFilterInput {
  /** Clé de l'API sans le préfixe `f.` : `contrat`, `montant.gte`, `echeance.lte`… */
  key: string
  label: string
  kind: 'text' | 'number' | 'date' | 'select' | 'boolean'
  options?: string[]
}

/** Même plafond que le serveur (D-RECH-GUARD). */
export const MAX_FIELD_FILTERS = 5

const SEARCHABLE = new Set(['STRING', 'NUMBER', 'DATE', 'BOOLEAN', 'SELECT'])

/** Entrées de filtre proposées pour un type : seuls les champs déclarés cherchables, jamais un champ sensible. */
export function fieldFilterInputs(definitions: FieldDefinition[] | undefined): FieldFilterInput[] {
  const inputs: FieldFilterInput[] = []
  for (const def of definitions ?? []) {
    if (!def.searchable || def.sensitive || !SEARCHABLE.has(def.type)) continue
    const label = def.label || def.name
    switch (def.type) {
      case 'STRING':
        inputs.push({ key: `${def.name}.prefix`, label: `${label} (commence par)`, kind: 'text' })
        break
      case 'SELECT':
        inputs.push({ key: def.name, label, kind: 'select', options: def.options ?? [] })
        break
      case 'BOOLEAN':
        inputs.push({ key: def.name, label, kind: 'boolean' })
        break
      case 'NUMBER':
        inputs.push({ key: `${def.name}.gte`, label: `${label} (min.)`, kind: 'number' })
        inputs.push({ key: `${def.name}.lte`, label: `${label} (max.)`, kind: 'number' })
        break
      case 'DATE':
        inputs.push({ key: `${def.name}.gte`, label: `${label} (du)`, kind: 'date' })
        inputs.push({ key: `${def.name}.lte`, label: `${label} (au)`, kind: 'date' })
        break
    }
  }
  return inputs
}

/** Filtres renseignés (valeurs non vides, rognées), prêts pour l'API. */
export function activeFieldFilters(values: Record<string, string>): Record<string, string> {
  const out: Record<string, string> = {}
  for (const [key, raw] of Object.entries(values)) {
    const value = String(raw ?? '').trim()
    if (value) out[key] = value
  }
  return out
}

export function tooManyFieldFilters(values: Record<string, string>): boolean {
  return Object.keys(activeFieldFilters(values)).length > MAX_FIELD_FILTERS
}

/** Lit les paramètres `f.*` d'une query de route. */
export function fieldFiltersFromQuery(query: Record<string, unknown>): Record<string, string> {
  const out: Record<string, string> = {}
  for (const [key, value] of Object.entries(query)) {
    if (key.startsWith('f.') && typeof value === 'string') out[key.slice(2)] = value
  }
  return out
}
