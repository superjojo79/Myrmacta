import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import axios from 'axios'
import ConnectorsAdminView from './ConnectorsAdminView.vue'
import * as adminApi from '@/api/admin'
import { routes } from '@/app/routes'
import { asPage } from '@/api/page'
import type { ConfigField } from '@/api/admin'

vi.mock('@/api/admin')

const httpRestSchema: ConfigField[] = [
  {
    name: 'webhookAuthType',
    label: 'Webhook auth',
    type: 'SELECT',
    required: true,
    defaultValue: 'HMAC_SHA256',
    options: ['HMAC_SHA256', 'SHARED_SECRET_TOKEN'],
  },
  { name: 'webhookSecret', label: 'Webhook secret', type: 'PASSWORD', required: true },
  { name: 'processKey', label: 'Process key', type: 'STRING', required: true },
  { name: 'dossierTypeCode', label: 'Dossier type', type: 'STRING', required: true },
]

async function mountView() {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push('/admin/connectors')
  await router.isReady()
  return mount(ConnectorsAdminView, { global: { plugins: [router] } })
}

describe('ConnectorsAdminView generated config form', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(adminApi.listConnectors).mockResolvedValue(asPage([]))
    vi.mocked(adminApi.createConnector).mockResolvedValue({} as never)
  })

  afterEach(() => {
    axios.defaults.baseURL = undefined
  })

  it('renders schema fields instead of a JSON textarea', async () => {
    vi.mocked(adminApi.listConnectorDefinitions).mockResolvedValue([
      { type: 'http-rest', label: 'HTTP REST', configSchema: httpRestSchema },
    ])
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.find('textarea').exists()).toBe(false)
    expect(wrapper.get('[data-testid="connector-config-field-webhookSecret"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="connector-config-field-processKey"]').exists()).toBe(true)
  })

  it('posts a typed config object from the generated form', async () => {
    vi.mocked(adminApi.listConnectorDefinitions).mockResolvedValue([
      { type: 'http-rest', label: 'HTTP REST', configSchema: httpRestSchema },
    ])
    const wrapper = await mountView()
    await flushPromises()
    await wrapper.get('input[placeholder="nom"]').setValue('Hook')
    await wrapper.get('[data-testid="connector-config-field-webhookSecret"]').setValue('a-very-long-secret')
    await wrapper.get('[data-testid="connector-config-field-processKey"]').setValue('signal-smoke')
    await wrapper.get('[data-testid="connector-config-field-dossierTypeCode"]').setValue('SINISTRE')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()
    expect(adminApi.createConnector).toHaveBeenCalledWith({
      name: 'Hook',
      connectorType: 'http-rest',
      enabled: true,
      config: {
        webhookAuthType: 'HMAC_SHA256',
        webhookSecret: 'a-very-long-secret',
        processKey: 'signal-smoke',
        dossierTypeCode: 'SINISTRE',
      },
    })
  })

  it('submits empty config for a type with an empty schema', async () => {
    vi.mocked(adminApi.listConnectorDefinitions).mockResolvedValue([
      { type: 'smtp', label: 'SMTP', configSchema: [] },
    ])
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.find('textarea').exists()).toBe(false)
    expect(wrapper.get('[data-testid="connector-config-empty"]').text()).toMatch(/Aucune configuration/)
    await wrapper.get('input[placeholder="nom"]').setValue('Mailer')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()
    expect(adminApi.createConnector).toHaveBeenCalledWith({
      name: 'Mailer',
      connectorType: 'smtp',
      enabled: true,
      config: {},
    })
  })

  it('resets values when the connector type changes', async () => {
    vi.mocked(adminApi.listConnectorDefinitions).mockResolvedValue([
      { type: 'http-rest', label: 'HTTP REST', configSchema: httpRestSchema },
      {
        type: 'scheduler',
        label: 'Scheduler',
        configSchema: [
          { name: 'cronExpression', label: 'Cron', type: 'STRING', required: true },
          { name: 'processKey', label: 'Process key', type: 'STRING', required: true },
        ],
      },
    ])
    const wrapper = await mountView()
    await flushPromises()
    await wrapper.get('[data-testid="connector-config-field-webhookSecret"]').setValue('leaked')
    await wrapper.get('[data-testid="connector-type"]').setValue('scheduler')
    await flushPromises()
    expect(wrapper.find('[data-testid="connector-config-field-webhookSecret"]').exists()).toBe(false)
    await wrapper.get('input[placeholder="nom"]').setValue('Cron')
    await wrapper.get('[data-testid="connector-config-field-cronExpression"]').setValue('0 * * * * *')
    await wrapper.get('[data-testid="connector-config-field-processKey"]').setValue('job')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()
    expect(adminApi.createConnector).toHaveBeenCalledWith({
      name: 'Cron',
      connectorType: 'scheduler',
      enabled: true,
      config: { cronExpression: '0 * * * * *', processKey: 'job' },
    })
  })

  it('keeps a JSON textarea when the catalogue is empty', async () => {
    vi.mocked(adminApi.listConnectorDefinitions).mockResolvedValue([])
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.get('[data-testid="connector-config-json-fallback"]').exists()).toBe(true)
    await wrapper.get('input[placeholder="nom"]').setValue('Custom')
    await wrapper.get('input[placeholder="type"]').setValue('custom')
    await wrapper.get('[data-testid="connector-config-json-fallback"]').setValue('{"k":"v"}')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()
    expect(adminApi.createConnector).toHaveBeenCalledWith({
      name: 'Custom',
      connectorType: 'custom',
      enabled: true,
      config: { k: 'v' },
    })
  })

  it('shows a problem detail when create fails', async () => {
    vi.mocked(adminApi.listConnectorDefinitions).mockResolvedValue([
      { type: 'smtp', label: 'SMTP', configSchema: [] },
    ])
    vi.mocked(adminApi.createConnector).mockRejectedValue({
      response: { status: 400, data: { detail: 'webhookSecret too short' } },
    })
    const wrapper = await mountView()
    await flushPromises()
    await wrapper.get('input[placeholder="nom"]').setValue('Bad')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()
    expect(wrapper.get('.error').text()).toBe('webhookSecret too short')
  })

  it('shows the webhook URL for an http-rest connector in the list', async () => {
    vi.mocked(adminApi.listConnectorDefinitions).mockResolvedValue([
      { type: 'http-rest', label: 'HTTP REST', configSchema: httpRestSchema },
    ])
    vi.mocked(adminApi.listConnectors).mockResolvedValue(
      asPage([
        {
          id: 'c1',
          tenantId: 't',
          name: 'Hook',
          connectorType: 'http-rest',
          enabled: true,
          config: {},
          secretConfigured: { webhookSecret: true },
        },
      ]),
    )
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.get('[data-testid="connector-ingress-url"]').text()).toBe(
      `POST ${globalThis.location.origin}/api/webhooks/c1`,
    )
  })

  it('shows the callback URL for a flowable-callback connector in the list', async () => {
    vi.mocked(adminApi.listConnectorDefinitions).mockResolvedValue([
      { type: 'flowable-callback', label: 'Callback', configSchema: [] },
    ])
    vi.mocked(adminApi.listConnectors).mockResolvedValue(
      asPage([
        {
          id: 'cb-9',
          tenantId: 't',
          name: 'Async',
          connectorType: 'flowable-callback',
          enabled: true,
          config: {},
          secretConfigured: { webhookSecret: true },
        },
      ]),
    )
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.get('[data-testid="connector-ingress"]').text()).toMatch(/URL callback/)
    expect(wrapper.get('[data-testid="connector-ingress-url"]').text()).toBe(
      `POST ${globalThis.location.origin}/api/callbacks/cb-9`,
    )
  })

  it('does not show an ingress URL for smtp', async () => {
    vi.mocked(adminApi.listConnectorDefinitions).mockResolvedValue([
      { type: 'smtp', label: 'SMTP', configSchema: [] },
    ])
    vi.mocked(adminApi.listConnectors).mockResolvedValue(
      asPage([
        {
          id: 's1',
          tenantId: 't',
          name: 'Mailer',
          connectorType: 'smtp',
          enabled: true,
          config: {},
          secretConfigured: {},
        },
      ]),
    )
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('Mailer')
    expect(wrapper.find('[data-testid="connector-ingress"]').exists()).toBe(false)
  })

  it('prefixes ingress URLs with the HTTP client tenant context-path', async () => {
    axios.defaults.baseURL = '/acme'
    vi.mocked(adminApi.listConnectorDefinitions).mockResolvedValue([
      { type: 'http-rest', label: 'HTTP REST', configSchema: httpRestSchema },
    ])
    vi.mocked(adminApi.listConnectors).mockResolvedValue(
      asPage([
        {
          id: 'c1',
          tenantId: 'acme',
          name: 'Hook',
          connectorType: 'http-rest',
          enabled: true,
          config: {},
          secretConfigured: {},
        },
      ]),
    )
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.get('[data-testid="connector-ingress-url"]').text()).toBe(
      `POST ${globalThis.location.origin}/acme/api/webhooks/c1`,
    )
  })
})
