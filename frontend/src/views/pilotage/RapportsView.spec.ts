import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import RapportsView from './RapportsView.vue'
import * as slaReport from '@/api/slaReport'

vi.mock('@/api/slaReport', () => ({
  getSlaReport: vi.fn(),
  downloadSlaReportCsv: vi.fn(),
}))

describe('RapportsView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(slaReport.getSlaReport).mockResolvedValue({
      from: '2026-02-01T00:00:00Z',
      to: '2026-03-16T00:00:00Z',
      complianceTargetPercent: 95,
      compliancePercent: 94.2,
      overdueCount: 148,
      meanProcessingDays: 6.4,
      medianProcessingDays: 4.1,
      meanDeltaVsPreviousDays: -0.8,
      typeShares: [{ code: 'SIN', label: 'Sinistre', volume: 10, percent: 100 }],
      stepDurationsHours: [{ name: 'Instruction', meanHours: 37.6 }],
      managerLoads: [
        { assigneeId: null, displayName: 'Non assignés', inProgress: 48, overdue: 11, slaPercent: null },
      ],
      types: [
        {
          typeCode: 'SIN',
          typeLabel: 'Sinistre auto',
          workflowKey: 'instruction-sinistre',
          volume: 1042,
          meanDays: 7.1,
          slaPercent: 92.4,
          producedDocCount: 2108,
          volumeTrendPercent: 4,
        },
      ],
    })
  })

  it('renders KPI cards from the snapshot', async () => {
    const wrapper = mount(RapportsView)
    await wrapper.vm.$nextTick()
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Rapports & SLA')
    expect(wrapper.text()).toContain('94.2 %')
    expect(wrapper.text()).toContain('6.4 j')
    expect(wrapper.get('[data-testid="rapports-export"]').text()).toContain('Exporter CSV')
    expect(wrapper.get('[data-testid="rapports-schedule"]').text()).toContain("Planifier l'envoi")
    expect(wrapper.get('[data-testid="rapports-schedule"]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Sinistre')
    expect(wrapper.text()).toContain('100 %')
  })
})
