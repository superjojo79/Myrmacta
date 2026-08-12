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
    // Keep first path segment as tenant context-path when present (DD-BASE)
    return `/${segments[0]}`
  } catch {
    return ''
  }
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
