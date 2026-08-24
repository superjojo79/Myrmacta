import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as workflowsApi from '@/api/workflows'
import { useBpmnDeployment } from './useBpmnDeployment'

vi.mock('@/api/workflows')

describe('useBpmnDeployment', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('loadInfo fetches deployment info for a process key and exposes version/instances', async () => {
    vi.mocked(workflowsApi.getBpmnDeploymentInfo).mockResolvedValue({
      processDefinitionKey: 'wf',
      latestVersion: 3,
      activeInstanceCount: 12,
    })

    const deployment = useBpmnDeployment()
    await deployment.loadInfo('wf')

    expect(workflowsApi.getBpmnDeploymentInfo).toHaveBeenCalledWith('wf')
    expect(deployment.deploymentInfo.value).toEqual({
      processDefinitionKey: 'wf',
      latestVersion: 3,
      activeInstanceCount: 12,
    })
  })

  it('loadInfo swallows a lookup error and leaves deploymentInfo null (never-deployed key)', async () => {
    vi.mocked(workflowsApi.getBpmnDeploymentInfo).mockRejectedValue(new Error('not found'))

    const deployment = useBpmnDeployment()
    await deployment.loadInfo('wf')

    expect(deployment.deploymentInfo.value).toBeNull()
  })

  it('deploy() posts the file and returns/exposes the deployment result on success', async () => {
    const file = new File(['<definitions/>'], 'diagram.bpmn20.xml', { type: 'application/xml' })
    vi.mocked(workflowsApi.deployBpmn).mockResolvedValue({
      processDefinitionKey: 'wf',
      deployedVersion: 2,
      deploymentId: 'dep-2',
    })

    const deployment = useBpmnDeployment()
    const result = await deployment.deploy(file)

    expect(workflowsApi.deployBpmn).toHaveBeenCalledWith(file)
    expect(result).toEqual({ processDefinitionKey: 'wf', deployedVersion: 2, deploymentId: 'dep-2' })
    expect(deployment.deployedResult.value).toEqual(result)
    expect(deployment.deploying.value).toBe(false)
  })

  it('deploy() surfaces the server ProblemDetail message and returns null on failure', async () => {
    const file = new File(['<definitions/>'], 'diagram.bpmn20.xml', { type: 'application/xml' })
    vi.mocked(workflowsApi.deployBpmn).mockRejectedValue({
      isAxiosError: true,
      response: { status: 404, data: { detail: 'No WorkflowTypeConfig found for key: wf' } },
    })

    const deployment = useBpmnDeployment()
    const result = await deployment.deploy(file)

    expect(result).toBeNull()
    expect(deployment.deployError.value).toBe('No WorkflowTypeConfig found for key: wf')
    expect(deployment.deployedResult.value).toBeNull()
  })
})
