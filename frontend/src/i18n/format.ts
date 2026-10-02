import { formatTag } from './index'

/** TODO §90 (D7) — dates, nombres et montants dans la langue courante ; valeur absente ou illisible → tiret cadratin. */
const DASH = '—'

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

/** Montant décimal exact (chaîne) et devise ISO 4217 — jamais d'arrondi binaire : la chaîne est passée telle quelle au formateur. */
export function formatMoney(amount: string | null | undefined, currency: string | null | undefined): string {
  if (!amount || !currency) return DASH
  try {
    return new Intl.NumberFormat(formatTag(), { style: 'currency', currency }).format(amount as unknown as number)
  } catch {
    return `${amount} ${currency}`
  }
}
