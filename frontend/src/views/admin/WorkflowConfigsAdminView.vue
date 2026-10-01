<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ref, watch } from 'vue'
import {
  createWorkflowTypeConfig,
  listWorkflowTypeConfigs,
  updateWorkflowTypeConfig,
} from '@/api/workflows'
import type { WorkflowTypeConfig } from '@/api/types'
import { toProblemDetail } from '@/api/http'
import AdminPager from '@/components/admin/AdminPager.vue'
import BpmnUploadPreview from '@/components/workflow/BpmnUploadPreview.vue'
import { useAdminListQuery } from '@/composables/useAdminListQuery'

const { page, size } = useAdminListQuery()
const configs = ref<WorkflowTypeConfig[]>([])
const totalPages = ref(0)
const error = ref<string | null>(null)
const bpmnDeployedMessage = ref<string | null>(null)
const form = ref({
  workflowKey: '',
  label: '',
  onStartDossierStatus: 'EN_COURS' as const,
})

async function refresh() {
  const result = await listWorkflowTypeConfigs({ page: page.value, size: size.value })
  configs.value = result.content
  totalPages.value = result.totalPages
}

watch(
  [page, size],
  () => {
    void refresh().catch((err) => {
      error.value = toProblemDetail(err).detail ?? t('admin.workflowConfigs.errors.load')
    })
  },
  { immediate: true },
)

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
    error.value = toProblemDetail(err).detail ?? t('admin.workflowConfigs.errors.create')
  }
}

async function rename(cfg: WorkflowTypeConfig) {
  const label = prompt(t('admin.workflowConfigs.renamePrompt'), cfg.label)
  if (!label) return
  await updateWorkflowTypeConfig(cfg.workflowKey, { ...cfg, label })
  await refresh()
}

function onBpmnDeployed() {
  bpmnDeployedMessage.value = t('admin.workflowConfigs.deployed')
}

const { t } = useI18n()
</script>

<template>
  <section>
    <h1>{{ t('admin.workflowConfigs.title') }}</h1>
    <p v-if="error" class="error">{{ error }}</p>
    <form class="admin-form" @submit.prevent="create">
      <input v-model="form.workflowKey" :placeholder="t('admin.workflowConfigs.keyPlaceholder')" required />
      <input v-model="form.label" :placeholder="t('admin.workflowConfigs.labelPlaceholder')" required />
      <button type="submit">{{ t('admin.workflowConfigs.create') }}</button>
    </form>
    <ul>
      <li v-for="cfg in configs" :key="cfg.workflowKey">
        <strong>{{ cfg.workflowKey }}</strong> — {{ cfg.label }}
        <button type="button" @click="rename(cfg)">{{ t('admin.workflowConfigs.rename') }}</button>
        <router-link
          class="workflow-config-edit-link"
          :to="{ path: '/admin/bpmn-editor', query: { processDefinitionKey: cfg.workflowKey } }"
        >
          {{ t('admin.workflowConfigs.edit') }}
        </router-link>
      </li>
    </ul>
    <AdminPager v-model="page" :total-pages="totalPages" />

    <BpmnUploadPreview @deployed="onBpmnDeployed" />
    <p v-if="bpmnDeployedMessage" class="bpmn-deployed-message">{{ bpmnDeployedMessage }}</p>
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
.bpmn-deployed-message {
  color: #2f9e44;
}
</style>
