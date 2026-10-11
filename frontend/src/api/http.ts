import axios, { type AxiosError } from 'axios'

export type ProblemDetail = {
  type?: string
  title?: string
  status?: number
  detail?: string
}

export function resolveApiBaseUrl(baseUri = globalThis.document?.baseURI ?? '/'): string {
  try {
    const url = new URL(baseUri, 'http://localhost/')
    const segments = url.pathname.split('/').filter(Boolean)
    if (segments.length === 0) {
      return ''
    }
    // Keep first path segment as tenant context-path when present (DD-BASE). baseURI comes from
    // <base href> (index.html, rewritten server-side to the context-path) — never the current route.
    return `/${segments[0]}`
  } catch {
    return ''
  }
}

/** Base of the router history: the tenant context-path ('/' when served at the root). */
export function routerBase(baseUri?: string): string {
  return `${resolveApiBaseUrl(baseUri)}/`
}

export function apiContextPath(): string {
  const fromClient = axios.defaults.baseURL
  if (typeof fromClient !== 'string' || fromClient === '') {
    return ''
  }
  return fromClient.replace(/\/$/, '')
}

export function configureHttpClient(): void {
  axios.defaults.withCredentials = true
  axios.defaults.xsrfCookieName = 'XSRF-TOKEN'
  axios.defaults.xsrfHeaderName = 'X-XSRF-TOKEN'
  const prefix = resolveApiBaseUrl()
  if (prefix) {
    axios.defaults.baseURL = prefix
  }
}

export function toProblemDetail(error: unknown): ProblemDetail {
  const axiosError = error as AxiosError<ProblemDetail>
  if (axiosError?.response?.data && typeof axiosError.response.data === 'object') {
    return axiosError.response.data
  }
  return {
    title: 'Request failed',
    detail: axiosError?.message ?? 'Unknown error',
    status: axiosError?.response?.status,
  }
}
