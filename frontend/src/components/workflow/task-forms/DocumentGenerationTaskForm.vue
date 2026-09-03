<script setup lang="ts">
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
    loadError.value = toProblemDetail(err).detail ?? 'Impossible de charger les templates'
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
</script>

<template>
  <div class="document-generation-task-form">
    <p v-if="loading">Chargement…</p>
    <p v-else-if="loadError" class="error">{{ loadError }}</p>
    <p v-else-if="templates.length === 0">Aucun template disponible pour ce type de dossier.</p>
    <template v-else>
      <select v-model="selectedIndex">
        <option :value="null" disabled>Choisir un document…</option>
        <option v-for="(template, index) in templates" :key="index" :value="index">
          {{ template.label }}
        </option>
      </select>
      <button type="button" :disabled="selectedIndex == null" @click="submit">Compléter</button>
    </template>
  </div>
</template>
