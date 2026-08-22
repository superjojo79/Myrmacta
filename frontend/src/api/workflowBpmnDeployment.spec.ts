import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import { deployBpmn, getBpmnDeploymentInfo } from './workflows'

vi.mock('axios')

describe('workflow BPMN deployment api client', () => {
  beforeEach(() => {
    vi.mocked(axios.get).mockReset()
    vi.mocked(axios.post).mockReset()
  })

  it('returns the deployment info when the key is already deployed (200)', async () => {
    vi.mocked(axios.get).mockResolvedValue({
      status: 200,
      data: { processDefinitionKey: 'wf', latestVersion: 3, activeInstanceCount: 12 },
    })

    const info = await getBpmnDeploymentInfo('wf')

    expect(info).toEqual({ processDefinitionKey: 'wf', latestVersion: 3, activeInstanceCount: 12 })
    expect(axios.get).toHaveBeenCalledWith('/api/workflow-bpmn-deployments/info', {
      params: { processDefinitionKey: 'wf' },
    })
  })

  it('returns null when the key was never deployed (204)', async () => {
    vi.mocked(axios.get).mockResolvedValue({ status: 204, data: '' })

    const info = await getBpmnDeploymentInfo('neverDeployed')

    expect(info).toBeNull()
  })

  it('deploys a BPMN file as multipart/form-data', async () => {
    vi.mocked(axios.post).mockResolvedValue({
      data: { processDefinitionKey: 'wf', deployedVersion: 1, deploymentId: 'dep-1' },
    })
    const file = new File(['<definitions/>'], 'diagram.bpmn20.xml', { type: 'application/xml' })

    const result = await deployBpmn(file)

    expect(result).toEqual({ processDefinitionKey: 'wf', deployedVersion: 1, deploymentId: 'dep-1' })
    expect(axios.post).toHaveBeenCalledWith(
      '/api/workflow-bpmn-deployments',
      expect.any(FormData),
      { headers: { 'Content-Type': 'multipart/form-data' } },
    )
    const sentForm = vi.mocked(axios.post).mock.calls[0][1] as FormData
    expect(sentForm.get('file')).toBe(file)
  })
})
