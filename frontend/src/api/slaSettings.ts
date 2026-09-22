import axios from 'axios'

export interface TenantSlaSettings {
  tenantId: string
  complianceTargetPercent: number
  alertingEnabled: boolean
  alertIntervalMinutes: number
  alertEmails: string[]
  webhookIds: string[]
  biExportEnabled: boolean
  biIntervalHours: number
  biRetentionDays: number
  alertingEnabledAt: string | null
  lastAlertScanAt: string | null
  biEnabledAt: string | null
  lastBiExportAt: string | null
  updatedAt: string
  updatedBy: string
}

export interface UpsertTenantSlaSettings {
  complianceTargetPercent: number
  alertingEnabled: boolean
  alertIntervalMinutes: number
  alertEmails: string[]
  webhookIds: string[]
  biExportEnabled: boolean
  biIntervalHours: number
  biRetentionDays: number
}

export async function getSlaSettings(): Promise<TenantSlaSettings> {
  const { data } = await axios.get<TenantSlaSettings>('/api/tenant/sla-settings')
  return data
}

export async function upsertSlaSettings(payload: UpsertTenantSlaSettings): Promise<TenantSlaSettings> {
  const { data } = await axios.put<TenantSlaSettings>('/api/tenant/sla-settings', payload)
  return data
}
