import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ConnectorIngressUrl from './ConnectorIngressUrl.vue'

describe('ConnectorIngressUrl', () => {
  beforeEach(() => {
    vi.stubGlobal('navigator', {
      clipboard: { writeText: vi.fn().mockResolvedValue(undefined) },
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows the webhook URL as a POST partner endpoint', () => {
    const wrapper = mount(ConnectorIngressUrl, {
      props: {
        kind: 'webhook',
        url: 'https://example.com/acme/api/webhooks/c-1',
      },
    })
    expect(wrapper.get('[data-testid="connector-ingress"]').text()).toMatch(/URL webhook/)
    expect(wrapper.get('[data-testid="connector-ingress-url"]').text()).toBe(
      'POST https://example.com/acme/api/webhooks/c-1',
    )
  })

  it('shows the callback URL for a flowable-callback instance', () => {
    const wrapper = mount(ConnectorIngressUrl, {
      props: {
        kind: 'callback',
        url: 'https://example.com/acme/api/callbacks/cb-9',
      },
    })
    expect(wrapper.get('[data-testid="connector-ingress"]').text()).toMatch(/URL callback/)
    expect(wrapper.get('[data-testid="connector-ingress-url"]').text()).toBe(
      'POST https://example.com/acme/api/callbacks/cb-9',
    )
  })

  it('copies the URL to the clipboard', async () => {
    const wrapper = mount(ConnectorIngressUrl, {
      props: {
        kind: 'webhook',
        url: 'https://example.com/api/webhooks/c-1',
      },
    })
    await wrapper.get('[data-testid="connector-ingress-copy"]').trigger('click')
    await flushPromises()
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith(
      'https://example.com/api/webhooks/c-1',
    )
    expect(wrapper.get('[data-testid="connector-ingress-copy"]').text()).toBe('Copié')
  })
})
