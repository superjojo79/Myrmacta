import axios from 'axios'

export interface TypeShare {
  code: string
  label: string
  volume: number
  percent: number
}

export interface StepDuration {
  name: string
  meanHours: number
}

export interface ManagerLoad {
  assigneeId: string | null
  displayName: string
  inProgress: number
  overdue: number
  slaPercent: number | null
}

export interface TypeBreakdown {
  typeCode: string
  typeLabel: string
  workflowKey: string
  volume: number
  meanDays: number | null
  slaPercent: number | null
  producedDocCount: number
  volumeTrendPercent: number | null
}

export interface SlaReport {
  from: string
  to: string
  complianceTargetPercent: number
  compliancePercent: number | null
  overdueCount: number
  meanProcessingDays: number | null
  medianProcessingDays: number | null
  meanDeltaVsPreviousDays: number | null
  typeShares: TypeShare[]
  stepDurationsHours: StepDuration[]
  managerLoads: ManagerLoad[]
  types: TypeBreakdown[]
}

export async function getSlaReport(params: {
  from?: string
  to?: string
  dossierTypeCode?: string
} = {}): Promise<SlaReport> {
  const { data } = await axios.get<SlaReport>('/api/reports/sla', { params })
  return data
}

export async function downloadSlaReportCsv(params: {
  from?: string
  to?: string
  dossierTypeCode?: string
} = {}): Promise<void> {
  const { data } = await axios.get<Blob>('/api/reports/sla.csv', { params, responseType: 'blob' })
  const url = URL.createObjectURL(data)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = 'sla-report.csv'
  anchor.click()
  URL.revokeObjectURL(url)
}
