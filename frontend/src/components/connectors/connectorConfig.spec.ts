import { describe, expect, it } from 'vitest'
import {
  configFromFormValues,
  defaultsFromSchema,
  schemaOf,
} from './connectorConfig'
import type { ConfigField, ConnectorDefinition } from '@/api/admin'

const httpRestSchema: ConfigField[] = [
  {
    name: 'webhookAuthType',
    label: 'Webhook auth',
    type: 'SELECT',
    required: true,
    defaultValue: 'HMAC_SHA256',
    options: ['HMAC_SHA256', 'SHARED_SECRET_TOKEN'],
  },
  {
    name: 'webhookSecret',
    label: 'Webhook secret',
    type: 'PASSWORD',
    required: true,
  },
  {
    name: 'processKey',
    label: 'Process key',
    type: 'STRING',
    required: true,
  },
  {
    name: 'authHeaderValue',
    label: 'Authorization header value',
    type: 'PASSWORD',
    required: false,
  },
  {
    name: 'timeoutSeconds',
    label: 'Timeout',
    type: 'INTEGER',
    required: false,
    defaultValue: '5',
  },
  {
    name: 'verifyTls',
    label: 'Verify TLS',
    type: 'BOOLEAN',
    required: false,
  },
]

describe('defaultsFromSchema', () => {
  it('uses defaultValue, SELECT first option, BOOLEAN false, and empty string otherwise', () => {
    const values = defaultsFromSchema(httpRestSchema)
    expect(values.webhookAuthType).toBe('HMAC_SHA256')
    expect(values.webhookSecret).toBe('')
    expect(values.processKey).toBe('')
    expect(values.authHeaderValue).toBe('')
    expect(values.timeoutSeconds).toBe(5)
    expect(values.verifyTls).toBe(false)
  })

  it('picks the first SELECT option when required and no defaultValue', () => {
    const schema: ConfigField[] = [
      {
        name: 'mode',
        label: 'Mode',
        type: 'SELECT',
        required: true,
        options: ['A', 'B'],
      },
    ]
    expect(defaultsFromSchema(schema).mode).toBe('A')
  })
})

describe('configFromFormValues', () => {
  it('coerces INTEGER to number and BOOLEAN to boolean', () => {
    const config = configFromFormValues(httpRestSchema, {
      webhookAuthType: 'SHARED_SECRET_TOKEN',
      webhookSecret: 'a-very-long-secret',
      processKey: 'pk',
      authHeaderValue: '',
      timeoutSeconds: '12',
      verifyTls: true,
    })
    expect(config).toEqual({
      webhookAuthType: 'SHARED_SECRET_TOKEN',
      webhookSecret: 'a-very-long-secret',
      processKey: 'pk',
      timeoutSeconds: 12,
      verifyTls: true,
    })
  })

  it('omits blank optional strings and empty optional integers', () => {
    const config = configFromFormValues(httpRestSchema, {
      webhookAuthType: 'HMAC_SHA256',
      webhookSecret: 'secret',
      processKey: 'pk',
      authHeaderValue: '',
      timeoutSeconds: '',
      verifyTls: false,
    })
    expect(config).not.toHaveProperty('authHeaderValue')
    expect(config).not.toHaveProperty('timeoutSeconds')
    expect(config.verifyTls).toBe(false)
  })

  it('omits non-numeric INTEGER values', () => {
    const schema: ConfigField[] = [
      { name: 'n', label: 'N', type: 'INTEGER', required: false },
    ]
    expect(configFromFormValues(schema, { n: 'abc' })).toEqual({})
  })

  it('returns empty object for an empty schema', () => {
    expect(configFromFormValues([], { leftover: 'x' })).toEqual({})
  })
})

describe('schemaOf', () => {
  const definitions: ConnectorDefinition[] = [
    { type: 'http-rest', label: 'HTTP', configSchema: httpRestSchema },
    { type: 'smtp', label: 'SMTP', configSchema: [] },
  ]

  it('returns the matching configSchema', () => {
    expect(schemaOf(definitions, 'http-rest')).toEqual(httpRestSchema)
    expect(schemaOf(definitions, 'smtp')).toEqual([])
  })

  it('returns empty list for unknown or missing type', () => {
    expect(schemaOf(definitions, 'nope')).toEqual([])
    expect(schemaOf(definitions, undefined)).toEqual([])
  })
})
