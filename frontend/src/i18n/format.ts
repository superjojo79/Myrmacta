import { currentLocale, formatTag } from './index'

/** TODO §90 (D7) — dates, nombres et montants dans la langue courante ; valeur absente ou illisible → tiret cadratin. */
const DASH = '—'

const DURATION_UNITS = {
  fr: { day: 'j', hour: 'h', minute: 'min' },
  en: { day: 'd', hour: 'h', minute: 'min' },
} as const

function toDate(value: string | number | Date | null | undefined): Date | null {
  if (value == null || value === '') return null
  const date = value instanceof Date ? value : new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

export function formatDateTime(value: string | number | Date | null | undefined, options?: Intl.DateTimeFormatOptions): string {
  const date = toDate(value)
  return date ? date.toLocaleString(formatTag(), options ?? { dateStyle: 'short', timeStyle: 'short' }) : DASH
}

export function formatDate(value: string | number | Date | null | undefined, options?: Intl.DateTimeFormatOptions): string {
  const date = toDate(value)
  return date ? date.toLocaleDateString(formatTag(), options ?? { dateStyle: 'short' }) : DASH
}

export function formatNumber(value: number | null | undefined, options?: Intl.NumberFormatOptions): string {
  return value == null || Number.isNaN(value) ? DASH : new Intl.NumberFormat(formatTag(), options).format(value)
}

/**
 * Durée restante à partir d'heures fractionnaires (ex. SLA) — jamais le double brut.
 * ≥ 24 h : jours + heures entières (minutes omises) ; sinon heures + minutes arrondies.
 */
export function formatDurationHours(hours: number | null | undefined): string {
  if (hours == null || !Number.isFinite(hours) || hours <= 0) return DASH
  const units = DURATION_UNITS[currentLocale()]
  const parts: string[] = []
  if (hours >= 24) {
    const wholeHours = Math.floor(hours)
    const days = Math.floor(wholeHours / 24)
    const remHours = wholeHours % 24
    parts.push(`${days} ${units.day}`)
    if (remHours > 0) parts.push(`${remHours} ${units.hour}`)
    return parts.join(' ')
  }
  const totalMinutes = Math.max(1, Math.round(hours * 60))
  const remHours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  if (remHours > 0) parts.push(`${remHours} ${units.hour}`)
  if (minutes > 0) parts.push(`${minutes} ${units.minute}`)
  return parts.join(' ')
}

/** Montant décimal exact (chaîne) et devise ISO 4217 — jamais d'arrondi binaire : la chaîne est passée telle quelle au formateur. */
export function formatMoney(amount: string | null | undefined, currency: string | null | undefined): string {
  if (!amount || !currency) return DASH
  try {
    return new Intl.NumberFormat(formatTag(), { style: 'currency', currency }).format(amount as unknown as number)
  } catch {
    return `${amount} ${currency}`
  }
}
