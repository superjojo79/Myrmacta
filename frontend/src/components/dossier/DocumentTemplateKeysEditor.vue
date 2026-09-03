<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listDocumentTemplates } from '@/api/admin'
import { listAllPages } from '@/api/page'
import { toProblemDetail } from '@/api/http'

// TODO §52 -- association DossierType.documentTemplateKeys, écran Admin -> Types. Réutilise
// GET /api/document-templates (admin-scopé, existant) pour dédupliquer les templateKey ACTIVE ;
// pas de nouvel endpoint admin nécessaire pour ce picker (contrairement au picker task-form, qui
// lui n'est pas admin-only -- voir DocumentGenerationTaskForm.vue).
const props = defineProps<{ modelValue: string[] }>()
const emit = defineEmits<{ 'update:modelValue': [string[]] }>()

const availableKeys = ref<string[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

onMounted(load)

async function load() {
  loading.value = true
  error.value = null
  try {
    const templates = await listAllPages((page, size) =>
      listDocumentTemplates(undefined, false, { page, size }),
    )
    availableKeys.value = [
      ...new Set(templates.filter((t) => t.status === 'ACTIVE').map((t) => t.templateKey)),
    ]
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? 'Impossible de charger les templates'
  } finally {
    loading.value = false
  }
}

function toggle(key: string, checked: boolean) {
  const next = checked
    ? [...props.modelValue, key]
    : props.modelValue.filter((k) => k !== key)
  emit('update:modelValue', next)
}
</script>

<template>
  <div class="document-template-keys-editor">
    <p v-if="loading">Chargement…</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="availableKeys.length === 0">Aucun template actif.</p>
    <label
      v-for="key in availableKeys"
      :key="key"
      class="document-template-keys-editor__row"
      data-testid="document-template-key-row"
    >
      <input
        type="checkbox"
        :checked="modelValue.includes(key)"
        :data-testid="`document-template-key-${key}`"
        @change="toggle(key, ($event.target as HTMLInputElement).checked)"
      />
      {{ key }}
    </label>
  </div>
</template>

<style scoped>
.document-template-keys-editor__row {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-bottom: 0.35rem;
}
.error {
  color: #c92a2a;
}
</style>
