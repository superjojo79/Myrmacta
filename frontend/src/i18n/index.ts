import { computed, type ComputedRef } from 'vue'
import { createI18n } from 'vue-i18n'
import en from './messages/en'
import fr from './messages/fr'
import {
  DEFAULT_LOCALE,
  FORMAT_TAGS,
  LOCALE_STORAGE_KEY,
  SUPPORTED_LOCALES,
  normalizeLocale,
  resolveLocale,
  type AppLocale,
} from './locales'

export { DEFAULT_LOCALE, SUPPORTED_LOCALES, type AppLocale } from './locales'

function readStored(): string | null {
  try {
    return window.localStorage.getItem(LOCALE_STORAGE_KEY)
  } catch {
    return null // stockage bloqué (fenêtre privée, politique) : la langue vient alors du lien, du tenant ou du navigateur
  }
}

function writeStored(locale: AppLocale | null): void {
  try {
    if (locale) window.localStorage.setItem(LOCALE_STORAGE_KEY, locale)
    else window.localStorage.removeItem(LOCALE_STORAGE_KEY)
  } catch {
    /* sans stockage, le choix vaut pour la session seulement */
  }
}

function queryLang(): string | null {
  try {
    return new URLSearchParams(window.location.search).get('lang')
  } catch {
    return null
  }
}

function initial(): AppLocale {
  return resolveLocale({
    stored: readStored(),
    query: queryLang(),
    navigatorLanguages: typeof navigator === 'undefined' ? [] : [...(navigator.languages ?? []), navigator.language],
  })
}

export const i18n = createI18n({
  legacy: false,
  locale: initial(),
  fallbackLocale: DEFAULT_LOCALE,
  messages: { fr, en },
  missingWarn: import.meta.env?.DEV ?? false,
  fallbackWarn: false,
})

function syncDocument(locale: AppLocale): void {
  if (typeof document !== 'undefined') document.documentElement.lang = locale
}
syncDocument(i18n.global.locale.value as AppLocale)

export function currentLocale(): AppLocale {
  return i18n.global.locale.value as AppLocale
}

/** Choix explicite de l'utilisateur : conservé, et prioritaire sur le défaut du tenant. */
export function setLocale(locale: AppLocale): void {
  i18n.global.locale.value = locale
  writeStored(locale)
  syncDocument(locale)
}

/** Défaut du tenant ({@code /api/auth/me}) : ne prend effet que si l'utilisateur n'a rien choisi. */
export function applyTenantDefault(tenantDefault: string | null | undefined): void {
  if (normalizeLocale(readStored())) return
  const locale = normalizeLocale(tenantDefault)
  if (!locale) return
  i18n.global.locale.value = locale
  syncDocument(locale)
}

/** Efface le choix de l'utilisateur (retour au défaut du tenant ou du navigateur au prochain chargement). */
export function clearStoredLocale(): void {
  writeStored(null)
}

export function useLocale(): { locale: ComputedRef<AppLocale>; setLocale: typeof setLocale; supported: readonly AppLocale[] } {
  return { locale: computed(() => i18n.global.locale.value as AppLocale), setLocale, supported: SUPPORTED_LOCALES }
}

/** Dates et nombres dans la langue courante. */
export function formatTag(): string {
  return FORMAT_TAGS[currentLocale()]
}
