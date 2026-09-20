import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ConnectorConfigForm from './ConnectorConfigForm.vue'
import type { ConfigField } from '@/api/admin'

const schema: ConfigField[] = [
  { name: 'processKey', label: 'Process key', type: 'STRING', required: true, helpText: 'BPMN key' },
  { name: 'webhookSecret', label: 'Webhook secret', type: 'PASSWORD', required: true },
  {
    name: 'webhookAuthType',
    label: 'Webhook auth',
    type: 'SELECT',
    required: true,
    options: ['HMAC_SHA256', 'SHARED_SECRET_TOKEN'],
  },
  { name: 'callbackUrl', label: 'Callback URL', type: 'URL', required: false },
  { name: 'timeoutSeconds', label: 'Timeout', type: 'INTEGER', required: false },
  { name: 'verifyTls', label: 'Verify TLS', type: 'BOOLEAN', required: false },
]

function mountForm(modelValue: Record<string, string | number | boolean> = {}) {
  return mount(ConnectorConfigForm, {
    props: { schema, modelValue },
  })
}

describe('ConnectorConfigForm', () => {
  it('renders a control for each ConfigFieldType', () => {
    const wrapper = mountForm({
      processKey: '',
      webhookSecret: '',
      webhookAuthType: 'HMAC_SHA256',
      callbackUrl: '',
      timeoutSeconds: '',
      verifyTls: false,
    })
    expect(wrapper.get('[data-testid="connector-config-field-processKey"]').attributes('type')).toBe(
      'text',
    )
    expect(
      wrapper.get('[data-testid="connector-config-field-webhookSecret"]').attributes('type'),
    ).toBe('password')
    expect(wrapper.get('[data-testid="connector-config-field-callbackUrl"]').attributes('type')).toBe(
      'url',
    )
    expect(
      wrapper.get('[data-testid="connector-config-field-timeoutSeconds"]').attributes('type'),
    ).toBe('number')
    expect(wrapper.find('[data-testid="connector-config-field-webhookAuthType"]').element.tagName).toBe(
      'SELECT',
    )
    expect(
      wrapper.get('[data-testid="connector-config-field-verifyTls"]').attributes('type'),
    ).toBe('checkbox')
  })

  it('marks required STRING fields and not BOOLEAN', () => {
    const wrapper = mountForm({
      processKey: '',
      webhookSecret: '',
      webhookAuthType: 'HMAC_SHA256',
      callbackUrl: '',
      timeoutSeconds: '',
      verifyTls: false,
    })
    expect(wrapper.get('[data-testid="connector-config-field-processKey"]').attributes('required')).toBeDefined()
    expect(wrapper.get('[data-testid="connector-config-field-verifyTls"]').attributes('required')).toBeUndefined()
  })

  it('shows helpText', () => {
    const wrapper = mountForm({
      processKey: '',
      webhookSecret: '',
      webhookAuthType: 'HMAC_SHA256',
      callbackUrl: '',
      timeoutSeconds: '',
      verifyTls: false,
    })
    expect(wrapper.get('[data-testid="connector-config-help-processKey"]').text()).toBe('BPMN key')
  })

  it('emits v-model updates from text, select and checkbox', async () => {
    const wrapper = mountForm({
      processKey: '',
      webhookSecret: '',
      webhookAuthType: 'HMAC_SHA256',
      callbackUrl: '',
      timeoutSeconds: '',
      verifyTls: false,
    })
    await wrapper.get('[data-testid="connector-config-field-processKey"]').setValue('pk')
    await wrapper.get('[data-testid="connector-config-field-webhookAuthType"]').setValue(
      'SHARED_SECRET_TOKEN',
    )
    await wrapper.get('[data-testid="connector-config-field-verifyTls"]').setValue(true)
    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted?.at(-1)?.[0]).toMatchObject({
      processKey: 'pk',
      webhookAuthType: 'SHARED_SECRET_TOKEN',
      verifyTls: true,
    })
  })

  it('renders nothing when the schema is empty', () => {
    const wrapper = mount(ConnectorConfigForm, {
      props: { schema: [], modelValue: {} },
    })
    expect(wrapper.find('[data-testid="connector-config-form"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid^="connector-config-field-"]')).toHaveLength(0)
  })

  it('renders a textarea for SECRET_TEXTAREA (connecteurs-cible, lot B1)', async () => {
    const wrapper = mount(ConnectorConfigForm, {
      props: {
        schema: [
          {
            name: 'sftpPrivateKey',
            label: 'SFTP private key',
            type: 'SECRET_TEXTAREA',
            required: true,
          },
        ],
        modelValue: { sftpPrivateKey: '' },
      },
    })
    const field = wrapper.get('[data-testid="connector-config-field-sftpPrivateKey"]')
    expect(field.element.tagName).toBe('TEXTAREA')

    const multiline = '-----BEGIN OPENSSH PRIVATE KEY-----\nabc\n-----END OPENSSH PRIVATE KEY-----'
    await field.setValue(multiline)
    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toMatchObject({
      sftpPrivateKey: multiline,
    })
  })

  it('offers a blank option for a non-required SELECT', () => {
    const wrapper = mount(ConnectorConfigForm, {
      props: {
        schema: [
          {
            name: 'mode',
            label: 'Mode',
            type: 'SELECT',
            required: false,
            options: ['A', 'B'],
          },
        ],
        modelValue: { mode: '' },
      },
    })
    const options = wrapper.findAll('[data-testid="connector-config-field-mode"] option')
    expect(options[0]?.text()).toBe('—')
  })
})
