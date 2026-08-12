import { describe, it, expect, beforeEach } from 'vitest'
import axios from 'axios'
import { configureHttpClient, resolveApiBaseUrl } from './http'

describe('http client', () => {
  beforeEach(() => {
    configureHttpClient()
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
})
