<script setup lang="ts">
import { shallowRef, watch } from 'vue'
import { useTemplatePreview } from '@/composables/useTemplatePreview'

const props = defineProps<{
  htmlContent: string
}>()

const variablesText = shallowRef('{}')
const parseError = shallowRef<string | null>(null)
const variables = shallowRef<Record<string, unknown>>({})

watch(
  variablesText,
  (text) => {
    try {
      const parsed: unknown = JSON.parse(text)
      if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
        parseError.value = 'JSON objet requis'
        return
      }
      parseError.value = null
      variables.value = parsed as Record<string, unknown>
    } catch {
      parseError.value = 'JSON invalide'
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
  <aside class="preview-panel" aria-label="Aperçu du template">
    <h2>Aperçu</h2>
    <label class="preview-panel__vars">
      Variables JSON
      <textarea v-model="variablesText" rows="4" />
    </label>
    <p v-if="parseError" class="preview-panel__error">{{ parseError }}</p>
    <p v-else-if="error" class="preview-panel__error">{{ error }}</p>
    <p v-if="loading" class="preview-panel__status">Chargement…</p>
    <iframe class="preview-panel__html" sandbox="" title="Aperçu HTML" :srcdoc="html" />
    <button type="button" @click="previewPdf">Aperçu PDF</button>
    <iframe v-if="pdfUrl" class="preview-panel__pdf" title="Aperçu PDF" :src="pdfUrl" />
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
