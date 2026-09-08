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
