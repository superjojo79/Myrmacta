<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useDossierStore } from '@/stores/dossier'
import { useAuthStore } from '@/stores/auth'
import { toProblemDetail } from '@/api/http'
import type { DossierStatus, ElementType, RelationType } from '@/api/types'
import DossierFieldsEditor from '@/components/dossier/DossierFieldsEditor.vue'
import StatusSelect from '@/components/dossier/StatusSelect.vue'
import ElementsList from '@/components/dossier/ElementsList.vue'
import WorkflowLinksList from '@/components/dossier/WorkflowLinksList.vue'
import AddElementDialog from '@/components/dossier/AddElementDialog.vue'
import RelationsList from '@/components/dossier/RelationsList.vue'
import LinkDossierDialog from '@/components/dossier/LinkDossierDialog.vue'
import BpmnViewer from '@/components/workflow/BpmnViewer.vue'
import TaskList from '@/components/workflow/TaskList.vue'
import HistoryTimeline from '@/components/workflow/HistoryTimeline.vue'
import StartWorkflowDialog from '@/components/workflow/StartWorkflowDialog.vue'
import DossierPurgeRequestButton from '@/components/dossier/DossierPurgeRequestButton.vue'
import PortalLinksPanel from '@/components/dossier/PortalLinksPanel.vue'
import SignatureRequestsPanel from '@/components/dossier/SignatureRequestsPanel.vue'

const route = useRoute()
const store = useDossierStore()
const auth = useAuthStore()
const {
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
} = storeToRefs(store)
const { isTenantAdmin, isWorkflowAdmin } = storeToRefs(auth)

const statusDraft = ref<DossierStatus>('DRAFT')
const startOpen = ref(false)
const linkOpen = ref(false)
const addElementOpen = ref(false)
const reassignTaskId = ref<string | null>(null)
const reassignUserId = ref('')
const actionError = ref<string | null>(null)

const canReassign = computed(() => isTenantAdmin.value || isWorkflowAdmin.value)
// TODO §36: server-computed effective permission (DossierAccessPolicy.canEdit), not a role guess.
const canEditDossier = computed(() => dossier.value?.canEdit ?? false)
const fieldsLocked = computed(
  () => dossier.value?.status === 'CLOTURE' || dossier.value?.status === 'ARCHIVE',
)
const canLink = canEditDossier
const linkId = computed(() => dossier.value?.currentWorkflowLinkId)

watch(
  dossier,
  (value) => {
    if (value) statusDraft.value = value.status
  },
  { immediate: true },
)

onMounted(async () => {
  await loadCurrent()
})

watch(
  () => route.params.id,
  async () => {
    await loadCurrent()
  },
)

async function loadCurrent() {
  const id = String(route.params.id ?? '')
  if (!id) return
  actionError.value = null
  await store.loadById(id)
}

async function saveStatus() {
  actionError.value = null
  try {
    await store.patchStatus(statusDraft.value)
  } catch (err) {
    actionError.value = toProblemDetail(err).detail ?? 'Mise à jour statut impossible'
  }
}

async function saveFields(patch: Record<string, unknown>) {
  actionError.value = null
  try {
    await store.patchFields(patch)
  } catch (err) {
    actionError.value = toProblemDetail(err).detail ?? 'Mise à jour champs impossible'
  }
}

async function onLink(payload: { targetDossierId: string; type: RelationType }) {
  actionError.value = null
  try {
    await store.linkDossier(payload.targetDossierId, payload.type)
    linkOpen.value = false
  } catch (err) {
    actionError.value = toProblemDetail(err).detail ?? 'Liaison impossible'
  }
}

async function onAddElement(payload: { type: ElementType; label: string; metadata: Record<string, unknown> }) {
  actionError.value = null
  try {
    await store.addElement(payload.type, payload.label, payload.metadata)
    addElementOpen.value = false
  } catch (err) {
    actionError.value = toProblemDetail(err).detail ?? 'Ajout impossible'
  }
}

async function onAddAttachment(payload: { file: File; label: string }) {
  actionError.value = null
  try {
    await store.addAttachment(payload.file, payload.label)
    addElementOpen.value = false
  } catch (err) {
    actionError.value = toProblemDetail(err).detail ?? 'Ajout impossible'
  }
}

async function onStart(key: string) {
  startOpen.value = false
  try {
    await store.startWorkflow(key)
  } catch (err) {
    actionError.value = toProblemDetail(err).detail ?? 'Démarrage impossible'
  }
}

async function onComplete(taskId: string, variables: Record<string, unknown> = {}) {
  try {
    await store.complete(taskId, variables)
  } catch (err) {
    actionError.value = toProblemDetail(err).detail ?? 'Complétion impossible'
  }
}

