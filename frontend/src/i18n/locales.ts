/**
 * TODO §90 (D7) — langues de l'interface : français (langue d'origine et de repli) et anglais. Sans dépendance au SPA interne
 * (store, routeur, axios) : le portail public embarque ce module tel quel.
 */
export const SUPPORTED_LOCALES = ['fr', 'en'] as const
export type AppLocale = (typeof SUPPORTED_LOCALES)[number]
export const DEFAULT_LOCALE: AppLocale = 'fr'
export const LOCALE_STORAGE_KEY = 'myrmacta.locale'
/** Ancienne clé, lue une fois puis recopiée. */
export const LEGACY_LOCALE_STORAGE_KEY = 'flowforge.locale' // flowforge-legacy

/** Lit le choix stocké. Si seule l'ancienne clé est présente, la recopie et la retire. */
export function readStoredLocale(storage: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>): string | null {
  try {
    const current = storage.getItem(LOCALE_STORAGE_KEY)
    if (current) return current
    const legacy = storage.getItem(LEGACY_LOCALE_STORAGE_KEY)
    if (!legacy) return null
    storage.setItem(LOCALE_STORAGE_KEY, legacy)
    storage.removeItem(LEGACY_LOCALE_STORAGE_KEY)
    return legacy
  } catch {
    return null
  }
}

/** BCP 47 utilisé pour les formats de dates et de nombres. */
export const FORMAT_TAGS: Record<AppLocale, string> = { fr: 'fr-FR', en: 'en-GB' }

/** {@code en-US}, {@code FR_ca}, {@code fr} → langue gérée ; {@code null} si non gérée. */
export function normalizeLocale(tag: string | null | undefined): AppLocale | null {
  if (!tag) return null
  const language = tag.trim().toLowerCase().split(/[-_]/)[0]
  return (SUPPORTED_LOCALES as readonly string[]).includes(language) ? (language as AppLocale) : null
}

export interface LocaleSources {
  /** Choix explicite de l'utilisateur (conservé dans ce navigateur). */
  stored?: string | null
  /** {@code ?lang=} d'un lien reçu par e-mail : la langue du message, donc celle du tenant. */
  query?: string | null
  /** Langue par défaut du tenant ({@code /api/auth/me}). */
  tenantDefault?: string | null
  navigatorLanguages?: readonly string[]
}

/** Ordre : choix de l'utilisateur, lien, défaut du tenant, navigateur, français. Une valeur non gérée est ignorée, jamais fatale. */
export function resolveLocale(sources: LocaleSources): AppLocale {
  for (const candidate of [sources.stored, sources.query, sources.tenantDefault]) {
    const locale = normalizeLocale(candidate)
    if (locale) return locale
  }
  for (const tag of sources.navigatorLanguages ?? []) {
    const locale = normalizeLocale(tag)
    if (locale) return locale
  }
  return DEFAULT_LOCALE
}
