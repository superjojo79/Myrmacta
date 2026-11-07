import axios from 'axios'

export type WeekendPattern = 'SATURDAY_SUNDAY' | 'FRIDAY_SATURDAY' | 'SUNDAY_ONLY'

/** `CLOSED` : jour fermé. `OPEN` : jour ouvré forcé, y compris un jour de week-end (TODO §121, D-CAL-OVERRIDE). */
export type EntryNature = 'CLOSED' | 'OPEN'

export type SourceKind = 'ICS_FILE' | 'ICS_URL' | 'BE_FEDERAL'

/** Une date ou une période, bornes incluses, dates ISO (TODO §121, D-CAL-ENTRY). */
export interface CalendarEntry {
  start: string
  end: string
  nature: EntryNature
  label: string | null
}

export interface CalendarSource {
  id: string
  name: string
  kind: SourceKind
  /** URL d'une source ICS_URL réduite à son hôte : l'URL entière est un secret. */
  maskedUrl: string | null
  entryCount: number
  loadedAt: string | null
  loadedBy: string | null
  lastError: string | null
  entries: CalendarEntry[]
}

export interface TenantCalendar {
  tenantId: string
  zoneId: string
  weekend: WeekendPattern
  /** Saisies manuelles. */
  entries: CalendarEntry[]
  sources: CalendarSource[]
  /** @deprecated jours fermés effectifs, calculés : lire `entries` et `sources`. */
  holidays: string[]
  updatedAt: string | null
  updatedBy: string | null
}

export async function getTenantCalendar(): Promise<TenantCalendar> {
  const { data } = await axios.get<TenantCalendar>('/api/tenant/calendar')
  return data
}

/** Remplace le fuseau, le week-end et les saisies manuelles ; les sources ne bougent pas. */
export async function replaceTenantCalendar(payload: {
  zoneId: string
  weekend: string
  entries: CalendarEntry[]
}): Promise<TenantCalendar> {
  const { data } = await axios.put<TenantCalendar>('/api/tenant/calendar', payload)
  return data
}

/** Crée ou recharge la source des fériés fédéraux belges (années courante et suivante). */
export async function addBelgianFederalHolidays(): Promise<TenantCalendar> {
  const { data } = await axios.post<TenantCalendar>('/api/tenant/calendar/belgian-federal-holidays')
  return data
}
