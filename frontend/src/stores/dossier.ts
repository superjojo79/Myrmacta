import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  createDossier,
  getDossierById,
  getDossierByRef,
  listDossierElements,
  listDossierWorkflows,
  startDossierWorkflow,
  updateDossierStatus,
} from '@/api/dossiers'
import { getDossierType } from '@/api/dossierTypes'
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
  DossierStatus,
  DossierType,
  WorkflowDiagram,
  WorkflowLink,
  WorkflowTaskView,
} from '@/api/types'

export const useDossierStore = defineStore('dossier', () => {
  const dossier = ref<Dossier | null>(null)
  const dossierType = ref<DossierType | null>(null)
  const workflows = ref<WorkflowLink[]>([])
  const elements = ref<DossierElementView[]>([])
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
    ;[dossierType.value, workflows.value, elements.value] = await Promise.all([
      getDossierType(current.dossierTypeCode),
      listDossierWorkflows(current.id),
      listDossierElements(current.id),
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
      const problem = toProblemDetail(err)
      if (problem.status === 409) {
        diagramUnavailable.value = true
        diagram.value = null
        history.value = await getWorkflowHistory(linkId).catch(() => [])
        tasks.value = await getWorkflowTasks(linkId).catch(() => [])
        return
      }
      throw err
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

  async function patchStatus(status: DossierStatus): Promise<void> {
    if (!dossier.value) return
    dossier.value = await updateDossierStatus(dossier.value.id, status, dossier.value.version)
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
    diagram,
    history,
    tasks,
    loading,
    error,
    diagramUnavailable,
    loadById,
    loadByRef,
    create,
    patchStatus,
    startWorkflow,
    complete,
    reassign,
    loadVisualization,
  }
})
