<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { onMounted, ref } from 'vue'
import { getDossierById } from '@/api/dossiers'
import { listDossierTypeDocumentTemplates } from '@/api/dossierTypes'
import { toProblemDetail } from '@/api/http'
import type { AllowedDocumentTemplate } from '@/api/types'

// TODO §52 -- kind `document-generation` (taskFormRegistry.ts). Submission never calls
// GenerateDocumentUseCase directly: it only supplies documentType/documentVariant/documentLocale
// as task completion variables -- the exact names DocumentGenerationTask.java (the
// ${documentGenerationTask} serviceTask) reads via execution.getVariable(...), so generation
// stays exactly where it already is, further along in the process.
const props = defineProps<{ dossierId: string }>()
const emit = defineEmits<{ ready: [Record<string, unknown>]; error: [string] }>()

const templates = ref<AllowedDocumentTemplate[]>([])
const selectedIndex = ref<number | null>(null)
const loading = ref(true)
const loadError = ref<string | null>(null)

onMounted(load)

async function load() {
  loading.value = true
  loadError.value = null
  try {
    const dossier = await getDossierById(props.dossierId)
    templates.value = await listDossierTypeDocumentTemplates(dossier.dossierTypeCode)
  } catch (err) {
    loadError.value = toProblemDetail(err).detail ?? t('workflow.taskForms.errors.templates')
  } finally {
    loading.value = false
  }
}

function submit() {
  if (selectedIndex.value == null) return
  const template = templates.value[selectedIndex.value]
  if (!template) return
  emit('ready', {
    documentType: template.templateKey,
    documentVariant: template.variantKey,
    documentLocale: template.locale,
  })
}

const { t } = useI18n()
</script>

<template>
  <div class="document-generation-task-form">
    <p v-if="loading">{{ t('common.loading') }}</p>
    <p v-else-if="loadError" class="error">{{ loadError }}</p>
    <p v-else-if="templates.length === 0">{{ t('workflow.taskForms.noTemplate') }}</p>
    <template v-else>
      <select v-model="selectedIndex">
        <option :value="null" disabled>{{ t('workflow.taskForms.chooseDocument') }}</option>
        <option v-for="(template, index) in templates" :key="index" :value="index">
          {{ template.label }}
        </option>
      </select>
      <button type="button" :disabled="selectedIndex == null" @click="submit">{{ t('workflow.taskForms.complete') }}</button>
    </template>
  </div>
</template>
