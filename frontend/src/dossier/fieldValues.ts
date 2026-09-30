import type { FieldDefinition, FieldType } from '@/api/types'

// TODO §76 (D1) — valeurs des champs structurés côté SPA : montant {amount, currency}, référence externe
// {system, id, label?}, groupe répétable (liste de lignes). Module pur, sans dépendance Vue.

export interface MoneyValue {
  amount: string
  currency: string
}

export interface ExternalRefValue {
  system: string
  id: string
  label?: string
}

export type Row = Record<string, unknown>

/** Types dont la valeur n'est pas un scalaire. */
export const STRUCTURED_TYPES: FieldType[] = ['MONEY', 'REPEATABLE_GROUP', 'EXTERNAL_REF']

export function isStructured(type: FieldType | undefined): boolean {
  return type !== undefined && STRUCTURED_TYPES.includes(type)
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === 'object' && !Array.isArray(value) ? (value as Record<string, unknown>) : null
}

function text(value: unknown): string {
  return value === undefined || value === null ? '' : String(value)
}

/** Vrai quand la saisie ne porte aucune information (le champ sera retiré du dossier). */
export function isBlankValue(def: Pick<FieldDefinition, 'type'> | undefined, value: unknown): boolean {
  if (value === undefined || value === null) return true
  switch (def?.type) {
    case 'BOOLEAN':
      return false
    case 'MONEY': {
      const m = asRecord(value)
      return !m || (text(m.amount).trim() === '' && text(m.currency).trim() === '')
    }
    case 'EXTERNAL_REF': {
      const r = asRecord(value)
      return !r || (text(r.system).trim() === '' && text(r.id).trim() === '' && text(r.label).trim() === '')
    }
    case 'REPEATABLE_GROUP':
      return !Array.isArray(value) || value.length === 0
    default:
      return value === ''
  }
}

/** Texte lisible d'une valeur (lecture seule, exports de debug) — jamais « [object Object] ». */
export function formatFieldValue(def: Pick<FieldDefinition, 'type'> | undefined, value: unknown): string {
  if (value === undefined || value === null) return ''
  switch (def?.type) {
    case 'MONEY': {
      const m = asRecord(value)
      return m ? `${text(m.amount)} ${text(m.currency)}`.trim() : ''
    }
    case 'EXTERNAL_REF': {
      const r = asRecord(value)
      if (!r) return ''
      const reference = `${text(r.system)} · ${text(r.id)}`
      return text(r.label) ? `${reference} — ${text(r.label)}` : reference
    }
    case 'REPEATABLE_GROUP': {
      const count = Array.isArray(value) ? value.length : 0
      return count === 1 ? '1 ligne' : `${count} lignes`
    }
    default:
      return String(value)
  }
}

function numericAmount(amount: unknown): unknown {
  const raw = text(amount).trim()
  return raw !== '' && !Number.isNaN(Number(raw)) ? Number(raw) : raw
}

/**
 * Forme comparable : « 12.5 » et « 12.50 » désignent le même montant, l'ordre des clés n'importe pas. Sans cela, un
 * champ structuré inchangé serait envoyé (et audité) comme modifié à chaque enregistrement.
 */
export function comparable(def: FieldDefinition | undefined, value: unknown): unknown {
  switch (def?.type) {
    case 'MONEY': {
      const m = asRecord(value)
      return m ? { amount: numericAmount(m.amount), currency: text(m.currency).trim() } : value
    }
    case 'EXTERNAL_REF': {
      const r = asRecord(value)
      return r ? { system: text(r.system).trim(), id: text(r.id).trim(), label: text(r.label).trim() } : value
    }
    case 'REPEATABLE_GROUP': {
      if (!Array.isArray(value)) return value
      const subs = new Map((def.fields ?? []).map((f) => [f.name, f]))
      return value.map((row) => {
        const r = asRecord(row)
        if (!r) return row
        const out: Record<string, unknown> = {}
        for (const key of Object.keys(r).sort()) {
          if (!isBlankValue(subs.get(key), r[key])) out[key] = comparable(subs.get(key), r[key])
        }
        return out
      })
    }
    default:
      return value
  }
}

export function deepEqual(a: unknown, b: unknown): boolean {
  if (Object.is(a, b)) return true
  if (a === null || b === null || typeof a !== 'object' || typeof b !== 'object') return false
  if (Array.isArray(a) !== Array.isArray(b)) return false
  if (Array.isArray(a) && Array.isArray(b)) {
    return a.length === b.length && a.every((item, i) => deepEqual(item, b[i]))
  }
  const ra = a as Record<string, unknown>
  const rb = b as Record<string, unknown>
  const keys = Object.keys(ra)
  return keys.length === Object.keys(rb).length && keys.every((k) => k in rb && deepEqual(ra[k], rb[k]))
}

/** Valeur à envoyer au serveur : les lignes entièrement vides sont retirées, les sous-champs vides aussi. */
export function toPayload(def: FieldDefinition, value: unknown): unknown {
  switch (def.type) {
    case 'MONEY': {
      const m = asRecord(value)
      return m ? { amount: text(m.amount).trim(), currency: text(m.currency).trim() } : value
    }
    case 'EXTERNAL_REF': {
      const r = asRecord(value)
      if (!r) return value
      const out: ExternalRefValue = { system: text(r.system).trim(), id: text(r.id).trim() }
      if (text(r.label).trim() !== '') out.label = text(r.label).trim()
      return out
    }
    case 'REPEATABLE_GROUP': {
      if (!Array.isArray(value)) return value
      const subs = new Map((def.fields ?? []).map((f) => [f.name, f]))
      return value
        .map((row) => {
          const r = asRecord(row)
          if (!r) return row
          const out: Record<string, unknown> = {}
          for (const [key, v] of Object.entries(r)) {
            const sub = subs.get(key)
            if (sub && !isBlankValue(sub, v)) out[key] = toPayload(sub, coerceScalar(sub, v))
          }
          return out
        })
        .filter((row) => Object.keys(row as object).length > 0)
    }
    default:
      return coerceScalar(def, value)
  }
}

/**
 * Champs prêts à créer un dossier : seuls les champs structurés sont nettoyés (vides retirés, lignes vides écartées,
 * valeurs rognées) ; les champs scalaires sont transmis tels quels, comme avant.
 */
export function cleanStructuredFields(definitions: FieldDefinition[], fields: Record<string, unknown>): Record<string, unknown> {
  const out = { ...fields }
  for (const def of definitions) {
    if (!isStructured(def.type) || !(def.name in out)) continue
    if (isBlankValue(def, out[def.name])) delete out[def.name]
    else out[def.name] = toPayload(def, out[def.name])
  }
  return out
}

function coerceScalar(def: FieldDefinition, value: unknown): unknown {
  if (def.type === 'NUMBER' && typeof value === 'string' && value !== '') {
    const n = Number(value)
    return Number.isNaN(n) ? value : n
  }
  return value
}
