import { describe, expect, it } from 'vitest'
import { LEGACY_LOCALE_STORAGE_KEY, LOCALE_STORAGE_KEY, normalizeLocale, readStoredLocale, resolveLocale } from './locales'

describe('normalizeLocale (D7)', () => {
  it('reduces a BCP 47 tag to a supported language, whatever the case or separator', () => {
    expect(normalizeLocale('en-US')).toBe('en')
    expect(normalizeLocale('FR_ca')).toBe('fr')
    expect(normalizeLocale(' en ')).toBe('en')
  })

  it('returns null for an unsupported, empty or absent language', () => {
    expect(normalizeLocale('de-DE')).toBeNull()
    expect(normalizeLocale('')).toBeNull()
    expect(normalizeLocale(null)).toBeNull()
    expect(normalizeLocale(undefined)).toBeNull()
  })
})

describe('resolveLocale (D7-DETECT order)', () => {
  it('the user choice wins over the e-mail link, the tenant default and the browser', () => {
    expect(resolveLocale({ stored: 'en', query: 'fr', tenantDefault: 'fr', navigatorLanguages: ['fr-FR'] })).toBe('en')
  })

  it('then the link, then the tenant default, then the browser, then French', () => {
    expect(resolveLocale({ query: 'en', tenantDefault: 'fr', navigatorLanguages: ['fr'] })).toBe('en')
    expect(resolveLocale({ tenantDefault: 'en', navigatorLanguages: ['fr'] })).toBe('en')
    expect(resolveLocale({ navigatorLanguages: ['de-DE', 'en-GB'] })).toBe('en')
    expect(resolveLocale({ navigatorLanguages: ['de-DE'] })).toBe('fr')
    expect(resolveLocale({})).toBe('fr')
  })

  it('copies the previous storage key once, then drops it', () => {
    const values = new Map<string, string>([[LEGACY_LOCALE_STORAGE_KEY, 'en']])
    const storage = {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => values.set(key, value),
      removeItem: (key: string) => values.delete(key),
    }
    expect(readStoredLocale(storage)).toBe('en')
    expect(values.get(LOCALE_STORAGE_KEY)).toBe('en')
    expect(values.has(LEGACY_LOCALE_STORAGE_KEY)).toBe(false)
    values.set(LEGACY_LOCALE_STORAGE_KEY, 'fr')
    expect(readStoredLocale(storage)).toBe('en')
    expect(values.get(LEGACY_LOCALE_STORAGE_KEY)).toBe('fr')
  })

  it('ignores an unsupported value instead of failing (a stale or hostile ?lang= never breaks the page)', () => {
    expect(resolveLocale({ stored: 'xx', query: '<script>', tenantDefault: 'de', navigatorLanguages: ['en'] })).toBe('en')
  })
})
