<script setup lang="ts">
import { useI18n } from 'vue-i18n'
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
import IncidentBanner from '@/components/workflow/IncidentBanner.vue'
import TaskList from '@/components/workflow/TaskList.vue'
import HistoryTimeline from '@/components/workflow/HistoryTimeline.vue'
import StartWorkflowDialog from '@/components/workflow/StartWorkflowDialog.vue'
import DossierPurgeRequestButton from '@/components/dossier/DossierPurgeRequestButton.vue'
import PortalLinksPanel from '@/components/dossier/PortalLinksPanel.vue'
import SignatureRequestsPanel from '@/components/dossier/SignatureRequestsPanel.vue'

const { t } = useI18n()
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
  assignableUsers,
  incidents,
} = storeToRefs(store)
const { user, isTenantAdmin, isWorkflowAdmin } = storeToRefs(auth)

const statusDraft = ref<DossierStatus>('DRAFT')
const startOpen = ref(false)
const linkOpen = ref(false)
const addElementOpen = ref(false)
const reassignTaskId = ref<string | null>(null)
const reassignUserId = ref('')
const actionError = ref<string | null>(null)
/** TODO §94 — job dont le rejeu est en cours. */
const retryingJobId = ref<string | null>(null)
const assignableUsersLoaded = ref(false)

const canReassign = computed(() => isTenantAdmin.value || isWorkflowAdmin.value)
// TODO §36: server-computed effective permission (DossierAccessPolicy.canEdit), not a role guess.
const canEditDossier = computed(() => dossier.value?.canEdit ?? false)
const fieldsLocked = computed(
  () => dossier.value?.status === 'CLOTURE' || dossier.value?.status === 'ARCHIVE',
)
const canLink = canEditDossier
const linkId = computed(() => dossier.value?.currentWorkflowLinkId)
// D-ASSIGN-CLAIM : prendre une tâche libre exige le même droit que la terminer (EDIT).
const canClaim = canEditDossier
const assigneeNames = computed(() =>
  Object.fromEntries(assignableUsers.value.map((candidate) => [candidate.userId, candidate.displayName])),
)

watch(
  dossier,
  (value) => {
    if (value) statusDraft.value = value.status
  },
  { immediate: true },
)

// Les admins ont accès à l'annuaire : il sert aussi à afficher un nom plutôt qu'un identifiant.
watch(
  [linkId, canReassign],
  async ([id, admin]) => {
    if (!id || !admin) return
    try {
      await store.loadAssignableUsers(id)
      assignableUsersLoaded.value = true
    } catch {
      // Noms facultatifs : la liste des tâches reste utilisable avec les identifiants.
    }
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
    actionError.value = toProblemDetail(err).detail ?? t('dossierDetail.errors.status')
  }
}

async function saveFields(patch: Record<string, unknown>) {
  actionError.value = null
  try {
    await store.patchFields(patch)
  } catch (err) {
    actionError.value = toProblemDetail(err).detail ?? t('dossierDetail.errors.fields')
  }
}

async function onLink(payload: { targetDossierId: string; type: RelationType }) {
  actionError.value = null
  try {
    await store.linkDossier(payload.targetDossierId, payload.type)
    linkOpen.value = false
  } catch (err) {
    actionError.value = toProblemDetail(err).detail ?? t('dossierDetail.errors.link')
  }
}

async function onAddElement(payload: { type: ElementType; label: string; metadata: Record<string, unknown> }) {
  actionError.value = null
  try {
    await store.addElement(payload.type, payload.label, payload.metadata)
    addElementOpen.value = false
  } catch (err) {
    actionError.value = toProblemDetail(err).detail ?? t('dossierDetail.errors.add')
  }
}

async function onAddAttachment(payload: { file: File; label: string }) {
  actionError.value = null
  try {
    await store.addAttachment(payload.file, payload.label)
    addElementOpen.value = false
  } catch (err) {
    actionError.value = toProblemDetail(err).detail ?? t('dossierDetail.errors.add')
  }
}

async function onStart(key: string, variables: Record<string, string> = {}) {
  startOpen.value = false
  try {
    await store.startWorkflow(key, variables)
  } catch (err) {
    actionError.value = toProblemDetail(err).detail ?? t('dossierDetail.errors.start')
  }
}

async function onRetryIncident(jobId: string) {
  if (!linkId.value) return
  actionError.value = null
  retryingJobId.value = jobId
  try {
    await store.retryIncident(linkId.value, jobId)
  } catch (err) {
    actionError.value = toProblemDetail(err).detail ?? t('workflow.incidents.retryError')
  } finally {
    retryingJobId.value = null
  }
}

