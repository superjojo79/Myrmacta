import { describe, expect, it, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useDossierStore } from './dossier'
import * as dossiersApi from '@/api/dossiers'
import * as typesApi from '@/api/dossierTypes'
import * as workflowsApi from '@/api/workflows'
import * as elementsApi from '@/api/elements'
import * as incidentsApi from '@/api/incidents'
import { asPage } from '@/api/page'

vi.mock('@/api/dossiers')
vi.mock('@/api/dossierTypes')
vi.mock('@/api/workflows')
vi.mock('@/api/tasks')
vi.mock('@/api/elements')
vi.mock('@/api/incidents')

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
      canEdit: true,
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
    vi.mocked(dossiersApi.listDossierWorkflows).mockResolvedValue(asPage([]))
    vi.mocked(dossiersApi.listDossierElements).mockResolvedValue(asPage([]))
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
      canEdit: true,
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
      canEdit: true,
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
      canEdit: true,
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
    expect(elementsApi.uploadAttachment).toHaveBeenCalledWith('d1', file, 'Justificatif', undefined)
    expect(store.elements).toHaveLength(1)
    expect(store.elements[0]?.id).toBe('e2')
  })

  /** TODO §106 — le reclassement remplace l'élément sans recharger la liste. */
  it('classifyElement replaces the element in place', async () => {
    const store = useDossierStore()
    store.dossier = { id: 'd1' } as never
    const original = {
      id: 'e1',
      type: 'ATTACHMENT' as const,
      label: 'Facture',
      addedBy: 'u',
      addedAt: '',
      workflowStepId: null,
      metadata: { filename: 'f.pdf' },
      downloadable: true,
    }
    store.elements = [original]
    vi.mocked(elementsApi.classifyAttachment).mockResolvedValue({ ...original, metadata: { pieceKey: 'preuve' } })

    await store.classifyElement('e1', 'preuve')

    expect(elementsApi.classifyAttachment).toHaveBeenCalledWith('d1', 'e1', 'preuve')
    expect(store.elements[0]?.metadata).toEqual({ pieceKey: 'preuve' })
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
      canEdit: true,
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
      canEdit: true,
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
    vi.mocked(workflowsApi.getWorkflowHistory).mockResolvedValue(asPage([]))
    vi.mocked(workflowsApi.getWorkflowTasks).mockResolvedValue(asPage([]))
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

  it('claim takes the task then reloads the visualization of that link', async () => {
    vi.mocked(workflowsApi.claimWorkflowTask).mockResolvedValue(undefined)
    vi.mocked(workflowsApi.getWorkflowDiagram).mockResolvedValue({
      bpmnXml: '<xml/>',
      activeIds: [],
      completedIds: [],
      failedIds: [],
    })
    vi.mocked(workflowsApi.getWorkflowHistory).mockResolvedValue(asPage([]))
    vi.mocked(workflowsApi.getWorkflowTasks).mockResolvedValue(
      asPage([{ taskId: 't1', name: 'Review', assignee: 'me', taskDefinitionKey: 'k', createTime: null }]),
    )
    const store = useDossierStore()

    await store.claim('link-1', 't1')

    expect(workflowsApi.claimWorkflowTask).toHaveBeenCalledWith('link-1', 't1')
    expect(store.tasks[0].assignee).toBe('me')
  })

  it('reassignToGroup sends the task to the group then reloads the tasks and the elements (the trace note)', async () => {
    vi.mocked(workflowsApi.reassignWorkflowTaskToGroup).mockResolvedValue(undefined)
    vi.mocked(workflowsApi.getWorkflowDiagram).mockResolvedValue({ bpmnXml: '<xml/>', activeIds: [], completedIds: [], failedIds: [] })
    vi.mocked(workflowsApi.getWorkflowHistory).mockResolvedValue(asPage([]))
    vi.mocked(workflowsApi.getWorkflowTasks).mockResolvedValue(
      asPage([{ taskId: 't1', name: 'Trier', assignee: null, taskDefinitionKey: 'k', createTime: null, candidateGroups: ['JURIDIQUE'] }]),
    )
    const store = useDossierStore()

    await store.reassignToGroup('link-1', 't1', 'JURIDIQUE')

    expect(workflowsApi.reassignWorkflowTaskToGroup).toHaveBeenCalledWith('link-1', 't1', 'JURIDIQUE')
    expect(store.tasks[0].candidateGroups).toEqual(['JURIDIQUE'])
  })

  describe('incidents (TODO §94)', () => {
    const incident = {
      jobId: 'job-1',
      activityId: 'mailRefus',
      activityName: 'E-mail : refus',
      state: 'INCIDENT' as const,
      since: '2026-10-18T18:51:26Z',
      retriesLeft: 0,
    }

    beforeEach(() => {
      vi.mocked(workflowsApi.getWorkflowDiagram).mockResolvedValue({
        bpmnXml: '<xml/>',
        activeIds: [],
        completedIds: [],
        failedIds: ['mailRefus'],
      })
      vi.mocked(workflowsApi.getWorkflowHistory).mockResolvedValue(asPage([]))
      vi.mocked(workflowsApi.getWorkflowTasks).mockResolvedValue(asPage([]))
    })

    it('loadVisualization charge aussi les échecs du lien', async () => {
      vi.mocked(incidentsApi.listLinkIncidents).mockResolvedValue([incident])
      const store = useDossierStore()

      await store.loadVisualization('link-1')

      expect(incidentsApi.listLinkIncidents).toHaveBeenCalledWith('link-1')
      expect(store.incidents).toEqual([incident])
    })

    it('un refus de lecture des échecs ne masque ni le diagramme ni le dossier', async () => {
      vi.mocked(incidentsApi.listLinkIncidents).mockRejectedValue({ response: { status: 403 } })
      const store = useDossierStore()

      await store.loadVisualization('link-1')

      expect(store.incidents).toEqual([])
      expect(store.diagram?.failedIds).toEqual(['mailRefus'])
    })

    it('après un rejeu, relit le lien à 5 s puis 15 s pour suivre la reprise par le moteur', async () => {
      vi.useFakeTimers()
      try {
        vi.mocked(incidentsApi.retryIncident).mockResolvedValue(undefined)
        vi.mocked(incidentsApi.listLinkIncidents)
          .mockResolvedValueOnce([{ ...incident, state: 'RETRYING', retriesLeft: 3 }])
          .mockResolvedValue([])
        const store = useDossierStore()

        await store.retryIncident('link-1', 'job-1')
        expect(store.incidents).toHaveLength(1)

        await vi.advanceTimersByTimeAsync(5_000)
        expect(store.incidents).toEqual([])
        const calls = vi.mocked(incidentsApi.listLinkIncidents).mock.calls.length
        await vi.advanceTimersByTimeAsync(10_000)
        expect(vi.mocked(incidentsApi.listLinkIncidents).mock.calls.length).toBe(calls + 1)
      } finally {
        vi.useRealTimers()
      }
    })

    it('les relectures programmées ne touchent pas un autre dossier ouvert entre-temps', async () => {
      vi.useFakeTimers()
      try {
        vi.mocked(incidentsApi.retryIncident).mockResolvedValue(undefined)
        vi.mocked(incidentsApi.listLinkIncidents).mockResolvedValue([])
        const store = useDossierStore()

        await store.retryIncident('link-1', 'job-1')
        await store.loadVisualization('link-2')
        vi.mocked(incidentsApi.listLinkIncidents).mockClear()
        await vi.advanceTimersByTimeAsync(20_000)

        expect(incidentsApi.listLinkIncidents).not.toHaveBeenCalledWith('link-1')
      } finally {
        vi.useRealTimers()
      }
    })

    it('retryIncident rejoue puis recharge la visualisation du lien', async () => {
      vi.mocked(incidentsApi.retryIncident).mockResolvedValue(undefined)
      vi.mocked(incidentsApi.listLinkIncidents).mockResolvedValue([])
      const store = useDossierStore()

      await store.retryIncident('link-1', 'job-1')

      expect(incidentsApi.retryIncident).toHaveBeenCalledWith('link-1', 'job-1')
      expect(workflowsApi.getWorkflowDiagram).toHaveBeenCalledWith('link-1')
      expect(store.incidents).toEqual([])
    })
  })

  it('loadAssignableUsers stores the users returned for the link', async () => {
    vi.mocked(workflowsApi.listAssignableUsers).mockResolvedValue([
      { userId: 'u1', displayName: 'Ada', email: 'ada@example.org' },
    ])
    const store = useDossierStore()

    await store.loadAssignableUsers('link-1')

    expect(workflowsApi.listAssignableUsers).toHaveBeenCalledWith('link-1')
    expect(store.assignableUsers.map((u) => u.userId)).toEqual(['u1'])
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
      canEdit: true,
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
      canEdit: true,
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
    vi.mocked(dossiersApi.listDossierWorkflows).mockResolvedValue(asPage([]))
    vi.mocked(dossiersApi.listDossierElements).mockResolvedValue(asPage([]))
    vi.mocked(dossiersApi.listDossierRelations).mockResolvedValue([])
    vi.mocked(workflowsApi.getWorkflowDiagram).mockResolvedValue({
      bpmnXml: '<xml/>',
      activeIds: [],
      completedIds: [],
      failedIds: [],
    })
    vi.mocked(workflowsApi.getWorkflowHistory).mockResolvedValue(asPage([]))
    vi.mocked(workflowsApi.getWorkflowTasks).mockResolvedValue(asPage([]))

    const store = useDossierStore()
    await store.create({ dossierTypeCode: 'CLAIM', label: 'L', fields: {} })
    await store.startWorkflow('wf')
    expect(dossiersApi.startDossierWorkflow).toHaveBeenCalled()
    expect(store.dossier?.currentWorkflowLinkId).toBe('link-1')
  })

  it('loadElementsPage stores content, page and totalPages', async () => {
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
      canEdit: true,
    }
    vi.mocked(dossiersApi.listDossierElements).mockResolvedValue(
      asPage(
        [
          {
            id: 'e1',
            type: 'NOTE',
            label: 'x',
            addedBy: 'u',
            addedAt: '',
            workflowStepId: null,
            metadata: {},
            downloadable: false,
          },
        ],
        { page: 1, totalPages: 3 },
      ),
    )
    await store.loadElementsPage(1, 20)
    expect(dossiersApi.listDossierElements).toHaveBeenCalledWith('d1', { page: 1, size: 20 })
    expect(store.elements).toHaveLength(1)
    expect(store.elementsPage).toBe(1)
    expect(store.elementsTotalPages).toBe(3)
  })

  it('loadVisualization remembers the linkId so loadHistoryPage/loadTasksPage can re-paginate', async () => {
    vi.mocked(workflowsApi.getWorkflowDiagram).mockResolvedValue({
      bpmnXml: '<xml/>',
      activeIds: [],
      completedIds: [],
      failedIds: [],
    })
    vi.mocked(workflowsApi.getWorkflowHistory).mockResolvedValue(asPage([], { totalPages: 2 }))
    vi.mocked(workflowsApi.getWorkflowTasks).mockResolvedValue(asPage([], { totalPages: 2 }))
    const store = useDossierStore()
    await store.loadVisualization('link-1')

    vi.mocked(workflowsApi.getWorkflowHistory).mockResolvedValue(asPage([], { page: 1, totalPages: 2 }))
    await store.loadHistoryPage(1, 20)
    expect(workflowsApi.getWorkflowHistory).toHaveBeenCalledWith('link-1', { page: 1, size: 20 })
    expect(store.historyPage).toBe(1)
  })
})
