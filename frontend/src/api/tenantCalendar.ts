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

/** Suite de jours consécutifs, bornes incluses, dates ISO. */
export interface DayRange {
  start: string
  end: string
}

export type IgnoreReason = 'TIMED' | 'CANCELLED' | 'TRANSPARENT' | 'INVALID'

/** Ce que l'application d'un fichier ICS changerait (TODO §121, D-CAL-ICS-PREVIEW). `sha256` est à renvoyer pour appliquer. */
export interface SourcePreview {
  sourceId: string
  name: string
  kind: SourceKind
  added: DayRange[]
  removed: DayRange[]
  masked: DayRange[]
  ignored: Partial<Record<IgnoreReason, number>>
  sha256: string
  periodCount: number
}

function form(fields: Record<string, string | File>): FormData {
  const data = new FormData()
  for (const [key, value] of Object.entries(fields)) data.append(key, value)
  return data
}

/** Aperçu d'un fichier ICS comme nouvelle source ; n'écrit rien. */
export async function previewNewSource(name: string, file: File): Promise<SourcePreview> {
  const { data } = await axios.post<SourcePreview>('/api/tenant/calendar/sources/preview', form({ name, file }))
  return data
}

/** Crée la source ; `expectedSha256` est celui de l'aperçu (409 si le contenu a changé ou si le nom est pris). */
export async function createSource(name: string, file: File, expectedSha256: string): Promise<TenantCalendar> {
  const { data } = await axios.post<TenantCalendar>('/api/tenant/calendar/sources', form({ name, file, expectedSha256 }))
  return data
}

export async function previewReload(sourceId: string, file: File): Promise<SourcePreview> {
  const { data } = await axios.post<SourcePreview>(`/api/tenant/calendar/sources/${sourceId}/preview`, form({ file }))
  return data
}

/** Remplace les jours de la source ; ni les autres sources ni les saisies manuelles ne bougent. */
export async function reloadSource(sourceId: string, file: File, expectedSha256: string): Promise<TenantCalendar> {
  const { data } = await axios.post<TenantCalendar>(`/api/tenant/calendar/sources/${sourceId}/reload`, form({ file, expectedSha256 }))
  return data
}

export async function deleteSource(sourceId: string): Promise<void> {
  await axios.delete(`/api/tenant/calendar/sources/${sourceId}`)
}
