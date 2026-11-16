import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import {
  claimWorkflowTask,
  createWorkflowTypeConfig,
  deployDecisionTable,
  evaluateDecision,
  getDecisionTableDeploymentInfo,
  getDeployedDmnXml,
  getDmnDraft,
  getWorkflowDiagram,
  getWorkflowHistory,
  getWorkflowTasks,
  getWorkflowTypeConfig,
  listAllWorkflowTypeConfigs,
  listAssignableUsers,
  listDecisionTableVersions,
  listStartVariables,
  listWorkflowTypeConfigs,
  reassignWorkflowTask,
  reassignWorkflowTaskToGroup,
  redeployDecisionTable,
  saveDmnDraft,
  testDecisionCsv,
  testDecisionJson,
  updateWorkflowTypeConfig,
} from './workflows'
import type { WorkflowTypeConfig } from './types'

// Le client d'API workflows (hors déploiement BPMN, couvert par workflowBpmnDeployment.spec.ts) : URL, paramètres et
// corps exacts, et le 204 « rien » rendu en null. Les vues le simulent toutes (vi.mock('@/api/workflows')).
vi.mock('axios')

const ok = (data: unknown, status = 200) => ({ status, data })

describe('workflow api client', () => {
  beforeEach(() => {
    vi.mocked(axios.get).mockReset()
    vi.mocked(axios.post).mockReset()
    vi.mocked(axios.put).mockReset()
    vi.mocked(axios.patch).mockReset()
  })

  describe('workflow links', () => {
    it('reads the diagram of a link', async () => {
      vi.mocked(axios.get).mockResolvedValue(ok({ bpmnXml: '<x/>' }))
      expect(await getWorkflowDiagram('l1')).toEqual({ bpmnXml: '<x/>' })
      expect(axios.get).toHaveBeenCalledWith('/api/workflow-links/l1/diagram')
    })

    it('pages the history and the tasks of a link', async () => {
      vi.mocked(axios.get).mockResolvedValue(ok({ content: [1] }))
      expect(await getWorkflowHistory('l1', { page: 2, size: 5 })).toEqual({ content: [1] })
      expect(axios.get).toHaveBeenLastCalledWith('/api/workflow-links/l1/history', { params: { page: 2, size: 5 } })
      expect(await getWorkflowTasks('l2', { page: 0, size: 10 })).toEqual({ content: [1] })
      expect(axios.get).toHaveBeenLastCalledWith('/api/workflow-links/l2/tasks', { params: { page: 0, size: 10 } })
      await getWorkflowTasks('l3')
      expect(axios.get).toHaveBeenLastCalledWith('/api/workflow-links/l3/tasks', { params: { page: undefined, size: undefined } })
    })

    it('reassigns, claims and hands a task to a group', async () => {
      vi.mocked(axios.post).mockResolvedValue(ok(undefined))
      await reassignWorkflowTask('l1', 't1', 'u7')
      expect(axios.post).toHaveBeenLastCalledWith('/api/workflow-links/l1/tasks/t1/reassign', { assigneeId: 'u7' })
      await claimWorkflowTask('l1', 't1')
      expect(axios.post).toHaveBeenLastCalledWith('/api/workflow-links/l1/tasks/t1/claim')
      await reassignWorkflowTaskToGroup('l1', 't1', 'IT')
      expect(axios.post).toHaveBeenLastCalledWith('/api/workflow-links/l1/tasks/t1/reassign-group', { groupCode: 'IT' })
    })

    it('lists the users a task can be assigned to', async () => {
      vi.mocked(axios.get).mockResolvedValue(ok([{ id: 'u1' }]))
      expect(await listAssignableUsers('l1')).toEqual([{ id: 'u1' }])
      expect(axios.get).toHaveBeenCalledWith('/api/workflow-links/l1/assignable-users')
    })
  })

  describe('workflow type configurations', () => {
    const config = { workflowKey: 'wf' } as unknown as WorkflowTypeConfig

    it('sends only the paging parameters that are set', async () => {
      vi.mocked(axios.get).mockResolvedValue(ok({ content: [] }))
      await listWorkflowTypeConfigs({ page: 1, size: 20 })
      expect(axios.get).toHaveBeenLastCalledWith('/api/workflow-type-configs', { params: { page: 1, size: 20 } })
      await listWorkflowTypeConfigs({ page: 0 })
      expect(axios.get).toHaveBeenLastCalledWith('/api/workflow-type-configs', { params: { page: 0 } })
      await listWorkflowTypeConfigs({ size: 0 })
      expect(axios.get).toHaveBeenLastCalledWith('/api/workflow-type-configs', { params: { size: 0 } })
      await listWorkflowTypeConfigs()
      expect(axios.get).toHaveBeenLastCalledWith('/api/workflow-type-configs', { params: {} })
    })

    it('reads every page of configurations', async () => {
      vi.mocked(axios.get).mockResolvedValue(ok({ content: [config], totalPages: 1, number: 0, last: true }))
      expect(await listAllWorkflowTypeConfigs()).toEqual([config])
      expect(axios.get).toHaveBeenCalledWith('/api/workflow-type-configs', { params: expect.objectContaining({ page: 0 }) })
    })

    it('reads, creates and updates a configuration', async () => {
      vi.mocked(axios.get).mockResolvedValue(ok(config))
      vi.mocked(axios.post).mockResolvedValue(ok(config))
      vi.mocked(axios.patch).mockResolvedValue(ok(config))
      expect(await getWorkflowTypeConfig('wf')).toBe(config)
      expect(axios.get).toHaveBeenCalledWith('/api/workflow-type-configs/wf')
      expect(await createWorkflowTypeConfig(config)).toBe(config)
      expect(axios.post).toHaveBeenCalledWith('/api/workflow-type-configs', config)
      expect(await updateWorkflowTypeConfig('wf', config)).toBe(config)
      expect(axios.patch).toHaveBeenCalledWith('/api/workflow-type-configs/wf', config)
    })

    it('lists the start variables declared on a deployed process', async () => {
      const variables = [{ name: 'montant', label: 'Montant', required: true }]
      vi.mocked(axios.get).mockResolvedValue(ok({ variables }))
      expect(await listStartVariables('wf')).toEqual(variables)
      expect(axios.get).toHaveBeenCalledWith('/api/workflow-start-variables', { params: { processDefinitionKey: 'wf' } })
    })
  })

  describe('decision tables', () => {
    it('reads the deployment info, or null when the key was never deployed (204)', async () => {
      vi.mocked(axios.get).mockResolvedValueOnce(ok({ latestVersion: 2 }))
      expect(await getDecisionTableDeploymentInfo('d')).toEqual({ latestVersion: 2 })
      expect(axios.get).toHaveBeenCalledWith('/api/dmn-decision-deployments/info', { params: { decisionKey: 'd' } })
      vi.mocked(axios.get).mockResolvedValueOnce(ok('', 204))
      expect(await getDecisionTableDeploymentInfo('d')).toBeNull()
    })

    it('deploys a DMN file as multipart form data', async () => {
      vi.mocked(axios.post).mockResolvedValue(ok({ deployedVersion: 3 }))
      const file = new File(['<definitions/>'], 'd.dmn')
      expect(await deployDecisionTable(file)).toEqual({ deployedVersion: 3 })
      const [url, form, options] = vi.mocked(axios.post).mock.calls[0]!
      expect(url).toBe('/api/dmn-decision-deployments')
      expect((form as FormData).get('file')).toBe(file)
      expect(options).toEqual({ headers: { 'Content-Type': 'multipart/form-data' } })
    })

    it('reads and saves a draft; no draft (204) is null', async () => {
      vi.mocked(axios.get).mockResolvedValueOnce(ok({ xml: '<d/>' }))
      expect(await getDmnDraft('d')).toEqual({ xml: '<d/>' })
      expect(axios.get).toHaveBeenCalledWith('/api/dmn-decision-drafts/d')
      vi.mocked(axios.get).mockResolvedValueOnce(ok('', 204))
      expect(await getDmnDraft('d')).toBeNull()
      vi.mocked(axios.put).mockResolvedValue(ok({ xml: '<e/>' }))
      expect(await saveDmnDraft('d', '<e/>')).toEqual({ xml: '<e/>' })
      expect(axios.put).toHaveBeenCalledWith('/api/dmn-decision-drafts/d', { xml: '<e/>' })
    })

    it('reads the deployed XML; nothing deployed (204) is null', async () => {
      vi.mocked(axios.get).mockResolvedValueOnce(ok('<definitions/>'))
      expect(await getDeployedDmnXml('d')).toBe('<definitions/>')
      expect(axios.get).toHaveBeenCalledWith('/api/dmn-decision-deployments/d/xml')
      vi.mocked(axios.get).mockResolvedValueOnce(ok('', 204))
      expect(await getDeployedDmnXml('d')).toBeNull()
    })

    it('lists the versions, encoding the key', async () => {
      vi.mocked(axios.get).mockResolvedValue(ok([{ version: 1 }]))
      expect(await listDecisionTableVersions('a b')).toEqual([{ version: 1 }])
      expect(axios.get).toHaveBeenCalledWith('/api/dmn-decision-deployments/a%20b/versions')
    })

    it('evaluates the latest version, or a chosen one', async () => {
      vi.mocked(axios.post).mockResolvedValue(ok({ rows: [] }))
      expect(await evaluateDecision('d', { x: 1 }, null)).toEqual({ rows: [] })
      expect(axios.post).toHaveBeenLastCalledWith('/api/dmn-decision-deployments/evaluate', { inputs: { x: 1 } },
        { params: { decisionKey: 'd' } })
      await evaluateDecision('d', { x: 1 }, 2)
      expect(axios.post).toHaveBeenLastCalledWith('/api/dmn-decision-deployments/evaluate', { inputs: { x: 1 } },
        { params: { decisionKey: 'd', version: 2 } })
    })

    it('runs a test suite given as JSON cases or as CSV', async () => {
      vi.mocked(axios.post).mockResolvedValue(ok({ passed: 1 }))
      const cases = [{ inputs: { x: 1 }, expected: { y: 2 } }] as never
      expect(await testDecisionJson('d', cases, null)).toEqual({ passed: 1 })
      expect(axios.post).toHaveBeenLastCalledWith('/api/dmn-decision-deployments/test', { cases },
        { params: { decisionKey: 'd' } })
      await testDecisionJson('d', cases, 4)
      expect(axios.post).toHaveBeenLastCalledWith('/api/dmn-decision-deployments/test', { cases },
        { params: { decisionKey: 'd', version: 4 } })
      expect(await testDecisionCsv('d', 'x,y\n1,2', null)).toEqual({ passed: 1 })
      expect(axios.post).toHaveBeenLastCalledWith('/api/dmn-decision-deployments/test', 'x,y\n1,2',
        { params: { decisionKey: 'd' }, headers: { 'Content-Type': 'text/csv' } })
      await testDecisionCsv('d', 'x,y\n1,2', 3)
      expect(axios.post).toHaveBeenLastCalledWith('/api/dmn-decision-deployments/test', 'x,y\n1,2',
        { params: { decisionKey: 'd', version: 3 }, headers: { 'Content-Type': 'text/csv' } })
    })

    it('redeploys a previous version, encoding the key', async () => {
      vi.mocked(axios.post).mockResolvedValue(ok({ deployedVersion: 5 }))
      expect(await redeployDecisionTable('a b', 1)).toEqual({ deployedVersion: 5 })
      expect(axios.post).toHaveBeenCalledWith('/api/dmn-decision-deployments/a%20b/redeploy/1')
    })
  })
})
