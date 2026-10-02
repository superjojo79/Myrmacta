<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ref, watch } from 'vue'
import { listDossiers } from '@/api/dossiers'
import { toProblemDetail } from '@/api/http'
import { RELATION_TYPES, type DossierListItem, type RelationType } from '@/api/types'

const props = defineProps<{
  open: boolean
  currentDossierId: string
}>()

const emit = defineEmits<{
  close: []
  submit: [payload: { targetDossierId: string; type: RelationType }]
}>()

const query = ref('')
const results = ref<DossierListItem[]>([])
const selected = ref<DossierListItem | null>(null)
const type = ref<RelationType>('LINKED')
const error = ref<string | null>(null)
const searching = ref(false)

watch(
  () => props.open,
  (open) => {
    if (!open) return
    query.value = ''
    results.value = []
    selected.value = null
    type.value = 'LINKED'
    error.value = null
  },
)

async function search() {
  error.value = null
  searching.value = true
  try {
    const page = await listDossiers({ reference: query.value || undefined, size: 10 })
    results.value = page.content.filter((item) => item.id !== props.currentDossierId)
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('dossier.linkDialog.errors.search')
  } finally {
    searching.value = false
  }
}

function confirm() {
  if (!selected.value) {
    error.value = t('dossier.linkDialog.errors.select')
    return
  }
  emit('submit', { targetDossierId: selected.value.id, type: type.value })
}

const { t } = useI18n()
</script>

<template>
  <div v-if="open" class="dialog" role="dialog" aria-labelledby="link-dossier-title">
    <h2 id="link-dossier-title">{{ t('dossier.linkDialog.title') }}</h2>
    <p v-if="error" class="error">{{ error }}</p>
    <label>
      {{ t('dossier.linkDialog.search') }}
      <input v-model="query" type="text" :placeholder="t('dossier.linkDialog.searchPlaceholder')" @keyup.enter="search" />
    </label>
    <button type="button" @click="search" :disabled="searching">{{ t('dossier.linkDialog.search') }}</button>
    <ul class="results">
      <li v-for="item in results" :key="item.id">
        <button
          type="button"
          :class="{ selected: selected?.id === item.id }"
          @click="selected = item"
        >
          {{ item.label }} · {{ item.reference }}
        </button>
      </li>
    </ul>
    <label>
      {{ t('dossier.linkDialog.type') }}
      <select v-model="type">
        <option v-for="option in RELATION_TYPES" :key="option" :value="option">{{ option }}</option>
      </select>
    </label>
    <div class="actions">
      <button type="button" class="primary" @click="confirm">{{ t('dossier.linkDialog.link') }}</button>
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
.results {
  list-style: none;
  padding: 0;
}
.results button {
  width: 100%;
  text-align: left;
}
.selected {
  outline: 2px solid var(--color-accent);
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
