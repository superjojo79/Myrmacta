import axios from 'axios'

export interface TenantRetentionSettings {
  tenantId: string
  defaultRetentionDays: number
  updatedAt: string
  updatedBy: string
}

export async function getRetentionSettings(): Promise<TenantRetentionSettings> {
  const { data } = await axios.get<TenantRetentionSettings>('/api/tenant/retention-settings')
  return data
}

export async function upsertRetentionSettings(
  defaultRetentionDays: number,
): Promise<TenantRetentionSettings> {
  const { data } = await axios.put<TenantRetentionSettings>('/api/tenant/retention-settings', {
    defaultRetentionDays,
  })
  return data
}
