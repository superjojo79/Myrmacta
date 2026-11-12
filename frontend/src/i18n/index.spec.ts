import { afterEach, describe, expect, it, vi } from 'vitest'
import { applyTenantDefault, clearStoredLocale, currentLocale, i18n, setLocale } from './index'
import { formatDate, formatDateTime, formatDurationHours, formatMoney, formatNumber } from './format'
import { LOCALE_STORAGE_KEY } from './locales'

describe('runtime locale (D7)', () => {
  afterEach(() => {
    clearStoredLocale()
    setLocale('fr')
    clearStoredLocale()
    vi.restoreAllMocks()
  })

  it('starts in French under a French browser (the setup fixes navigator.language)', () => {
    expect(currentLocale()).toBe('fr')
    expect(document.documentElement.lang).toBe('fr')
  })

  it('a user choice switches the messages, the <html lang> and survives in the browser', () => {
    setLocale('en')
    expect(currentLocale()).toBe('en')
    expect(document.documentElement.lang).toBe('en')
    expect(window.localStorage.getItem(LOCALE_STORAGE_KEY)).toBe('en')
    expect(i18n.global.t('common.save')).toBe('Save')
    setLocale('fr')
    expect(i18n.global.t('common.save')).toBe('Enregistrer')
  })

  it('the tenant default applies only while the user has not chosen', () => {
    applyTenantDefault('en')
    expect(currentLocale()).toBe('en')
    setLocale('fr')
    applyTenantDefault('en')
    expect(currentLocale()).toBe('fr')
  })

  it('an unsupported tenant default is ignored', () => {
    applyTenantDefault('de')
    applyTenantDefault(null)
    expect(currentLocale()).toBe('fr')
  })

  it('works when the storage is blocked (the choice then lasts for the session)', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    expect(() => setLocale('en')).not.toThrow()
    expect(currentLocale()).toBe('en')
    expect(() => applyTenantDefault('fr')).not.toThrow()
  })

  it('an unknown key falls back to the French text, then to the key — never a blank', () => {
    setLocale('en')
    expect(i18n.global.t('common.does.not.exist')).toBe('common.does.not.exist')
  })
})

describe('formats follow the language (D7)', () => {
  afterEach(() => {
    clearStoredLocale()
    setLocale('fr')
    clearStoredLocale()
  })

  it('formats a date in the day-first order in both languages, with the month spelled in the language', () => {
    expect(formatDate('2026-10-01T10:00:00Z', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' })).toBe('1 octobre 2026')
    setLocale('en')
    expect(formatDate('2026-10-01T10:00:00Z', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' })).toBe('1 October 2026')
  })

  it('formats numbers and money with the separators of the language, never rounding the exact decimal string', () => {
    expect(formatNumber(1234.5)).toBe('1 234,5'.replace(' ', ' '))
    expect(formatMoney('1234.50', 'EUR')).toContain('1')
    setLocale('en')
    expect(formatNumber(1234.5)).toBe('1,234.5')
    expect(formatMoney('1234.50', 'EUR')).toBe('€1,234.50')
  })

  it('a missing or unreadable value is a dash, a bad currency falls back to plain text', () => {
    expect(formatDate(null)).toBe('—')
    expect(formatDateTime('not a date')).toBe('—')
    expect(formatNumber(undefined)).toBe('—')
    expect(formatMoney('12', 'not-a-currency')).toBe('12 not-a-currency')
    expect(formatMoney(null, 'EUR')).toBe('—')
  })

  it('formats a remaining SLA duration without raw fractional hours (issue #67)', () => {
    expect(formatDurationHours(71.89438361111111)).toBe('2 j 23 h')
    expect(formatDurationHours(3.2)).toBe('3 h 12 min')
    expect(formatDurationHours(12)).toBe('12 h')
    expect(formatDurationHours(0.4)).toBe('24 min')
    expect(formatDurationHours(null)).toBe('—')
    expect(formatDurationHours(Number.POSITIVE_INFINITY)).toBe('—')
    setLocale('en')
    expect(formatDurationHours(71.89438361111111)).toBe('2 d 23 h')
    expect(formatDurationHours(3.2)).toBe('3 h 12 min')
    expect(formatDurationHours(12)).toBe('12 h')
  })
})
