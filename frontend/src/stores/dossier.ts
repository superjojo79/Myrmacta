import { defineStore } from 'pinia'
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
} from '@/api/workflows'
import { completeTask } from '@/api/tasks'
import { toProblemDetail } from '@/api/http'
import type {
  ActivityHistoryEntry,
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

export const useDossierStore = defineStore('dossier', () => {
  const dossier = ref<Dossier | null>(null)
  const dossierType = ref<DossierType | null>(null)
  const workflows = ref<WorkflowLink[]>([])
  const elements = ref<DossierElementView[]>([])
  const relations = ref<DossierRelation[]>([])
  const diagram = ref<WorkflowDiagram | null>(null)
  const history = ref<ActivityHistoryEntry[]>([])
  const tasks = ref<WorkflowTaskView[]>([])
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
      error.value = toProblemDetail(err).detail ?? 'Impossible de charger le dossier'
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
      error.value = toProblemDetail(err).detail ?? 'Impossible de charger le dossier'
      throw err
    } finally {
      loading.value = false
    }
  }

  async function refreshRelated(): Promise<void> {
    if (!dossier.value) return
    const current = dossier.value
    ;[dossierType.value, workflows.value, elements.value, relations.value] = await Promise.all([
      getDossierType(current.dossierTypeCode),
      listDossierWorkflows(current.id),
      listDossierElements(current.id),
      listDossierRelations(current.id),
    ])
    diagram.value = null
    history.value = []
    tasks.value = []
    diagramUnavailable.value = false
    if (current.currentWorkflowLinkId) {
      await loadVisualization(current.currentWorkflowLinkId)
    }
  }

  async function loadVisualization(linkId: string): Promise<void> {
    try {
      ;[diagram.value, history.value, tasks.value] = await Promise.all([
        getWorkflowDiagram(linkId),
        getWorkflowHistory(linkId),
        getWorkflowTasks(linkId),
      ])
      diagramUnavailable.value = false
    } catch (err) {
      // Soft-fail viz (409 sans PI, 403 non-participant, 5xx) — ne pas masquer le dossier.
      diagramUnavailable.value = true
      diagram.value = null
      history.value = await getWorkflowHistory(linkId).catch(() => [])
      tasks.value = await getWorkflowTasks(linkId).catch(() => [])
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

  async function complete(taskId: string): Promise<void> {
    await completeTask(taskId, {})
    if (dossier.value) await loadById(dossier.value.id)
  }

  async function reassign(linkId: string, taskId: string, assigneeId: string): Promise<void> {
    await reassignWorkflowTask(linkId, taskId, assigneeId)
    await loadVisualization(linkId)
  }

  return {
    dossier,
    dossierType,
    workflows,
    elements,
    relations,
    diagram,
    history,
    tasks,
    loading,
    error,
    diagramUnavailable,
    loadById,
    loadByRef,
    create,
    linkDossier,
    addElement,
    addAttachment,
    patchStatus,
    patchFields,
    startWorkflow,
    complete,
    reassign,
    loadVisualization,
  }
})
