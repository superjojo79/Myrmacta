import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import ObservabilityAdminView from './ObservabilityAdminView.vue'
import * as api from '@/api/observability'
import { routes } from '@/app/routes'
import type { ObservabilitySnapshot } from '@/api/observability'

vi.mock('@/api/observability')

function snapshot(overrides: Partial<ObservabilitySnapshot> = {}): ObservabilitySnapshot {
  return {
    tenantId: 'tenant_dev',
    observedAt: '2026-08-20T07:41:00Z',
    triggerOutboxOldestPendingAgeSeconds: 12,
    notificationOutboxOldestPendingAgeSeconds: 0,
    purgeOutboxOldestUnfinishedAgeSeconds: 3600,
    flowableDeadLetterJobCount: 0,
    flowableExceptionJobCount: 1,
    pdfRender: { count: 0, meanMs: null, maxMs: null },
    webhookDeadCount: 2,
    sseActiveCount: 3,
    severity: {
      triggerOutboxOldestPendingAge: 'ok',
      notificationOutboxOldestPendingAge: 'ok',
      purgeOutboxOldestUnfinishedAge: 'crit',
      flowableDeadLetterJobs: 'ok',
      flowableExceptionJobs: 'warn',
      pdfRender: 'ok',
      webhookDead: 'warn',
      sseActive: 'ok',
    },
    ...overrides,
  }
}

describe('ObservabilityAdminView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('renders kpis including three ages and n/a pdf, without force buttons', async () => {
    vi.mocked(api.getObservabilitySnapshot).mockResolvedValue(snapshot())
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/admin/observabilite')
    await router.isReady()
    const wrapper = mount(ObservabilityAdminView, { global: { plugins: [router] } })
    await flushPromises()
    expect(wrapper.text()).toContain('Observabilité')
    expect(wrapper.get('[data-testid="kpi-trigger-outbox-age"]').text()).toContain('12 s')
    expect(wrapper.get('[data-testid="kpi-notification-outbox-age"]').text()).toContain('0 s')
    expect(wrapper.get('[data-testid="kpi-purge-outbox-age"]').text()).toContain('3600 s')
    expect(wrapper.get('[data-testid="kpi-purge-outbox-age"]').classes()).toContain('kpi-card--crit')
    expect(wrapper.get('[data-testid="kpi-pdf-render"]').text()).toContain('n/a')
    expect(wrapper.text()).not.toContain('Forcer')
    expect(wrapper.text()).not.toContain('Relancer')
  })

  it('refresh button re-fetches', async () => {
    vi.mocked(api.getObservabilitySnapshot).mockResolvedValue(snapshot())
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/admin/observabilite')
    await router.isReady()
    const wrapper = mount(ObservabilityAdminView, { global: { plugins: [router] } })
    await flushPromises()
    await wrapper.get('[data-testid="observability-refresh"]').trigger('click')
    await flushPromises()
    expect(api.getObservabilitySnapshot).toHaveBeenCalledTimes(2)
  })

  it('shows problem detail on error', async () => {
    vi.mocked(api.getObservabilitySnapshot).mockRejectedValue({
      response: { data: { detail: 'Accès refusé' }, status: 403 },
    })
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/admin/observabilite')
    await router.isReady()
    const wrapper = mount(ObservabilityAdminView, { global: { plugins: [router] } })
    await flushPromises()
    expect(wrapper.text()).toContain('Accès refusé')
  })
})
