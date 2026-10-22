/**
 * Paramètre `fieldMapping` de l'étape « Renseigner des champs du dossier » : une ligne `champ = expression` par champ cible
 * (même lecture que `FieldMappingSpec` côté serveur — le séparateur est le premier `=` qui n'est pas un `==`).
 */
export interface MappingRow {
  field: string
  expression: string
}

const LINE = /^([^=]*?)\s*=(?!=)\s*(.*)$/

export function parseFieldMapping(raw: string | null | undefined): MappingRow[] {
  if (!raw) return []
  return raw
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line !== '')
    .map((line) => {
      const m = LINE.exec(line)
      return m ? { field: m[1].trim(), expression: m[2].trim() } : { field: line, expression: '' }
    })
}

export function serializeFieldMapping(rows: MappingRow[]): string {
  return rows
    .map((r) => ({ field: r.field.trim(), expression: r.expression.trim() }))
    .filter((r) => r.field !== '' || r.expression !== '')
    .map((r) => `${r.field} = ${r.expression}`)
    .join('\n')
}

/** Insère `snippet` à la place de la sélection [start, end[, en l'espaçant s'il collerait à un mot ou un opérateur. */
export function insertAt(text: string, start: number, end: number, snippet: string): { text: string; caret: number } {
  const before = text.slice(0, start)
  const after = text.slice(end)
  const padBefore = before !== '' && !/[\s([]$/.test(before) ? ' ' : ''
  const padAfter = after !== '' && !/^[\s)\],]/.test(after) ? ' ' : ''
  const inserted = padBefore + snippet + padAfter
  return { text: before + inserted + after, caret: before.length + padBefore.length + snippet.length }
}
