/**
 * TODO §84 (LC4b) — variables proposées par l'éditeur de templates, déduites des variables d'exemple de l'aperçu (JSON).
 * Un template est rendu avec les variables du processus au moment de la génération : l'éditeur ne peut pas les deviner, il propose
 * celles de l'exemple et laisse saisir n'importe quel chemin.
 */
export interface VariableCatalog {
  /** Chemins de valeurs simples : `client.nom`. */
  scalars: string[]
  /** Listes répétables : `lignes` avec les champs d'un élément (`designation`, `prix`…). */
  lists: Array<{ path: string; itemFields: string[] }>
}

const MAX_DEPTH = 5
const MAX_PATHS = 200

export function buildVariableCatalog(sample: unknown): VariableCatalog {
  const catalog: VariableCatalog = { scalars: [], lists: [] }
  walk(sample, '', 0, catalog)
  catalog.scalars = [...new Set(catalog.scalars)].sort()
  catalog.lists.sort((a, b) => a.path.localeCompare(b.path))
  return catalog
}

function walk(value: unknown, path: string, depth: number, out: VariableCatalog): void {
  if (out.scalars.length + out.lists.length >= MAX_PATHS || depth > MAX_DEPTH) return
  if (Array.isArray(value)) {
    if (!path) return
    const first = value.find((v) => v !== null && typeof v === 'object' && !Array.isArray(v)) as Record<string, unknown> | undefined
    const itemFields = first ? Object.keys(first).filter(isIdentifier).sort() : []
    out.lists.push({ path, itemFields })
    return
  }
  if (value !== null && typeof value === 'object') {
    for (const [key, child] of Object.entries(value as Record<string, unknown>)) {
      if (isIdentifier(key)) walk(child, path ? `${path}.${key}` : key, depth + 1, out)
    }
    return
  }
  if (path) out.scalars.push(path)
}

// Lettres Unicode comme le moteur (Character.isLetter) : `client.prénom` est un chemin valide.
const IDENTIFIER = /^[\p{L}_][\p{L}\p{N}_]*$/u

/** Propriétés que le moteur refuse (SafeThymeleafExpressionSupport.BLOCKED_PROPERTIES) : refusées ici aussi, avant le rendu. */
const BLOCKED = new Set(['class', 'getclass', 'wait', 'notify', 'notifyall'])

/** Un segment de chemin que l'allowlist des expressions accepte. */
export function isIdentifier(name: string): boolean {
  return IDENTIFIER.test(name) && !BLOCKED.has(name.toLowerCase())
}

/** Un chemin de variable valide : `a`, `a.b.c` (jamais d'opérateur, d'appel ni d'indice). */
export function isVariablePath(path: string): boolean {
  return path.length > 0 && path.split('.').every(isIdentifier)
}
