<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useDossierStore } from '@/stores/dossier'
import { useAuthStore } from '@/stores/auth'
import { toProblemDetail } from '@/api/http'
import type { DossierStatus } from '@/api/types'
import DynamicFields from '@/components/dossier/DynamicFields.vue'
import StatusSelect from '@/components/dossier/StatusSelect.vue'
import ElementsList from '@/components/dossier/ElementsList.vue'
import BpmnViewer from '@/components/workflow/BpmnViewer.vue'
import TaskList from '@/components/workflow/TaskList.vue'
import HistoryTimeline from '@/components/workflow/HistoryTimeline.vue'
import StartWorkflowDialog from '@/components/workflow/StartWorkflowDialog.vue'

const route = useRoute()
const store = useDossierStore()
const auth = useAuthStore()
const {
  dossier,
  dossierType,
  elements,
  diagram,
  history,
  tasks,
  loading,
  error,
  diagramUnavailable,
} = storeToRefs(store)
const { isTenantAdmin, isWorkflowAdmin } = storeToRefs(auth)

const statusDraft = ref<DossierStatus>('DRAFT')
const startOpen = ref(false)
const reassignTaskId = ref<string | null>(null)
const reassignUserId = ref('')
const actionError = ref<string | null>(null)

const canReassign = computed(() => isTenantAdmin.value || isWorkflowAdmin.value)
const linkId = computed(() => dossier.value?.currentWorkflowLinkId)

watch(
  dossier,
  (value) => {
    if (value) statusDraft.value = value.status
  },
  { immediate: true },
)

onMounted(async () => {
  const id = String(route.params.id)
  await store.loadById(id)
})

async function saveStatus() {
  actionError.value = null
  try {
    await store.patchStatus(statusDraft.value)
  } catch (err) {
    actionError.value = toProblemDetail(err).detail ?? 'Mise à jour statut impossible'
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

async function onComplete(taskId: string) {
  try {
    await store.complete(taskId)
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
      </div>
    </header>
    <p v-if="actionError" class="error">{{ actionError }}</p>

    <DynamicFields
      v-if="dossierType"
      :definitions="dossierType.fieldDefinitions"
      :model-value="dossier.fields"
      readonly
    />

    <ElementsList :elements="elements" />

    <BpmnViewer :diagram="diagram" :unavailable="diagramUnavailable" />
    <HistoryTimeline :history="history" />
    <TaskList
      :tasks="tasks"
      :can-reassign="canReassign"
      @complete="onComplete"
      @reassign="reassignTaskId = $event"
    />

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
