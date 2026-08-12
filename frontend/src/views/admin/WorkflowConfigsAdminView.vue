<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  createWorkflowTypeConfig,
  listWorkflowTypeConfigs,
  updateWorkflowTypeConfig,
} from '@/api/workflows'
import type { WorkflowTypeConfig } from '@/api/types'
import { toProblemDetail } from '@/api/http'

const configs = ref<WorkflowTypeConfig[]>([])
const error = ref<string | null>(null)
const form = ref({
  workflowKey: '',
  label: '',
  onStartDossierStatus: 'EN_COURS' as const,
})

async function refresh() {
  configs.value = await listWorkflowTypeConfigs()
}

onMounted(() => {
  void refresh().catch((err) => {
    error.value = toProblemDetail(err).detail ?? 'Chargement impossible'
  })
})

async function create() {
  try {
    await createWorkflowTypeConfig({
      workflowKey: form.value.workflowKey,
      label: form.value.label,
      statusDefinitions: [{ code: 'INIT', label: 'Initial', initial: true, outcome: 'NONE' }],
      allowedDossierStatuses: ['DRAFT', 'OUVERT', 'EN_COURS'],
      onStartDossierStatus: form.value.onStartDossierStatus,
      observerUserIds: [],
    })
    form.value = { workflowKey: '', label: '', onStartDossierStatus: 'EN_COURS' }
    await refresh()
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? 'Création impossible'
  }
}

async function rename(cfg: WorkflowTypeConfig) {
  const label = prompt('Nouveau libellé', cfg.label)
  if (!label) return
  await updateWorkflowTypeConfig(cfg.workflowKey, { ...cfg, label })
  await refresh()
}
</script>

<template>
  <section>
    <h1>Configurations workflow</h1>
    <p v-if="error" class="error">{{ error }}</p>
    <form class="admin-form" @submit.prevent="create">
      <input v-model="form.workflowKey" placeholder="workflowKey" required />
      <input v-model="form.label" placeholder="libellé" required />
      <button type="submit">Créer</button>
    </form>
    <ul>
      <li v-for="cfg in configs" :key="cfg.workflowKey">
        <strong>{{ cfg.workflowKey }}</strong> — {{ cfg.label }}
        <button type="button" @click="rename(cfg)">Renommer</button>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.admin-form {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  margin-bottom: 1rem;
}
ul {
  list-style: none;
  padding: 0;
}
li {
  padding: 0.5rem 0;
  border-bottom: 1px solid #ddd;
}
.error {
  color: #c92a2a;
}
</style>
