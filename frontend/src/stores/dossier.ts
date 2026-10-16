import { defineStore } from 'pinia'
import { i18n } from '@/i18n'
import { ref } from 'vue'
import {
  addDossierElement,
  createDossier,
  createDossierRelation,
  getDossierById,
  getDossierByRef,
  listDossierElements,
  listDossierRelations,
  listDossierWorkflows,
  startDossierWorkflow,
  updateDossierFields,
  updateDossierStatus,
} from '@/api/dossiers'
import { getDossierType } from '@/api/dossierTypes'
import { uploadAttachment } from '@/api/elements'
import {
  getWorkflowDiagram,
  getWorkflowHistory,
  getWorkflowTasks,
  reassignWorkflowTask,
  claimWorkflowTask,
  listAssignableUsers,
} from '@/api/workflows'
import { completeTask } from '@/api/tasks'
import { toProblemDetail } from '@/api/http'
import type {
  ActivityHistoryEntry,
  AssignableUser,
  Dossier,
  DossierElementView,
  DossierRelation,
  DossierStatus,
  DossierType,
  ElementType,
  RelationType,
  WorkflowDiagram,
  WorkflowLink,
  WorkflowTaskView,
} from '@/api/types'

const DEFAULT_SIZE = 20

export const useDossierStore = defineStore('dossier', () => {
  const dossier = ref<Dossier | null>(null)
  const dossierType = ref<DossierType | null>(null)
  const workflows = ref<WorkflowLink[]>([])
  const workflowsPage = ref(0)
  const workflowsTotalPages = ref(0)
  const elements = ref<DossierElementView[]>([])
  const elementsPage = ref(0)
  const elementsTotalPages = ref(0)
  const relations = ref<DossierRelation[]>([])
  const diagram = ref<WorkflowDiagram | null>(null)
  const history = ref<ActivityHistoryEntry[]>([])
  const historyPage = ref(0)
  const historyTotalPages = ref(0)
  const tasks = ref<WorkflowTaskView[]>([])
  const assignableUsers = ref<AssignableUser[]>([])
  const tasksPage = ref(0)
  const tasksTotalPages = ref(0)
  const currentLinkId = ref<string | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const diagramUnavailable = ref(false)

  async function loadById(id: string): Promise<void> {
    loading.value = true
    error.value = null
    try {
      dossier.value = await getDossierById(id)
      await refreshRelated()
    } catch (err) {
      error.value = toProblemDetail(err).detail ?? i18n.global.t('common.errors.dossier')
      throw err
    } finally {
      loading.value = false
    }
  }

  async function loadByRef(ref: string): Promise<void> {
    loading.value = true
    error.value = null
    try {
      dossier.value = await getDossierByRef(ref)
      await refreshRelated()
    } catch (err) {
      error.value = toProblemDetail(err).detail ?? i18n.global.t('common.errors.dossier')
      throw err
    } finally {
      loading.value = false
    }
  }

  async function refreshRelated(): Promise<void> {
    if (!dossier.value) return
    ;[dossierType.value] = await Promise.all([
      getDossierType(dossier.value.dossierTypeCode),
      loadWorkflowsPage(0, DEFAULT_SIZE),
      loadElementsPage(0, DEFAULT_SIZE),
      listDossierRelations(dossier.value.id).then((result) => {
        relations.value = result
      }),
    ])
    diagram.value = null
    history.value = []
    historyPage.value = 0
    historyTotalPages.value = 0
    tasks.value = []
    tasksPage.value = 0
    tasksTotalPages.value = 0
    currentLinkId.value = null
    diagramUnavailable.value = false
    if (dossier.value.currentWorkflowLinkId) {
      await loadVisualization(dossier.value.currentWorkflowLinkId)
    }
  }

  async function loadElementsPage(page = 0, size = DEFAULT_SIZE): Promise<void> {
    if (!dossier.value) return
    const result = await listDossierElements(dossier.value.id, { page, size })
    elements.value = result.content
    elementsPage.value = result.page
    elementsTotalPages.value = result.totalPages
  }

  async function loadWorkflowsPage(page = 0, size = DEFAULT_SIZE): Promise<void> {
    if (!dossier.value) return
    const result = await listDossierWorkflows(dossier.value.id, { page, size })
    workflows.value = result.content
    workflowsPage.value = result.page
    workflowsTotalPages.value = result.totalPages
  }

  async function loadHistoryPage(page = 0, size = DEFAULT_SIZE): Promise<void> {
    if (!currentLinkId.value) return
    const result = await getWorkflowHistory(currentLinkId.value, { page, size })
    history.value = result.content
    historyPage.value = result.page
    historyTotalPages.value = result.totalPages
  }

  async function loadTasksPage(page = 0, size = DEFAULT_SIZE): Promise<void> {
    if (!currentLinkId.value) return
    const result = await getWorkflowTasks(currentLinkId.value, { page, size })
    tasks.value = result.content
    tasksPage.value = result.page
    tasksTotalPages.value = result.totalPages
  }

  async function loadVisualization(linkId: string): Promise<void> {
    currentLinkId.value = linkId
    try {
      const [diagramResult] = await Promise.all([
        getWorkflowDiagram(linkId),
        loadHistoryPage(0, DEFAULT_SIZE),
        loadTasksPage(0, DEFAULT_SIZE),
      ])
      diagram.value = diagramResult
      diagramUnavailable.value = false
    } catch (err) {
      // Soft-fail viz (409 sans PI, 403 non-participant, 5xx) — ne pas masquer le dossier.
      diagramUnavailable.value = true
      diagram.value = null
      await loadHistoryPage(0, DEFAULT_SIZE).catch(() => {
        history.value = []
      })
      await loadTasksPage(0, DEFAULT_SIZE).catch(() => {
        tasks.value = []
      })
      void err
    }
  }

  async function create(payload: {
    dossierTypeCode: string
    label: string
    fields: Record<string, unknown>
  }): Promise<Dossier> {
    const created = await createDossier(payload)
    dossier.value = created
    return created
  }

  async function linkDossier(targetDossierId: string, type: RelationType): Promise<DossierRelation> {
    if (!dossier.value) throw new Error('No dossier loaded')
    const created = await createDossierRelation(dossier.value.id, { targetDossierId, type })
    relations.value = [created, ...relations.value.filter((item) => item.id !== created.id)]
    return created
  }

  async function addElement(
    type: ElementType,
    label: string,
    metadata: Record<string, unknown>,
  ): Promise<DossierElementView> {
    if (!dossier.value) throw new Error('No dossier loaded')
    const created = await addDossierElement(dossier.value.id, { type, label, metadata })
    elements.value = [...elements.value, created]
    return created
  }

  async function addAttachment(file: File, label: string): Promise<DossierElementView> {
    if (!dossier.value) throw new Error('No dossier loaded')
    const created = await uploadAttachment(dossier.value.id, file, label)
    elements.value = [...elements.value, created]
    return created
  }

  async function patchStatus(status: DossierStatus): Promise<void> {
    if (!dossier.value) return
    dossier.value = await updateDossierStatus(dossier.value.id, status, dossier.value.version)
  }

  async function patchFields(fields: Record<string, unknown>): Promise<void> {
    if (!dossier.value) return
    dossier.value = await updateDossierFields(dossier.value.id, fields, dossier.value.version)
  }

  async function startWorkflow(
    processDefinitionKey: string,
    variables: Record<string, unknown> = {},
  ): Promise<WorkflowLink> {
    if (!dossier.value) throw new Error('No dossier loaded')
    const link = await startDossierWorkflow(
      dossier.value.id,
      processDefinitionKey,
      variables,
      crypto.randomUUID(),
    )
    await loadById(dossier.value.id)
    return link
  }

  async function complete(taskId: string, variables: Record<string, unknown> = {}): Promise<void> {
    await completeTask(taskId, variables)
    if (dossier.value) await loadById(dossier.value.id)
  }

  async function reassign(linkId: string, taskId: string, assigneeId: string): Promise<void> {
    await reassignWorkflowTask(linkId, taskId, assigneeId)
    await loadVisualization(linkId)
  }

  async function claim(linkId: string, taskId: string): Promise<void> {
    await claimWorkflowTask(linkId, taskId)
    await loadVisualization(linkId)
  }

  async function loadAssignableUsers(linkId: string): Promise<void> {
    assignableUsers.value = await listAssignableUsers(linkId)
  }

  return {
    dossier,
    dossierType,
    workflows,
    workflowsPage,
    workflowsTotalPages,
    elements,
    elementsPage,
    elementsTotalPages,
    relations,
    diagram,
    history,
    historyPage,
    historyTotalPages,
    tasks,
    tasksPage,
    tasksTotalPages,
    loading,
    error,
    diagramUnavailable,
    loadById,
    loadByRef,
    loadElementsPage,
    loadWorkflowsPage,
    loadHistoryPage,
    loadTasksPage,
    create,
    linkDossier,
    addElement,
    addAttachment,
    patchStatus,
    patchFields,
    startWorkflow,
    complete,
    reassign,
    claim,
    assignableUsers,
    loadAssignableUsers,
    loadVisualization,
  }
})
