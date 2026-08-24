import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import { deployBpmn, getBpmnDeploymentInfo, getBpmnDraft, getDeployedBpmnXml, saveBpmnDraft } from './workflows'

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

describe('workflow BPMN draft api client', () => {
  beforeEach(() => {
    vi.mocked(axios.get).mockReset()
    vi.mocked(axios.put).mockReset()
  })

  it('returns the draft when one exists for the key (200)', async () => {
    vi.mocked(axios.get).mockResolvedValue({
      status: 200,
      data: { processDefinitionKey: 'wf', xml: '<definitions/>', updatedAt: '2026-01-01T00:00:00Z', updatedBy: 'admin' },
    })

    const draft = await getBpmnDraft('wf')

    expect(draft).toEqual({
      processDefinitionKey: 'wf',
      xml: '<definitions/>',
      updatedAt: '2026-01-01T00:00:00Z',
      updatedBy: 'admin',
    })
    expect(axios.get).toHaveBeenCalledWith('/api/workflow-bpmn-drafts/wf')
  })

  it('returns null when no draft exists for the key (204)', async () => {
    vi.mocked(axios.get).mockResolvedValue({ status: 204, data: '' })

    const draft = await getBpmnDraft('wf')

    expect(draft).toBeNull()
  })

  it('saves (upserts) a draft via PUT', async () => {
    vi.mocked(axios.put).mockResolvedValue({
      data: { processDefinitionKey: 'wf', xml: '<definitions/>', updatedAt: '2026-01-01T00:00:00Z', updatedBy: 'admin' },
    })

    const draft = await saveBpmnDraft('wf', '<definitions/>')

    expect(axios.put).toHaveBeenCalledWith('/api/workflow-bpmn-drafts/wf', { xml: '<definitions/>' })
    expect(draft.updatedBy).toBe('admin')
  })

  it('returns the deployed XML when a version is deployed for the key (200)', async () => {
    vi.mocked(axios.get).mockResolvedValue({ status: 200, data: '<definitions/>' })

    const xml = await getDeployedBpmnXml('wf')

    expect(xml).toBe('<definitions/>')
    expect(axios.get).toHaveBeenCalledWith('/api/workflow-bpmn-deployments/wf/xml')
  })

  it('returns null when no version is deployed for the key (204)', async () => {
    vi.mocked(axios.get).mockResolvedValue({ status: 204, data: '' })

    const xml = await getDeployedBpmnXml('wf')

    expect(xml).toBeNull()
  })
})
