import axios from 'axios'

export interface OperationalCounts {
  deadLetterJobCount: number
  unassignedOpenTaskCount: number
}

export async function getOperationalCounts(): Promise<OperationalCounts> {
  const { data } = await axios.get<OperationalCounts>('/api/reports/operations')
  return data
}
