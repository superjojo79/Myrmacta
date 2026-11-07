import axios from 'axios'

export type WeekendPattern = 'SATURDAY_SUNDAY' | 'FRIDAY_SATURDAY' | 'SUNDAY_ONLY'

export interface TenantCalendar {
  tenantId: string
  zoneId: string
  weekend: WeekendPattern
  holidays: string[]
  updatedAt: string | null
  updatedBy: string | null
}

export async function getTenantCalendar(): Promise<TenantCalendar> {
  const { data } = await axios.get<TenantCalendar>('/api/tenant/calendar')
  return data
}

export async function replaceTenantCalendar(payload: {
  zoneId: string
  weekend: string
  holidays: string[]
}): Promise<TenantCalendar> {
  const { data } = await axios.put<TenantCalendar>('/api/tenant/calendar', payload)
  return data
}

export async function addBelgianFederalHolidays(): Promise<TenantCalendar> {
  const { data } = await axios.post<TenantCalendar>('/api/tenant/calendar/belgian-federal-holidays')
  return data
}
