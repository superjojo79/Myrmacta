<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { shallowRef, watch } from 'vue'
import { useTemplatePreview } from '@/composables/useTemplatePreview'

const props = defineProps<{
  htmlContent: string
}>()
const { t } = useI18n()
const emit = defineEmits<{ 'update:variables': [variables: Record<string, unknown>] }>()

const variablesText = shallowRef('{}')
const parseError = shallowRef<string | null>(null)
const variables = shallowRef<Record<string, unknown>>({})

watch(
  variablesText,
  (text) => {
    try {
      const parsed: unknown = JSON.parse(text)
      if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
        parseError.value = t('admin.preview.errors.object')
        return
      }
      parseError.value = null
      variables.value = parsed as Record<string, unknown>
      // TODO §84 (LC4b) : l'éditeur visuel propose ces variables d'exemple dans son menu
      emit('update:variables', variables.value)
    } catch {
      parseError.value = t('admin.preview.errors.invalid')
    }
  },
  { immediate: true },
)

const { html, pdfUrl, loading, error, previewPdf } = useTemplatePreview({
  htmlContent: () => props.htmlContent,
  variables: () => variables.value,
  enabled: () => parseError.value === null,
})
</script>

<template>
  <aside class="preview-panel" :aria-label="t('admin.preview.aria')">
    <h2>{{ t('admin.preview.title') }}</h2>
    <label class="preview-panel__vars">
      {{ t('admin.preview.variables') }}
      <textarea v-model="variablesText" rows="4" />
    </label>
    <p v-if="parseError" class="preview-panel__error">{{ parseError }}</p>
    <p v-else-if="error" class="preview-panel__error">{{ error }}</p>
    <p v-if="loading" class="preview-panel__status">{{ t('common.loading') }}</p>
    <iframe class="preview-panel__html" sandbox="" :title="t('admin.preview.htmlTitle')" :srcdoc="html" />
    <button type="button" :disabled="parseError !== null" @click="previewPdf">{{ t('admin.preview.pdfButton') }}</button>
    <iframe v-if="pdfUrl" class="preview-panel__pdf" :title="t('admin.preview.pdfTitle')" :src="pdfUrl" />
  </aside>
</template>

<style scoped>
.preview-panel {
  display: grid;
  gap: 0.5rem;
  min-width: 280px;
}
.preview-panel__vars {
  display: grid;
  gap: 0.25rem;
}
.preview-panel__html,
.preview-panel__pdf {
  width: 100%;
  min-height: 240px;
  border: 1px solid #ddd;
  background: #f4f4f4;
}
.preview-panel__error {
  color: #c92a2a;
  margin: 0;
}
.preview-panel__status {
  margin: 0;
  color: #555;
}
</style>
