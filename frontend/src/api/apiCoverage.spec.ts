import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import {
  createDossier,
  createDossierRelation,
  getDossierById,
  getDossierByRef,
  listDossierElements,
  listDossierRelations,
  listDossierWorkflows,
  startDossierWorkflow,
  updateDossierStatus,
} from './dossiers'
import {
  createDossierType,
  getDossierType,
  grantDossierTypeAcl,
  listDossierTypes,
  revokeDossierTypeAcl,
  updateDossierType,
} from './dossierTypes'
import {
  createWorkflowTypeConfig,
  getWorkflowDiagram,
  getWorkflowHistory,
  getWorkflowTasks,
  getWorkflowTypeConfig,
  listWorkflowTypeConfigs,
  reassignWorkflowTask,
  updateWorkflowTypeConfig,
} from './workflows'
import {
  createConnector,
  createDocumentTemplate,
  deleteConnector,
  listConnectors,
  listDocumentTemplates,
  updateConnector,
  updateDocumentTemplate,
} from './admin'
import { downloadElementContent } from './elements'
import { completeTask, listMyTasks } from './tasks'
import { previewHtml, previewPdf } from './documents'
import { enqueueDossierPurge, listPurgeOutbox } from './purgeOutbox'

vi.mock('axios')

describe('api coverage', () => {
  beforeEach(() => {
    vi.mocked(axios.get).mockReset().mockResolvedValue({ data: {} })
    vi.mocked(axios.post).mockReset().mockResolvedValue({ data: {} })
    vi.mocked(axios.patch).mockReset().mockResolvedValue({ data: {} })
    vi.mocked(axios.delete).mockReset().mockResolvedValue({})
  })

  it('covers dossiers endpoints', async () => {
    await createDossier({ dossierTypeCode: 'C', label: 'L', fields: {} })
    await getDossierById('d1')
    await getDossierByRef('DOS-1')
    await updateDossierStatus('d1', 'OUVERT', 1)
    await listDossierElements('d1')
    await listDossierRelations('d1')
    await createDossierRelation('d1', { targetDossierId: 'd2', type: 'LINKED' })
    await listDossierWorkflows('d1')
    await startDossierWorkflow('d1', 'wf', {}, 'k')
    expect(axios.get).toHaveBeenCalled()
    expect(axios.post).toHaveBeenCalled()
    expect(axios.patch).toHaveBeenCalled()
  })

  it('covers dossier types and ACL', async () => {
    await listDossierTypes()
    await getDossierType('CLAIM')
    await createDossierType({ code: 'C', label: 'L', workflowKey: 'w' })
    await updateDossierType('C', { label: 'X' })
    await grantDossierTypeAcl('C', 'u', 'EDIT')
    await revokeDossierTypeAcl('C', 'u')
    expect(axios.delete).toHaveBeenCalled()
  })

  it('covers workflows viz and configs', async () => {
    await getWorkflowDiagram('l')
    await getWorkflowHistory('l')
    await getWorkflowTasks('l')
    await reassignWorkflowTask('l', 't', 'u')
    await listWorkflowTypeConfigs()
    await getWorkflowTypeConfig('wf')
    await createWorkflowTypeConfig({
      workflowKey: 'wf',
      label: 'L',
      statusDefinitions: [],
      allowedDossierStatuses: [],
      onStartDossierStatus: null,
      observerUserIds: [],
    })
    await updateWorkflowTypeConfig('wf', {
      workflowKey: 'wf',
      label: 'L2',
      statusDefinitions: [],
      allowedDossierStatuses: [],
      onStartDossierStatus: null,
      observerUserIds: [],
    })
    expect(axios.patch).toHaveBeenCalled()
  })

  it('covers admin, tasks, elements', async () => {
    await listDocumentTemplates('k', true)
    await createDocumentTemplate({ templateKey: 'k', label: 'L', htmlContent: '<p/>' })
    await updateDocumentTemplate('id', { label: 'L2' })
    await listConnectors()
    await createConnector({ name: 'n', connectorType: 'HTTP' })
    await updateConnector('c1', { enabled: false })
    await deleteConnector('c1')
    await listMyTasks()
    await completeTask('t')
    await downloadElementContent('e1')
    await previewHtml({ htmlContent: '<p/>' })
    await previewPdf({ htmlContent: '<p/>' })
    await listPurgeOutbox()
    await enqueueDossierPurge('d1')
    expect(axios.delete).toHaveBeenCalled()
  })
})
