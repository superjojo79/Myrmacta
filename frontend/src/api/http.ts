import axios, { type AxiosError } from 'axios'
import type { FieldError, SchemaDuplicate } from '@/api/types'

export type ProblemDetail = {
  type?: string
  title?: string
  status?: number
  detail?: string
  errors?: unknown
  duplicates?: unknown
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

/** `errors[]` d'un ProblemDetail, ou rien si le corps n'en a pas (les autres erreurs gardent `detail`). */
export function fieldErrorsOf(problem: ProblemDetail): FieldError[] {
  if (!Array.isArray(problem.errors)) return []
  const parsed: FieldError[] = []
  for (const item of problem.errors) {
    if (!isRecord(item) || typeof item.field !== 'string' || typeof item.code !== 'string') continue
    parsed.push({
      field: item.field,
      code: item.code,
      message: typeof item.message === 'string' ? item.message : '',
      reference: typeof item.reference === 'string' ? item.reference : null,
    })
  }
  return parsed
}

/** `duplicates[]` d'un refus de schéma (409). Une entrée mal formée est ignorée. */
export function schemaDuplicatesOf(problem: ProblemDetail): SchemaDuplicate[] {
  if (!Array.isArray(problem.duplicates)) return []
  const parsed: SchemaDuplicate[] = []
  for (const item of problem.duplicates) {
    if (!isRecord(item) || typeof item.field !== 'string' || typeof item.value !== 'string' || !Array.isArray(item.references)) {
      continue
    }
    if (!item.references.every((reference) => typeof reference === 'string')) continue
    const duplicate: SchemaDuplicate = { field: item.field, value: item.value, references: item.references }
    if (typeof item.referenceCount === 'number') duplicate.referenceCount = item.referenceCount
    parsed.push(duplicate)
  }
  return parsed
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
    // Le statut HTTP fait foi quand le corps ne le reprend pas (corps d'erreur non ProblemDetail).
    return { ...axiosError.response.data, status: axiosError.response.data.status ?? axiosError.response.status }
  }
  return {
    title: 'Request failed',
    detail: axiosError?.message ?? 'Unknown error',
    status: axiosError?.response?.status,
  }
}
