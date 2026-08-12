import axios from 'axios'
import type { Connector, DocumentTemplate } from './types'

export async function listDocumentTemplates(
  templateKey?: string,
  includeContent = false,
): Promise<DocumentTemplate[]> {
  const { data } = await axios.get<DocumentTemplate[]>('/api/document-templates', {
    params: { templateKey, includeContent },
  })
  return data
}

export async function createDocumentTemplate(payload: {
  templateKey: string
  variantKey?: string
  locale?: string
  label: string
  htmlContent: string
}): Promise<DocumentTemplate> {
  const { data } = await axios.post<DocumentTemplate>('/api/document-templates', payload)
  return data
}

export async function updateDocumentTemplate(
  id: string,
  payload: { label?: string; htmlContent?: string },
): Promise<DocumentTemplate> {
  const { data } = await axios.patch<DocumentTemplate>(`/api/document-templates/${id}`, payload)
  return data
}

export async function activateDocumentTemplate(id: string): Promise<DocumentTemplate> {
  const { data } = await axios.post<DocumentTemplate>(`/api/document-templates/${id}/activate`)
  return data
}

export async function archiveDocumentTemplate(id: string): Promise<DocumentTemplate> {
  const { data } = await axios.post<DocumentTemplate>(`/api/document-templates/${id}/archive`)
  return data
}

export async function newDocumentTemplateVersion(id: string): Promise<DocumentTemplate> {
  const { data } = await axios.post<DocumentTemplate>(`/api/document-templates/${id}/new-version`)
  return data
}

export async function listConnectorDefinitions(): Promise<
  Array<{ type: string; label?: string; [key: string]: unknown }>
> {
  const { data } = await axios.get('/api/connector-definitions')
  return data
}

export async function listConnectors(): Promise<Connector[]> {
  const { data } = await axios.get<Connector[]>('/api/connectors')
  return data
}

export async function createConnector(payload: {
  name: string
  connectorType: string
  enabled?: boolean
  config?: Record<string, unknown>
}): Promise<Connector> {
  const { data } = await axios.post<Connector>('/api/connectors', payload)
  return data
}

export async function updateConnector(
  id: string,
  payload: {
    name?: string
    enabled?: boolean
    config?: Record<string, unknown>
    restore?: boolean
  },
): Promise<Connector> {
  const { data } = await axios.patch<Connector>(`/api/connectors/${id}`, payload)
  return data
}

export async function deleteConnector(id: string): Promise<void> {
  await axios.delete(`/api/connectors/${id}`)
}