async function confirmReassign() {
  if (!linkId.value || !reassignTaskId.value || !reassignUserId.value) return
  try {
    await store.reassign(linkId.value, reassignTaskId.value, reassignUserId.value)
    reassignTaskId.value = null
    reassignUserId.value = ''
  } catch (err) {
    actionError.value = toProblemDetail(err).detail ?? 'Réassignation impossible'
  }
}
</script>

<template>
  <section v-if="loading">Chargement…</section>
  <section v-else-if="error" class="error">{{ error }}</section>
  <section v-else-if="dossier" class="dossier-detail">
    <header class="dossier-detail__header">
      <div>
        <h1>{{ dossier.label }}</h1>
        <p>{{ dossier.reference }} · {{ dossier.dossierTypeCode }}</p>
      </div>
      <div class="dossier-detail__actions">
        <StatusSelect v-model="statusDraft" />
        <button type="button" @click="saveStatus">Enregistrer statut</button>
        <button type="button" class="primary" @click="startOpen = true">Démarrer workflow</button>
        <DossierPurgeRequestButton v-if="isTenantAdmin" :dossier-id="dossier.id" />
      </div>
    </header>
    <p v-if="actionError" class="error">{{ actionError }}</p>

    <DossierFieldsEditor
      v-if="dossierType"
      :definitions="dossierType.fieldDefinitions"
      :fields="dossier.fields"
      :can-edit="canEditDossier"
      :locked="fieldsLocked"
      @save="saveFields"
    />

    <ElementsList
      :elements="elements"
      :page="elementsPage"
      :total-pages="elementsTotalPages"
      @update:page="store.loadElementsPage($event)"
    >
      <template #actions>
        <button v-if="canEditDossier" type="button" @click="addElementOpen = true">Ajouter</button>
      </template>
    </ElementsList>
    <WorkflowLinksList
      :workflows="workflows"
      :page="workflowsPage"
      :total-pages="workflowsTotalPages"
      @update:page="store.loadWorkflowsPage($event)"
    />
    <AddElementDialog
      :open="addElementOpen"
      @close="addElementOpen = false"
      @submit-element="onAddElement"
      @submit-attachment="onAddAttachment"
    />

    <RelationsList :relations="relations">
      <template #actions>
        <button v-if="canLink" type="button" @click="linkOpen = true">Lier un dossier</button>
      </template>
    </RelationsList>
    <LinkDossierDialog
      :open="linkOpen"
      :current-dossier-id="dossier.id"
      @close="linkOpen = false"
      @submit="onLink"
    />

    <BpmnViewer :diagram="diagram" :unavailable="diagramUnavailable || !linkId" />
    <HistoryTimeline
      :history="history"
      :page="historyPage"
      :total-pages="historyTotalPages"
      @update:page="store.loadHistoryPage($event)"
    />
    <TaskList
      :tasks="tasks"
      :dossier-id="dossier?.id"
      :can-reassign="canReassign"
      :page="tasksPage"
      :total-pages="tasksTotalPages"
      @complete="onComplete"
      @reassign="reassignTaskId = $event"
      @form-error="actionError = $event"
      @update:page="store.loadTasksPage($event)"
    />

    <PortalLinksPanel :key="`${dossier.id}-${tasks.length}`" :dossier-id="dossier.id" />

    <SignatureRequestsPanel :key="`${dossier.id}-${tasks.length}`" :dossier-id="dossier.id" />

    <div v-if="reassignTaskId" class="reassign">
      <label>
        Nouvel assignee
        <input v-model="reassignUserId" type="text" />
      </label>
      <button type="button" @click="confirmReassign">Confirmer</button>
      <button type="button" @click="reassignTaskId = null">Annuler</button>
    </div>

    <StartWorkflowDialog
      :open="startOpen"
      :default-workflow-key="dossierType?.workflowKey ?? ''"
      @close="startOpen = false"
      @start="onStart"
    />
  </section>
</template>

<style scoped>
.dossier-detail__header {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
  margin-bottom: 1rem;
}
.dossier-detail__actions {
  display: flex;
  gap: 0.5rem;
  align-items: end;
  flex-wrap: wrap;
}
button {
  border: 1px solid #b7b7ba;
  background: white;
  padding: 0.4rem 0.7rem;
  cursor: pointer;
}
button.primary {
  background: var(--color-accent);
  color: white;
  border-color: transparent;
}
.error {
  color: #c92a2a;
}
.reassign {
  display: flex;
  gap: 0.5rem;
  align-items: end;
  margin-top: 1rem;
}
</style>
