<script setup lang="ts">
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
    error.value = toProblemDetail(err).detail ?? 'Recherche impossible'
  } finally {
    searching.value = false
  }
}

function confirm() {
  if (!selected.value) {
    error.value = 'Sélectionnez un dossier cible'
    return
  }
  emit('submit', { targetDossierId: selected.value.id, type: type.value })
}
</script>

<template>
  <div v-if="open" class="dialog" role="dialog" aria-labelledby="link-dossier-title">
    <h2 id="link-dossier-title">Lier un dossier</h2>
    <p v-if="error" class="error">{{ error }}</p>
    <label>
      Rechercher
      <input v-model="query" type="text" placeholder="préfixe DOS-…" @keyup.enter="search" />
    </label>
    <button type="button" @click="search" :disabled="searching">Rechercher</button>
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
      Type
      <select v-model="type">
        <option v-for="option in RELATION_TYPES" :key="option" :value="option">{{ option }}</option>
      </select>
    </label>
    <div class="actions">
      <button type="button" class="primary" @click="confirm">Lier</button>
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
