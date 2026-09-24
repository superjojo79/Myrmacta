import type { FieldType } from '@/api/types'

export interface PortalField {
  name: string
  label: string
  type: FieldType
  required: boolean
  options?: string[]
  group?: string | null
}

export interface PortalAttachment {
  id: string
  label: string
  filename: string
  sizeBytes?: number | null
  addedAt: string
}

export interface PortalTask {
  dossierReference: string
  taskName: string | null
  instructions: string | null
  form: { label: string; fields: PortalField[] }
  expiresAt: string
  attachments: PortalAttachment[]
}

export class PortalApiError extends Error {
  readonly status: number
  readonly detail: string

  constructor(status: number, detail: string) {
    super(detail)
    this.status = status
    this.detail = detail
  }
}

const TOKEN_HEADER = 'X-Portal-Token'

/** `{contexte}/portal/…` → `{contexte}/api/portal/task` (routage par chemin, décision n° 14). */
export function apiBase(pathname: string = window.location.pathname): string {
  // Dernier segment /portal : le chemin du tenant peut lui-même contenir « /portal » (Phase 7).
  const index = pathname.lastIndexOf('/portal/')
  const prefix = index > 0 ? pathname.slice(0, index) : ''
  return `${prefix}/api/portal/task`
}

async function request<T>(token: string, path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set(TOKEN_HEADER, token)
  headers.set('Accept', 'application/json')
  // credentials: 'omit' — la page publique ne doit jamais transporter le cookie de session du SPA
  // interne, même si le tiers est aussi un utilisateur connecté dans le même navigateur.
  const response = await fetch(apiBase() + path, { ...init, headers, credentials: 'omit' })
  if (!response.ok) {
    let detail = ''
    try {
      const body = (await response.json()) as { detail?: string }
      detail = body.detail ?? ''
    } catch {
      detail = ''
    }
    throw new PortalApiError(response.status, detail)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export function fetchTask(token: string): Promise<PortalTask> {
  return request<PortalTask>(token, '')
}

export function uploadAttachment(token: string, file: File, label: string): Promise<PortalAttachment> {
  const body = new FormData()
  body.append('file', file)
  if (label.trim()) {
    body.append('label', label.trim())
  }
  return request<PortalAttachment>(token, '/attachments', { method: 'POST', body })
}

export function submitAnswers(token: string, answers: Record<string, unknown>): Promise<void> {
  return request<void>(token, '/submit', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ answers }),
  })
}
