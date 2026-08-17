<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { MANUAL_ELEMENT_TYPES, type ElementType } from '@/api/types'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  close: []
  submitElement: [payload: { type: ElementType; label: string; metadata: Record<string, unknown> }]
  submitAttachment: [payload: { file: File; label: string }]
}>()

const type = ref<ElementType>('ATTACHMENT')
const label = ref('')
const file = ref<File | null>(null)
const infoKey = ref('')
const infoValue = ref('')
const noteText = ref('')
const linkUrl = ref('')
const linkDescription = ref('')
const error = ref<string | null>(null)

const typeLabels: Record<ElementType, string> = {
  ATTACHMENT: 'Pièce jointe',
  INFORMATION: 'Information',
  NOTE: 'Note',
  LINK: 'Lien',
  PRODUCED_DOC: 'Document généré',
}

const manualTypes = computed(() => MANUAL_ELEMENT_TYPES)

watch(
  () => props.open,
  (open) => {
    if (!open) return
    type.value = 'ATTACHMENT'
    label.value = ''
    file.value = null
    infoKey.value = ''
    infoValue.value = ''
    noteText.value = ''
    linkUrl.value = ''
    linkDescription.value = ''
    error.value = null
  },
)

function onFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  file.value = target.files?.[0] ?? null
}

function confirm() {
  error.value = null
  if (!label.value.trim()) {
    error.value = 'Libellé requis'
    return
  }
  if (type.value === 'ATTACHMENT') {
    if (!file.value) {
      error.value = 'Sélectionnez un fichier'
      return
    }
    emit('submitAttachment', { file: file.value, label: label.value.trim() })
    return
  }
  if (type.value === 'INFORMATION') {
    if (!infoKey.value.trim()) {
      error.value = 'Clé requise'
      return
    }
    emit('submitElement', {
      type: 'INFORMATION',
      label: label.value.trim(),
      metadata: { [infoKey.value.trim()]: infoValue.value },
    })
    return
  }
  if (type.value === 'NOTE') {
    if (!noteText.value.trim()) {
      error.value = 'Texte requis'
      return
    }
    emit('submitElement', { type: 'NOTE', label: label.value.trim(), metadata: { text: noteText.value } })
    return
  }
  if (type.value === 'LINK') {
    if (!linkUrl.value.trim()) {
      error.value = 'URL requise'
      return
    }
    emit('submitElement', {
      type: 'LINK',
      label: label.value.trim(),
      metadata: { url: linkUrl.value.trim(), description: linkDescription.value || undefined },
    })
  }
}
</script>

<template>
  <div v-if="open" class="dialog" role="dialog" aria-labelledby="add-element-title">
    <h2 id="add-element-title">Ajouter un élément</h2>
    <p v-if="error" class="error">{{ error }}</p>
    <label>
      Type
      <select v-model="type">
        <option v-for="option in manualTypes" :key="option" :value="option">
          {{ typeLabels[option] }}
        </option>
      </select>
    </label>
    <label>
      Libellé
      <input v-model="label" type="text" />
    </label>

    <label v-if="type === 'ATTACHMENT'">
      Fichier
      <input type="file" @change="onFileChange" />
    </label>

    <template v-if="type === 'INFORMATION'">
      <label>
        Clé
        <input v-model="infoKey" type="text" />
      </label>
      <label>
        Valeur
        <input v-model="infoValue" type="text" />
      </label>
    </template>

    <label v-if="type === 'NOTE'">
      Texte
      <textarea v-model="noteText" rows="4"></textarea>
    </label>

    <template v-if="type === 'LINK'">
      <label>
        URL
        <input v-model="linkUrl" type="url" placeholder="https://…" />
      </label>
      <label>
        Description
        <input v-model="linkDescription" type="text" />
      </label>
    </template>

    <div class="actions">
      <button type="button" class="primary" @click="confirm">Ajouter</button>
      <button type="button" @click="emit('close')">Annuler</button>
    </div>
  </div>
</template>

<style scoped>
.dialog {
  border: 1px solid #ccc;
  padding: 1rem;
  margin-top: 1rem;
  background: white;
}
.dialog label {
  display: block;
  margin-top: 0.5rem;
}
.dialog input,
.dialog select,
.dialog textarea {
  display: block;
  width: 100%;
  margin-top: 0.2rem;
}
.actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.75rem;
}
.error {
  color: #c92a2a;
}
button.primary {
  background: var(--color-accent);
  color: white;
  border-color: transparent;
}
</style>
