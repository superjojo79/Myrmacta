import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as workflowsApi from '@/api/workflows'
import { useDmnDeployment } from './useDmnDeployment'

vi.mock('@/api/workflows')

describe('useDmnDeployment', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('loadInfo fetches deployment info for a decision key and exposes the latest version', async () => {
    vi.mocked(workflowsApi.getDecisionTableDeploymentInfo).mockResolvedValue({
      decisionKey: 'grilleEligibilite',
      latestVersion: 3,
    })

    const deployment = useDmnDeployment()
    await deployment.loadInfo('grilleEligibilite')

    expect(workflowsApi.getDecisionTableDeploymentInfo).toHaveBeenCalledWith('grilleEligibilite')
    expect(deployment.deploymentInfo.value).toEqual({
      decisionKey: 'grilleEligibilite',
      latestVersion: 3,
    })
  })

  it('loadInfo swallows a lookup error and leaves deploymentInfo null (never-deployed key)', async () => {
    vi.mocked(workflowsApi.getDecisionTableDeploymentInfo).mockRejectedValue(new Error('not found'))

    const deployment = useDmnDeployment()
    await deployment.loadInfo('grilleEligibilite')

    expect(deployment.deploymentInfo.value).toBeNull()
  })

  it('deploy() posts the file and returns/exposes the deployment result on success', async () => {
    const file = new File(['<definitions/>'], 'table.dmn', { type: 'application/xml' })
    vi.mocked(workflowsApi.deployDecisionTable).mockResolvedValue({
      decisionKey: 'grilleEligibilite',
      deployedVersion: 2,
      deploymentId: 'dep-2',
    })

    const deployment = useDmnDeployment()
    const result = await deployment.deploy(file)

    expect(workflowsApi.deployDecisionTable).toHaveBeenCalledWith(file)
    expect(result).toEqual({ decisionKey: 'grilleEligibilite', deployedVersion: 2, deploymentId: 'dep-2' })
    expect(deployment.deployedResult.value).toEqual(result)
    expect(deployment.deploying.value).toBe(false)
  })

  it('deploy() surfaces the server ProblemDetail message and returns null on failure', async () => {
    const file = new File(['<definitions/>'], 'table.dmn', { type: 'application/xml' })
    vi.mocked(workflowsApi.deployDecisionTable).mockRejectedValue({
      isAxiosError: true,
      response: { status: 400, data: { detail: 'DMN cell expression is not allowed' } },
    })

    const deployment = useDmnDeployment()
    const result = await deployment.deploy(file)

    expect(result).toBeNull()
    expect(deployment.deployError.value).toBe('DMN cell expression is not allowed')
    expect(deployment.deployedResult.value).toBeNull()
  })

  it('reset() clears info, error and result', async () => {
    vi.mocked(workflowsApi.getDecisionTableDeploymentInfo).mockResolvedValue({
      decisionKey: 'grilleEligibilite',
      latestVersion: 1,
    })
    const deployment = useDmnDeployment()
    await deployment.loadInfo('grilleEligibilite')

    deployment.reset()

    expect(deployment.deploymentInfo.value).toBeNull()
    expect(deployment.deployError.value).toBeNull()
    expect(deployment.deployedResult.value).toBeNull()
  })
})
