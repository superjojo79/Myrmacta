import axios from 'axios'
import type { AdminListParams } from './dossierTypes'
import type { Page } from './page'

/** Gabarit de notification (TODO §123) : objet et corps texte obligatoires, corps HTML facultatif. */
export interface NotificationTemplate {
  id: string
  templateKey: string
  variantKey: string | null
  locale: string | null
  label: string
  version: number
  status: 'DRAFT' | 'ACTIVE' | 'ARCHIVED'
  subject: string
  textBody: string
  htmlBody: string | null
  createdAt: string
  updatedAt: string
  updatedBy: string | null
}

export interface NotificationTemplateContent {
  subject: string
  textBody: string
  htmlBody?: string | null
}

export interface RenderedNotification {
  subject: string
  textBody: string
  htmlBody: string | null
}

const BASE = '/api/notification-templates'

export async function listNotificationTemplates(
  params: AdminListParams = {},
): Promise<Page<NotificationTemplate>> {
  const query: Record<string, number> = {}
  if (params.page != null) query.page = params.page
  if (params.size != null) query.size = params.size
  const { data } = await axios.get<Page<NotificationTemplate>>(BASE, { params: query })
  return data
}

export async function getNotificationTemplate(id: string): Promise<NotificationTemplate> {
  const { data } = await axios.get<NotificationTemplate>(`${BASE}/${id}`)
  return data
}

export async function createNotificationTemplate(
  payload: NotificationTemplateContent & {
    templateKey: string
    variantKey?: string
    locale?: string
    label: string
  },
): Promise<NotificationTemplate> {
  const { data } = await axios.post<NotificationTemplate>(BASE, payload)
  return data
}

/** Brouillon seulement. Un champ absent est inchangé ; `htmlBody` vide supprime le corps HTML. */
export async function updateNotificationTemplate(
  id: string,
  payload: Partial<NotificationTemplateContent> & { label?: string },
): Promise<NotificationTemplate> {
  const { data } = await axios.put<NotificationTemplate>(`${BASE}/${id}`, payload)
  return data
}

export async function activateNotificationTemplate(id: string): Promise<NotificationTemplate> {
  const { data } = await axios.post<NotificationTemplate>(`${BASE}/${id}/activate`)
  return data
}

export async function archiveNotificationTemplate(id: string): Promise<NotificationTemplate> {
  const { data } = await axios.post<NotificationTemplate>(`${BASE}/${id}/archive`)
  return data
}

export async function newNotificationTemplateVersion(id: string): Promise<NotificationTemplate> {
  const { data } = await axios.post<NotificationTemplate>(`${BASE}/${id}/new-version`)
  return data
}

/** Soit un gabarit stocké (`id`, brouillon compris), soit un contenu fourni (`template`). */
export async function previewNotificationTemplate(
  request:
    | { id: string; model: Record<string, unknown> }
    | { template: NotificationTemplateContent; model: Record<string, unknown> },
): Promise<RenderedNotification> {
  const { data } = await axios.post<RenderedNotification>(`${BASE}/preview`, request)
  return data
}
