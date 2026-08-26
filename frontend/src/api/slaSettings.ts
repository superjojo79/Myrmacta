import axios from 'axios'

export interface TenantSlaSettings {
  tenantId: string
  complianceTargetPercent: number
  updatedAt: string
  updatedBy: string
}

export async function getSlaSettings(): Promise<TenantSlaSettings> {
  const { data } = await axios.get<TenantSlaSettings>('/api/tenant/sla-settings')
  return data
}

export async function upsertSlaSettings(complianceTargetPercent: number): Promise<TenantSlaSettings> {
  const { data } = await axios.put<TenantSlaSettings>('/api/tenant/sla-settings', {
    complianceTargetPercent,
  })
  return data
}
