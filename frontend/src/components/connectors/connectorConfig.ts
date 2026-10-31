import type { ConfigField, ConnectorDefinition } from '@/api/admin'

export type FormValue = string | number | boolean
export type FormValues = Record<string, FormValue>

export function schemaOf(
  definitions: ConnectorDefinition[],
  connectorType: string | undefined,
): ConfigField[] {
  if (!connectorType) {
    return []
  }
  const definition = definitions.find((item) => item.type === connectorType)
  return definition?.configSchema ?? []
}

export function defaultsFromSchema(schema: ConfigField[]): FormValues {
  const values: FormValues = {}
  for (const field of schema) {
    values[field.name] = defaultForField(field)
  }
  return values
}

function defaultForField(field: ConfigField): FormValue {
  if (field.type === 'BOOLEAN') {
    return field.defaultValue === 'true' || field.defaultValue === '1'
  }
  if (field.defaultValue != null && field.defaultValue !== '') {
    if (field.type === 'INTEGER') {
      const parsed = Number(field.defaultValue)
      return Number.isFinite(parsed) ? parsed : ''
    }
    return field.defaultValue
  }
  if (field.type === 'SELECT' && field.required && field.options?.[0]) {
    return field.options[0]
  }
  return ''
}

export function configFromFormValues(
  schema: ConfigField[],
  values: FormValues,
): Record<string, unknown> {
  const config: Record<string, unknown> = {}
  for (const field of schema) {
    const raw = values[field.name]
    if (field.type === 'BOOLEAN') {
      config[field.name] = Boolean(raw)
      continue
    }
    if (field.type === 'INTEGER') {
      if (raw === '' || raw === undefined) {
        continue
      }
      const parsed = typeof raw === 'number' ? raw : Number(raw)
      if (!Number.isFinite(parsed)) {
        continue
      }
      config[field.name] = parsed
      continue
    }
    const text = raw == null ? '' : String(raw)
    if (!field.required && text === '') {
      continue
    }
    config[field.name] = text
  }
  return config
}

export type ConnectorIngress = {
  kind: 'webhook' | 'callback'
  method: 'POST'
  url: string
}

const INGRESS_BY_TYPE: Record<string, { kind: ConnectorIngress['kind']; path: string }> = {
  'http-rest': { kind: 'webhook', path: '/api/webhooks' },
  'flowable-callback': { kind: 'callback', path: '/api/callbacks' },
}

/** TODO §112 : une instance « service seul » (`webhookEnabled` faux) n'a pas d'URL d'entrée — le serveur répond 404. */
function webhookDisabled(config: Record<string, unknown> | undefined): boolean {
  const value = config?.webhookEnabled
  return value === false || (typeof value === 'string' && value.trim().toLowerCase() === 'false')
}

export function ingressUrlFor(
  connector: { id: string; connectorType: string; config?: Record<string, unknown> },
  origin: string,
  apiBase = '',
): ConnectorIngress | null {
  const mapping = INGRESS_BY_TYPE[connector.connectorType]
  if (!mapping || !connector.id) {
    return null
  }
  if (mapping.kind === 'webhook' && webhookDisabled(connector.config)) {
    return null
  }
  const originClean = origin.replace(/\/$/, '')
  const base = apiBase.replace(/\/$/, '')
  return {
    kind: mapping.kind,
    method: 'POST',
    url: `${originClean}${base}${mapping.path}/${connector.id}`,
  }
}
