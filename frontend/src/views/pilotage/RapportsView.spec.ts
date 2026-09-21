import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import RapportsView from './RapportsView.vue'
import * as slaReport from '@/api/slaReport'
import * as dossierTypes from '@/api/dossierTypes'
import * as operations from '@/api/operations'
import * as biExports from '@/api/biExports'

vi.mock('@/api/slaReport', () => ({
  getSlaReport: vi.fn(),
  downloadSlaReportCsv: vi.fn(),
}))

vi.mock('@/api/dossierTypes', () => ({
  listAllDossierTypes: vi.fn(),
}))

vi.mock('@/api/operations', () => ({
  getOperationalCounts: vi.fn(),
}))

vi.mock('@/api/biExports', () => ({
  listBiExports: vi.fn(),
  downloadBiExport: vi.fn(),
}))

const snapshot = {
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
}

describe('RapportsView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(slaReport.getSlaReport).mockResolvedValue(snapshot)
    vi.mocked(slaReport.downloadSlaReportCsv).mockResolvedValue(undefined)
    vi.mocked(operations.getOperationalCounts).mockResolvedValue({
      deadLetterJobCount: 2,
      unassignedOpenTaskCount: 4,
    })
    vi.mocked(biExports.listBiExports).mockResolvedValue({
      content: [
        {
          id: 'bi-1',
          kind: 'SLA',
          dossierTypeCode: null,
          fileName: 'sla-report-2026.csv',
          createdAt: '2026-08-01T00:00:00Z',
          sizeBytes: 12,
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
    vi.mocked(biExports.downloadBiExport).mockResolvedValue(undefined)
    vi.mocked(dossierTypes.listAllDossierTypes).mockResolvedValue([
      {
        id: 't1',
        tenantId: 'tenant_dev',
        code: 'CLAIM',
        label: 'Sinistre',
        workflowKey: 'claimWorkflow',
        active: true,
        allowUnknownFields: false,
        fieldDefinitions: [],
      },
    ])
  })

  it('renders KPI cards from the snapshot', async () => {
    const wrapper = mount(RapportsView)
    await flushPromises()
    expect(wrapper.text()).toContain('Rapports & SLA')
    expect(wrapper.text()).toContain('94.2 %')
    expect(wrapper.text()).toContain('6.4 j')
    expect(wrapper.get('[data-testid="rapports-export"]').text()).toContain('Exporter CSV')
    expect(wrapper.get('[data-testid="rapports-schedule"]').text()).toContain("Planifier l'envoi")
    expect(wrapper.get('[data-testid="rapports-schedule"]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Sinistre')
    expect(wrapper.text()).toContain('100 %')
    expect(wrapper.get('[data-testid="kpi-dead-letter"]').text()).toContain('2')
    expect(wrapper.get('[data-testid="kpi-unassigned"]').text()).toContain('4')
    expect(wrapper.get('[data-testid="rapports-bi-exports"]').text()).toContain('sla-report-2026.csv')
  })

  it('sends datetime-local bounds as UTC Instant and optional type filter', async () => {
    const wrapper = mount(RapportsView)
    await flushPromises()
    expect(slaReport.getSlaReport).toHaveBeenCalledWith({
      from: undefined,
      to: undefined,
      dossierTypeCode: undefined,
    })

    await wrapper.get('[data-testid="rapports-from"]').setValue('2026-03-01T00:00')
    await wrapper.get('[data-testid="rapports-to"]').setValue('2026-03-15T23:59')
    await wrapper.get('[data-testid="rapports-type"]').setValue('CLAIM')
    await wrapper.get('[data-testid="rapports-refresh"]').trigger('click')
    await flushPromises()

    expect(slaReport.getSlaReport).toHaveBeenLastCalledWith({
      from: '2026-03-01T00:00:00Z',
      to: '2026-03-15T23:59:00Z',
      dossierTypeCode: 'CLAIM',
    })
  })
})
