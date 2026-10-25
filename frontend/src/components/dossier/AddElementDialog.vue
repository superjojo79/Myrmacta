<script setup lang="ts">
import { labelOr } from '@/i18n'
import { useI18n } from 'vue-i18n'
import { computed, ref, watch } from 'vue'
import { MANUAL_ELEMENT_TYPES, type ElementType, type PieceType } from '@/api/types'

const props = defineProps<{
  open: boolean
  /** TODO §105 — types de pièces du type de dossier ; vide : pièce non classée. */
  pieceTypes?: PieceType[]
}>()

const emit = defineEmits<{
  close: []
  submitElement: [payload: { type: ElementType; label: string; metadata: Record<string, unknown> }]
  submitAttachment: [payload: { file: File; label: string; pieceKey: string | null }]
}>()

const type = ref<ElementType>('ATTACHMENT')
const label = ref('')
const file = ref<File | null>(null)
const pieceKey = ref('')
const infoKey = ref('')
const infoValue = ref('')
const noteText = ref('')
const linkUrl = ref('')
const linkDescription = ref('')
const error = ref<string | null>(null)

const typeLabel = (type: ElementType): string => labelOr(`dossier.addElement.types.${type}`, type)

const manualTypes = computed(() => MANUAL_ELEMENT_TYPES)

watch(
  () => props.open,
  (open) => {
    if (!open) return
    type.value = 'ATTACHMENT'
    label.value = ''
    file.value = null
    pieceKey.value = ''
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
    error.value = t('dossier.addElement.errors.label')
    return
  }
  if (type.value === 'ATTACHMENT') {
    if (!file.value) {
      error.value = t('dossier.addElement.errors.file')
      return
    }
    emit('submitAttachment', { file: file.value, label: label.value.trim(), pieceKey: pieceKey.value || null })
    return
  }
  if (type.value === 'INFORMATION') {
    if (!infoKey.value.trim()) {
      error.value = t('dossier.addElement.errors.key')
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
      error.value = t('dossier.addElement.errors.text')
      return
    }
    emit('submitElement', { type: 'NOTE', label: label.value.trim(), metadata: { text: noteText.value } })
    return
  }
  if (type.value === 'LINK') {
    if (!linkUrl.value.trim()) {
      error.value = t('dossier.addElement.errors.url')
      return
    }
    emit('submitElement', {
      type: 'LINK',
      label: label.value.trim(),
      metadata: { url: linkUrl.value.trim(), description: linkDescription.value || undefined },
    })
  }
}

const { t } = useI18n()
</script>

<template>
  <div v-if="open" class="dialog" role="dialog" aria-labelledby="add-element-title">
    <h2 id="add-element-title">{{ t('dossier.addElement.title') }}</h2>
    <p v-if="error" class="error">{{ error }}</p>
    <label>
      {{ t('dossier.addElement.type') }}
      <select v-model="type">
        <option v-for="option in manualTypes" :key="option" :value="option">
          {{ typeLabel(option) }}
        </option>
      </select>
    </label>
    <label>
      {{ t('dossier.addElement.label') }}
      <input v-model="label" type="text" />
    </label>

    <label v-if="type === 'ATTACHMENT'">
      {{ t('dossier.addElement.file') }}
      <input type="file" @change="onFileChange" />
    </label>
    <label v-if="type === 'ATTACHMENT' && (pieceTypes?.length ?? 0) > 0">
      {{ t('dossier.pieceTypes.choose') }}
      <select v-model="pieceKey" data-testid="add-element-piece-type">
        <option value="">{{ t('dossier.pieceTypes.unclassified') }}</option>
        <option v-for="pieceType in pieceTypes" :key="pieceType.key" :value="pieceType.key">
          {{ pieceType.label }}
        </option>
      </select>
    </label>

    <template v-if="type === 'INFORMATION'">
      <label>
        {{ t('dossier.addElement.key') }}
        <input v-model="infoKey" type="text" />
      </label>
      <label>
        {{ t('dossier.addElement.value') }}
        <input v-model="infoValue" type="text" />
      </label>
    </template>

    <label v-if="type === 'NOTE'">
      {{ t('dossier.addElement.text') }}
      <textarea v-model="noteText" rows="4"></textarea>
    </label>

    <template v-if="type === 'LINK'">
      <label>
        {{ t('dossier.addElement.url') }}
        <input v-model="linkUrl" type="url" placeholder="https://…" />
      </label>
      <label>
        {{ t('dossier.addElement.description') }}
        <input v-model="linkDescription" type="text" />
      </label>
    </template>

    <div class="actions">
      <button type="button" class="primary" @click="confirm">{{ t('dossier.addElement.add') }}</button>
      <button type="button" @click="emit('close')">{{ t('common.cancel') }}</button>
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
