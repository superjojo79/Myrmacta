import type { PieceType } from '@/api/types'

/** TODO §105 — mêmes règles que `PieceType` côté serveur (le serveur reste la source de vérité). */
export const PIECE_TYPE_KEY = /^[a-z][a-zA-Z0-9_]{0,59}$/
export const MAX_PIECE_TYPES = 30
export const MAX_PIECE_TYPE_LABEL = 100

export type PieceTypeError =
  | { code: 'tooMany' }
  | { code: 'key'; key: string }
  | { code: 'duplicate'; key: string }
  | { code: 'label'; key: string }

export function validatePieceTypes(pieceTypes: PieceType[]): PieceTypeError[] {
  const errors: PieceTypeError[] = []
  if (pieceTypes.length > MAX_PIECE_TYPES) errors.push({ code: 'tooMany' })
  const seen = new Set<string>()
  for (const type of pieceTypes) {
    if (!PIECE_TYPE_KEY.test(type.key)) {
      errors.push({ code: 'key', key: type.key })
    } else if (seen.has(type.key)) {
      errors.push({ code: 'duplicate', key: type.key })
    }
    seen.add(type.key)
    const label = type.label.trim()
    if (label.length === 0 || label.length > MAX_PIECE_TYPE_LABEL) errors.push({ code: 'label', key: type.key })
  }
  return errors
}
