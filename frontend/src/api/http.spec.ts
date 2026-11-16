import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import { apiContextPath, configureHttpClient, fieldErrorsOf, resolveApiBaseUrl, routerBase, schemaDuplicatesOf } from './http'

vi.mock('@/i18n', () => ({
  currentLocale: vi.fn(() => 'fr'),
}))

import { currentLocale } from '@/i18n'

describe('http client', () => {
  let previousBaseUrl: string | undefined

  beforeEach(() => {
    previousBaseUrl = axios.defaults.baseURL
    configureHttpClient()
  })

  afterEach(() => {
    axios.defaults.baseURL = previousBaseUrl
  })

  it('enables credentials and Spring CSRF cookie headers', () => {
    expect(axios.defaults.withCredentials).toBe(true)
    expect(axios.defaults.xsrfCookieName).toBe('XSRF-TOKEN')
    expect(axios.defaults.xsrfHeaderName).toBe('X-XSRF-TOKEN')
  })

  it('sends Accept-Language from the current UI locale (D-CONNECTOR-CATALOG-I18N)', async () => {
    vi.mocked(currentLocale).mockReturnValue('en')
    configureHttpClient()
    const headers = new Map<string, string>()
    const config = {
      headers: {
        set: (key: string, value: string) => {
          headers.set(key, value)
        },
      },
    }
    // Intercepteur enregistré par configureHttpClient : on le rejoue hors réseau.
    const interceptor = (
      axios.interceptors.request as unknown as {
        handlers: Array<{ fulfilled?: (value: typeof config) => unknown }>
      }
    ).handlers.find((handler) => handler?.fulfilled)
    expect(interceptor?.fulfilled).toBeTypeOf('function')
    await interceptor!.fulfilled!(config)
    expect(headers.get('Accept-Language')).toBe('en')
  })

  it('resolves API base from document baseURI under a context-path', () => {
    expect(resolveApiBaseUrl('https://example.com/tenant_a/app/')).toBe('/tenant_a')
    expect(resolveApiBaseUrl('https://example.com/')).toBe('')
  })

  it('routerBase is the context-path with a trailing slash (DD-BASE)', () => {
    expect(routerBase('https://example.com/tenant_a/')).toBe('/tenant_a/')
    expect(routerBase('https://example.com/')).toBe('/')
  })

  it('resolves from <base href>, never from the current nested route', () => {
    const base = document.createElement('base')
    document.head.appendChild(base)
    window.history.pushState({}, '', '/dossiers/new')
    try {
      base.setAttribute('href', '/')
      expect(resolveApiBaseUrl()).toBe('')
      expect(routerBase()).toBe('/')
      base.setAttribute('href', '/tenant_a/')
      expect(resolveApiBaseUrl()).toBe('/tenant_a')
      expect(routerBase()).toBe('/tenant_a/')
    } finally {
      base.remove()
      window.history.pushState({}, '', '/')
    }
  })

  it('extracts field errors (code and reference) and ignores a body without a usable errors array', () => {
    expect(
      fieldErrorsOf({
        detail: 'Dossier field validation failed',
        errors: [{ field: 'plaque', code: 'UNIQUE', message: 'Value already used by dossier DOS-9', reference: 'DOS-9' }],
      }),
    ).toEqual([{ field: 'plaque', code: 'UNIQUE', message: 'Value already used by dossier DOS-9', reference: 'DOS-9' }])
    expect(fieldErrorsOf({ errors: [{ field: 'a', code: 'REQUIRED', message: 'Field is required' }] })).toEqual([
      { field: 'a', code: 'REQUIRED', message: 'Field is required', reference: null },
    ])
    expect(fieldErrorsOf({ detail: 'type inactive' })).toEqual([])
    expect(fieldErrorsOf({ errors: 'nope' })).toEqual([])
    expect(fieldErrorsOf({ errors: [{ code: 'UNIQUE' }, null, { field: 'a', message: 'x' }] })).toEqual([])
  })

  it('extracts the duplicate values of a schema conflict and ignores anything else', () => {
    expect(
      schemaDuplicatesOf({
        detail: 'incompatible',
        duplicates: [{ field: 'plaque', value: '1ABC123', references: ['DOS-2', 'DOS-1'] }],
      }),
    ).toEqual([{ field: 'plaque', value: '1ABC123', references: ['DOS-2', 'DOS-1'] }])
    expect(schemaDuplicatesOf({ detail: 'conflit' })).toEqual([])
    expect(schemaDuplicatesOf({ duplicates: [{ field: 'a', value: 1, references: 'DOS-1' }] })).toEqual([])
  })

  it('apiContextPath uses the axios baseURL captured at boot', () => {
    axios.defaults.baseURL = '/acme'
    expect(apiContextPath()).toBe('/acme')
    axios.defaults.baseURL = '/acme/'
    expect(apiContextPath()).toBe('/acme')
    axios.defaults.baseURL = undefined
    expect(apiContextPath()).toBe('')
  })
})
