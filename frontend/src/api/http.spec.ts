import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import axios from 'axios'
import { apiContextPath, configureHttpClient, resolveApiBaseUrl, routerBase } from './http'

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

  it('apiContextPath uses the axios baseURL captured at boot', () => {
    axios.defaults.baseURL = '/acme'
    expect(apiContextPath()).toBe('/acme')
    axios.defaults.baseURL = '/acme/'
    expect(apiContextPath()).toBe('/acme')
    axios.defaults.baseURL = undefined
    expect(apiContextPath()).toBe('')
  })
})