async function onComplete(taskId: string, variables: Record<string, unknown> = {}) {
  try {
    await store.complete(taskId, variables)
  } catch (err) {
    actionError.value = toProblemDetail(err).detail ?? t('dossierDetail.errors.complete')
  }
}

async function onClaim(taskId: string) {
  if (!linkId.value) return
  actionError.value = null
  try {
    await store.claim(linkId.value, taskId)
  } catch (err) {
    actionError.value = toProblemDetail(err).detail ?? t('dossierDetail.errors.claim')
  }
}

async function openReassign(taskId: string) {
  reassignTaskId.value = taskId
  reassignUserId.value = ''
  if (!linkId.value) return
  try {
    await store.loadAssignableUsers(linkId.value)
    assignableUsersLoaded.value = true
  } catch (err) {
    actionError.value = toProblemDetail(err).detail ?? t('dossierDetail.errors.assignableUsers')
  }
}

async function confirmReassign() {
  if (!linkId.value || !reassignTaskId.value || !reassignUserId.value) return
  try {
    await store.reassign(linkId.value, reassignTaskId.value, reassignUserId.value)
    reassignTaskId.value = null
    reassignUserId.value = ''
  } catch (err) {
    actionError.value = toProblemDetail(err).detail ?? t('dossierDetail.errors.reassign')
  }
}
</script>

<template>
  <section v-if="loading">{{ t('common.loading') }}</section>
  <section v-else-if="error" class="error">{{ error }}</section>
  <section v-else-if="dossier" class="dossier-detail">
    <header class="dossier-detail__header">
      <div>
        <h1>{{ dossier.label }}</h1>
        <p>{{ dossier.reference }} · {{ dossier.dossierTypeCode }}</p>
      </div>
      <div class="dossier-detail__actions">
        <StatusSelect v-model="statusDraft" />
        <button type="button" @click="saveStatus">{{ t('dossierDetail.saveStatus') }}</button>
        <button type="button" class="primary" @click="startOpen = true">{{ t('dossierDetail.startWorkflow') }}</button>
        <DossierPurgeRequestButton v-if="isTenantAdmin" :dossier-id="dossier.id" />
      </div>
    </header>
    <p v-if="actionError" class="error">{{ actionError }}</p>

    <DossierFieldsEditor
      v-if="dossierType"
      :definitions="dossierType.fieldDefinitions"
      :type-code="dossierType.code"
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
        <button v-if="canEditDossier" type="button" @click="addElementOpen = true">{{ t('dossierDetail.add') }}</button>
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
        <button v-if="canLink" type="button" @click="linkOpen = true">{{ t('dossierDetail.linkDossier') }}</button>
      </template>
    </RelationsList>
    <LinkDossierDialog
      :open="linkOpen"
      :current-dossier-id="dossier.id"
      @close="linkOpen = false"
      @submit="onLink"
    />

    <IncidentBanner
      :incidents="incidents"
      :can-retry="isWorkflowAdmin"
      :retrying-job-id="retryingJobId"
      @retry="onRetryIncident"
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
      :can-claim="canClaim"
      :current-user-id="user?.id"
      :assignee-names="assigneeNames"
      :page="tasksPage"
      :total-pages="tasksTotalPages"
      @complete="onComplete"
      @reassign="openReassign"
      @claim="onClaim"
      @form-error="actionError = $event"
      @update:page="store.loadTasksPage($event)"
    />

    <PortalLinksPanel :key="`${dossier.id}-${tasks.length}`" :dossier-id="dossier.id" />

    <SignatureRequestsPanel :key="`${dossier.id}-${tasks.length}`" :dossier-id="dossier.id" />

    <div v-if="reassignTaskId" class="reassign">
      <label>
        {{ t('dossierDetail.newAssignee') }}
        <select v-model="reassignUserId" data-testid="reassign-user">
          <option value="" disabled>{{ t('dossierDetail.chooseAssignee') }}</option>
          <option v-for="candidate in assignableUsers" :key="candidate.userId" :value="candidate.userId">
            {{ candidate.email ? `${candidate.displayName} (${candidate.email})` : candidate.displayName }}
          </option>
        </select>
      </label>
      <button type="button" :disabled="!reassignUserId" @click="confirmReassign">
        {{ t('dossierDetail.confirm') }}
      </button>
      <button type="button" @click="reassignTaskId = null">{{ t('common.cancel') }}</button>
      <p v-if="assignableUsersLoaded && assignableUsers.length === 0" class="reassign__hint">
        {{ t('dossierDetail.noAssignableUsers') }}
      </p>
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
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: end;
  margin-top: 1rem;
}
.reassign__hint {
  flex-basis: 100%;
  margin: 0;
  font-size: 0.85rem;
  opacity: 0.75;
}
</style>
