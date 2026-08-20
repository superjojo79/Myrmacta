import axios from 'axios'

export type ObservabilitySeverity = 'ok' | 'warn' | 'crit'

export interface PdfRenderStats {
  count: number
  meanMs: number | null
  maxMs: number | null
}

export interface ObservabilitySeverityMap {
  triggerOutboxOldestPendingAge: ObservabilitySeverity
  notificationOutboxOldestPendingAge: ObservabilitySeverity
  purgeOutboxOldestUnfinishedAge: ObservabilitySeverity
  flowableDeadLetterJobs: ObservabilitySeverity
  flowableExceptionJobs: ObservabilitySeverity
  pdfRender: ObservabilitySeverity
  webhookDead: ObservabilitySeverity
  sseActive: ObservabilitySeverity
}

export interface ObservabilitySnapshot {
  tenantId: string
  observedAt: string
  triggerOutboxOldestPendingAgeSeconds: number
  notificationOutboxOldestPendingAgeSeconds: number
  purgeOutboxOldestUnfinishedAgeSeconds: number
  flowableDeadLetterJobCount: number
  flowableExceptionJobCount: number
  pdfRender: PdfRenderStats
  webhookDeadCount: number
  sseActiveCount: number
  severity: ObservabilitySeverityMap
}

export async function getObservabilitySnapshot(): Promise<ObservabilitySnapshot> {
  const { data } = await axios.get<ObservabilitySnapshot>('/api/observability')
  return data
}
