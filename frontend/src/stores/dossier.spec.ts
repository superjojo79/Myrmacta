import { describe, expect, it, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useDossierStore } from './dossier'
import * as dossiersApi from '@/api/dossiers'
import * as typesApi from '@/api/dossierTypes'
import * as workflowsApi from '@/api/workflows'
import * as elementsApi from '@/api/elements'

vi.mock('@/api/dossiers')
vi.mock('@/api/dossierTypes')
vi.mock('@/api/workflows')
vi.mock('@/api/tasks')
vi.mock('@/api/elements')

describe('dossier store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
  })

  it('loads dossier by id and related data', async () => {
    vi.mocked(dossiersApi.getDossierById).mockResolvedValue({
      id: 'd1',
      tenantId: 't',
      reference: 'DOS-1',
      dossierTypeCode: 'CLAIM',
      label: 'L',
      status: 'DRAFT',
      fields: {},
      currentWorkflowLinkId: null,
      version: 1,
      createdBy: 'u',
      createdAt: '',
      updatedBy: 'u',
      updatedAt: '',
      closedBy: null,
      closedAt: null,
    })
    vi.mocked(typesApi.getDossierType).mockResolvedValue({
      id: 't1',
      tenantId: 't',
      code: 'CLAIM',
      label: 'Claim',
      workflowKey: 'wf',
      active: true,
      allowUnknownFields: false,
      fieldDefinitions: [],
    })
    vi.mocked(dossiersApi.listDossierWorkflows).mockResolvedValue([])
    vi.mocked(dossiersApi.listDossierElements).mockResolvedValue([])
    vi.mocked(dossiersApi.listDossierRelations).mockResolvedValue([])

    const store = useDossierStore()
    await store.loadById('d1')
    expect(store.dossier?.reference).toBe('DOS-1')
    expect(store.dossierType?.workflowKey).toBe('wf')
  })

  it('linkDossier prepends the created relation without reload', async () => {
    const store = useDossierStore()
    store.dossier = {
      id: 'd1',
      tenantId: 't',
      reference: 'DOS-1',
      dossierTypeCode: 'CLAIM',
      label: 'L',
      status: 'DRAFT',
      fields: {},
      currentWorkflowLinkId: null,
      version: 1,
      createdBy: 'u',
      createdAt: '',
      updatedBy: 'u',
      updatedAt: '',
      closedBy: null,
      closedAt: null,
    }
    store.relations = []
    vi.mocked(dossiersApi.createDossierRelation).mockResolvedValue({
      id: 'r1',
      type: 'LINKED',
      direction: 'OUTGOING',
      relatedDossier: {
        id: 'd2',
        reference: 'DOS-2',
        label: 'Other',
        dossierTypeCode: 'FAC',
        dossierTypeLabel: 'Facture',
        status: 'OUVERT',
        createdAt: '',
        updatedAt: '',
      },
      createdBy: 'u',
      createdAt: '',
    })
    await store.linkDossier('d2', 'LINKED')
    expect(dossiersApi.createDossierRelation).toHaveBeenCalledWith('d1', {
      targetDossierId: 'd2',
      type: 'LINKED',
    })
    expect(store.relations[0]?.id).toBe('r1')
  })

  it('addElement appends the created element without reload', async () => {
    const store = useDossierStore()
    store.dossier = {
      id: 'd1',
      tenantId: 't',
      reference: 'DOS-1',
      dossierTypeCode: 'CLAIM',
      label: 'L',
      status: 'OUVERT',
      fields: {},
      currentWorkflowLinkId: null,
      version: 1,
      createdBy: 'u',
      createdAt: '',
      updatedBy: 'u',
      updatedAt: '',
      closedBy: null,
      closedAt: null,
    }
    store.elements = []
    vi.mocked(dossiersApi.addDossierElement).mockResolvedValue({
      id: 'e1',
      type: 'NOTE',
      label: 'Rappel',
      addedBy: 'u',
      addedAt: '',
      workflowStepId: null,
      metadata: { text: 'x' },
      downloadable: false,
    })
    await store.addElement('NOTE', 'Rappel', { text: 'x' })
    expect(dossiersApi.addDossierElement).toHaveBeenCalledWith('d1', {
      type: 'NOTE',
      label: 'Rappel',
      metadata: { text: 'x' },
    })
    expect(store.elements).toHaveLength(1)
    expect(store.elements[0]?.id).toBe('e1')
  })

  it('addAttachment appends the created element without reload', async () => {
    const store = useDossierStore()
    store.dossier = {
      id: 'd1',
      tenantId: 't',
      reference: 'DOS-1',
      dossierTypeCode: 'CLAIM',
      label: 'L',
      status: 'OUVERT',
      fields: {},
      currentWorkflowLinkId: null,
      version: 1,
      createdBy: 'u',
      createdAt: '',
      updatedBy: 'u',
      updatedAt: '',
      closedBy: null,
      closedAt: null,
    }
    store.elements = []
    const file = new File(['%PDF'], 'doc.pdf', { type: 'application/pdf' })
    vi.mocked(elementsApi.uploadAttachment).mockResolvedValue({
      id: 'e2',
      type: 'ATTACHMENT',
      label: 'Justificatif',
      addedBy: 'u',
      addedAt: '',
      workflowStepId: null,
      metadata: { filename: 'doc.pdf', contentType: 'application/pdf', sizeBytes: 4 },
      downloadable: true,
    })
    await store.addAttachment(file, 'Justificatif')
    expect(elementsApi.uploadAttachment).toHaveBeenCalledWith('d1', file, 'Justificatif')
    expect(store.elements).toHaveLength(1)
    expect(store.elements[0]?.id).toBe('e2')
  })

  it('patches status with expectedVersion from current dossier', async () => {
    const store = useDossierStore()
    store.dossier = {
      id: 'd1',
      tenantId: 't',
      reference: 'DOS-1',
      dossierTypeCode: 'CLAIM',
      label: 'L',
      status: 'DRAFT',
      fields: {},
      currentWorkflowLinkId: null,
      version: 3,
      createdBy: 'u',
      createdAt: '',
      updatedBy: 'u',
      updatedAt: '',
      closedBy: null,
      closedAt: null,
    }
    vi.mocked(dossiersApi.updateDossierStatus).mockResolvedValue({
      ...store.dossier,
      status: 'OUVERT',
      version: 4,
    })
    await store.patchStatus('OUVERT')
    expect(dossiersApi.updateDossierStatus).toHaveBeenCalledWith('d1', 'OUVERT', 3)
    expect(store.dossier?.version).toBe(4)
  })

  it('patches fields with expectedVersion from current dossier', async () => {
    const store = useDossierStore()
    store.dossier = {
      id: 'd1',
      tenantId: 't',
      reference: 'DOS-1',
      dossierTypeCode: 'CLAIM',
      label: 'L',
      status: 'DRAFT',
      fields: { priority: 'NORMAL' },
      currentWorkflowLinkId: null,
      version: 3,
      createdBy: 'u',
      createdAt: '',
      updatedBy: 'u',
      updatedAt: '',
      closedBy: null,
      closedAt: null,
    }
    vi.mocked(dossiersApi.updateDossierFields).mockResolvedValue({
      ...store.dossier,
      fields: { priority: 'HIGH' },
      version: 4,
    })
    await store.patchFields({ priority: 'HIGH' })
    expect(dossiersApi.updateDossierFields).toHaveBeenCalledWith('d1', { priority: 'HIGH' }, 3)
    expect(store.dossier?.fields).toEqual({ priority: 'HIGH' })
    expect(store.dossier?.version).toBe(4)
  })

  it('marks diagram unavailable on 409', async () => {
    vi.mocked(workflowsApi.getWorkflowDiagram).mockRejectedValue({
      response: { status: 409, data: { status: 409, detail: 'no PI' } },
    })
    vi.mocked(workflowsApi.getWorkflowHistory).mockResolvedValue([])
    vi.mocked(workflowsApi.getWorkflowTasks).mockResolvedValue([])
    const store = useDossierStore()
    await store.loadVisualization('link-1')
    expect(store.diagramUnavailable).toBe(true)
  })

  it('soft-fails visualization on 403 without throwing', async () => {
    vi.mocked(workflowsApi.getWorkflowDiagram).mockRejectedValue({
      response: { status: 403, data: { status: 403, detail: 'forbidden' } },
    })
    vi.mocked(workflowsApi.getWorkflowHistory).mockRejectedValue({ response: { status: 403 } })
    vi.mocked(workflowsApi.getWorkflowTasks).mockRejectedValue({ response: { status: 403 } })
    const store = useDossierStore()
    await expect(store.loadVisualization('link-1')).resolves.toBeUndefined()
    expect(store.diagramUnavailable).toBe(true)
    expect(store.diagram).toBeNull()
  })

  it('creates and starts workflow with reload', async () => {
    vi.mocked(dossiersApi.createDossier).mockResolvedValue({
      id: 'd1',
      tenantId: 't',
      reference: 'DOS-1',
      dossierTypeCode: 'CLAIM',
      label: 'L',
      status: 'DRAFT',
      fields: {},
      currentWorkflowLinkId: null,
      version: 0,
      createdBy: 'u',
      createdAt: '',
      updatedBy: 'u',
      updatedAt: '',
      closedBy: null,
      closedAt: null,
    })
    vi.mocked(dossiersApi.startDossierWorkflow).mockResolvedValue({
      id: 'link-1',
      dossierId: 'd1',
      tenantId: 't',
      processInstanceId: 'pi',
      processDefinitionKey: 'wf',
      sequence: 1,
      status: 'ACTIVE',
      triggerRef: null,
      startedAt: null,
      endedAt: null,
      endReason: null,
      startedBy: 'u',
    })
    vi.mocked(dossiersApi.getDossierById).mockResolvedValue({
      id: 'd1',
      tenantId: 't',
      reference: 'DOS-1',
      dossierTypeCode: 'CLAIM',
      label: 'L',
      status: 'EN_COURS',
      fields: {},
      currentWorkflowLinkId: 'link-1',
      version: 1,
      createdBy: 'u',
      createdAt: '',
      updatedBy: 'u',
      updatedAt: '',
      closedBy: null,
      closedAt: null,
    })
    vi.mocked(typesApi.getDossierType).mockResolvedValue({
      id: 't1',
      tenantId: 't',
      code: 'CLAIM',
      label: 'Claim',
      workflowKey: 'wf',
      active: true,
      allowUnknownFields: false,
      fieldDefinitions: [],
    })
    vi.mocked(dossiersApi.listDossierWorkflows).mockResolvedValue([])
    vi.mocked(dossiersApi.listDossierElements).mockResolvedValue([])
    vi.mocked(dossiersApi.listDossierRelations).mockResolvedValue([])
    vi.mocked(workflowsApi.getWorkflowDiagram).mockResolvedValue({
      bpmnXml: '<xml/>',
      activeIds: [],
      completedIds: [],
      failedIds: [],
    })
    vi.mocked(workflowsApi.getWorkflowHistory).mockResolvedValue([])
    vi.mocked(workflowsApi.getWorkflowTasks).mockResolvedValue([])

    const store = useDossierStore()
    await store.create({ dossierTypeCode: 'CLAIM', label: 'L', fields: {} })
    await store.startWorkflow('wf')
    expect(dossiersApi.startDossierWorkflow).toHaveBeenCalled()
    expect(store.dossier?.currentWorkflowLinkId).toBe('link-1')
  })
})
