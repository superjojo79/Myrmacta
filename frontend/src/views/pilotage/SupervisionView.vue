<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { listAllDossierTypes } from '@/api/dossierTypes'
import { listDossiers } from '@/api/dossiers'
import { toProblemDetail } from '@/api/http'
import type { DossierListItem, DossierStatus, DossierType } from '@/api/types'
import { useDossierStore } from '@/stores/dossier'
import {
  activeFieldFilters,
  fieldFilterInputs,
  fieldFiltersFromQuery,
  MAX_FIELD_FILTERS,
  tooManyFieldFilters,
} from '@/dossier/fieldSearch'

const STATUSES: DossierStatus[] = ['DRAFT', 'OUVERT', 'EN_COURS', 'SUSPENDU', 'CLOTURE', 'ARCHIVE']

const router = useRouter()
const route = useRoute()
const store = useDossierStore()

const query = ref('')
const error = ref<string | null>(null)
const loading = ref(false)
const listLoading = ref(false)
const listError = ref<string | null>(null)
const items = ref<DossierListItem[]>([])
const page = ref(0)
const size = ref(20)
const totalElements = ref(0)
const totalPages = ref(0)
const statusFilter = ref<DossierStatus | ''>('')
const typeFilter = ref('')
const referenceFilter = ref('')
const types = ref<DossierType[]>([])
// TODO §81 (D2) : filtres sur les champs cherchables du type choisi (clés de l'API : `contrat.prefix`, `montant.gte`…).
const fieldFilters = ref<Record<string, string>>({})
const fieldInputs = computed(() =>
  fieldFilterInputs(types.value.find((t) => t.code === typeFilter.value)?.fieldDefinitions),
)
const fieldFilterError = computed(() =>
  tooManyFieldFilters(fieldFilters.value) ? `Au plus ${MAX_FIELD_FILTERS} filtres sur les champs` : null,
)

const hasPrev = computed(() => page.value > 0)
const hasNext = computed(() => page.value + 1 < totalPages.value)

function readFiltersFromRoute() {
  const q = route.query
  statusFilter.value = typeof q.status === 'string' && STATUSES.includes(q.status as DossierStatus)
    ? (q.status as DossierStatus)
    : ''
  typeFilter.value = typeof q.dossierTypeCode === 'string' ? q.dossierTypeCode : ''
  referenceFilter.value = typeof q.reference === 'string' ? q.reference : ''
  fieldFilters.value = typeFilter.value ? fieldFiltersFromQuery(q) : {}
  const rawPage = typeof q.page === 'string' ? Number(q.page) : 0
  page.value = Number.isFinite(rawPage) && rawPage >= 0 ? rawPage : 0
  const rawSize = typeof q.size === 'string' ? Number(q.size) : 20
  size.value = Number.isFinite(rawSize) && rawSize >= 1 && rawSize <= 100 ? rawSize : 20
}

async function syncRoute() {
  const nextQuery: Record<string, string> = {}
  if (statusFilter.value) nextQuery.status = statusFilter.value
  if (typeFilter.value) nextQuery.dossierTypeCode = typeFilter.value
  if (referenceFilter.value) nextQuery.reference = referenceFilter.value
  if (typeFilter.value) {
    for (const [key, value] of Object.entries(activeFieldFilters(fieldFilters.value))) nextQuery[`f.${key}`] = value
  }
  if (page.value > 0) nextQuery.page = String(page.value)
  if (size.value !== 20) nextQuery.size = String(size.value)
  await router.replace({ query: nextQuery })
}

async function loadList() {
  listError.value = null
  listLoading.value = true
  try {
    const result = await listDossiers({
      page: page.value,
      size: size.value,
      status: statusFilter.value || undefined,
      dossierTypeCode: typeFilter.value || undefined,
      reference: referenceFilter.value || undefined,
      fieldFilters: typeFilter.value ? activeFieldFilters(fieldFilters.value) : undefined,
    })
    items.value = result.content ?? []
    totalElements.value = result.totalElements ?? 0
    totalPages.value = result.totalPages ?? 0
    page.value = result.page ?? page.value
    size.value = result.size ?? size.value
  } catch (err) {
    listError.value = toProblemDetail(err).detail ?? 'Impossible de charger la liste'
    items.value = []
  } finally {
    listLoading.value = false
  }
}

async function applyFilters() {
  if (fieldFilterError.value) return
  page.value = 0
  await syncRoute()
  await loadList()
}

async function goPage(next: number) {
  page.value = next
  await syncRoute()
  await loadList()
}

async function openRow(item: DossierListItem) {
  await router.push(`/dossiers/${item.id}`)
}

async function openDossier() {
  error.value = null
  const value = query.value.trim()
  if (!value) {
    error.value = 'Saisir une référence ou un identifiant'
    return
  }
  loading.value = true
  try {
    if (/^[0-9a-f-]{36}$/i.test(value)) {
      await store.loadById(value)
    } else {
      await store.loadByRef(value)
    }
    if (store.dossier) await router.push(`/dossiers/${store.dossier.id}`)
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? 'Dossier introuvable'
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  readFiltersFromRoute()
  try {
    types.value = await listAllDossierTypes()
  } catch {
    types.value = []
  }
  await loadList()
})

watch(
  () => route.query,
  async () => {
    readFiltersFromRoute()
    await loadList()
  },
)
</script>

<template>
  <section class="supervision">
    <h1>Supervision</h1>

    <form class="supervision__filters" @submit.prevent="applyFilters">
      <label>
        Statut
        <select v-model="statusFilter">
          <option value="">Tous</option>
          <option v-for="status in STATUSES" :key="status" :value="status">{{ status }}</option>
        </select>
      </label>
      <label>
        Type
        <select v-model="typeFilter" data-testid="type-filter" @change="fieldFilters = {}">
          <option value="">Tous</option>
          <option v-for="type in types" :key="type.code" :value="type.code">
            {{ type.label }} ({{ type.code }})
          </option>
        </select>
      </label>
      <label>
        Référence
        <input v-model="referenceFilter" type="text" placeholder="préfixe DOS-…" />
      </label>
      <fieldset v-if="fieldInputs.length" class="supervision__fields" data-testid="field-filters">
        <legend>Champs du type</legend>
        <label v-for="input in fieldInputs" :key="input.key">
          {{ input.label }}
          <select v-if="input.kind === 'select'" v-model="fieldFilters[input.key]" :data-testid="`field-filter-${input.key}`">
            <option value="">Tous</option>
            <option v-for="option in input.options" :key="option" :value="option">{{ option }}</option>
          </select>
          <select v-else-if="input.kind === 'boolean'" v-model="fieldFilters[input.key]" :data-testid="`field-filter-${input.key}`">
            <option value="">Tous</option>
            <option value="true">Oui</option>
            <option value="false">Non</option>
          </select>
          <input
            v-else
            v-model="fieldFilters[input.key]"
            :type="input.kind === 'text' ? 'text' : input.kind"
            :step="input.kind === 'number' ? 'any' : undefined"
            :data-testid="`field-filter-${input.key}`"
          />
        </label>
        <p v-if="fieldFilterError" class="supervision__error" role="alert">{{ fieldFilterError }}</p>
      </fieldset>
      <button type="submit" :disabled="listLoading || !!fieldFilterError">Filtrer</button>
    </form>

    <p v-if="listError" class="supervision__error">{{ listError }}</p>
    <p v-if="listLoading">Chargement…</p>

    <table v-else class="supervision__table">
      <thead>
        <tr>
          <th>Référence</th>
          <th>Libellé</th>
          <th>Type</th>
          <th>Statut</th>
          <th>Mis à jour</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="items.length === 0">
          <td colspan="5">Aucun dossier</td>
        </tr>
        <tr
          v-for="item in items"
          :key="item.id"
          class="supervision__row"
          @click="openRow(item)"
        >
          <td>{{ item.reference }}</td>
          <td>{{ item.label }}</td>
          <td>{{ item.dossierTypeLabel || item.dossierTypeCode }}</td>
          <td>{{ item.status }}</td>
          <td>{{ item.updatedAt }}</td>
        </tr>
      </tbody>
    </table>

    <div class="supervision__pager">
      <button type="button" :disabled="!hasPrev || listLoading" @click="goPage(page - 1)">
        Précédent
      </button>
      <span>Page {{ page + 1 }} / {{ Math.max(totalPages, 1) }} ({{ totalElements }})</span>
      <button type="button" :disabled="!hasNext || listLoading" @click="goPage(page + 1)">
        Suivant
      </button>
    </div>

    <h2>Ouverture rapide</h2>
    <p>Ouvrir un dossier par référence ou identifiant.</p>
    <form class="supervision__form" @submit.prevent="openDossier">
      <input v-model="query" type="text" placeholder="DOS-… ou UUID" />
      <button type="submit" :disabled="loading">Ouvrir</button>
      <RouterLink class="supervision__create" to="/dossiers/new">Créer un dossier</RouterLink>
    </form>
    <p v-if="error" class="supervision__error">{{ error }}</p>
  </section>
</template>

<style scoped>
.supervision__fields {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  border: 1px solid #ddd;
  padding: 0.5rem 0.75rem;
}

.supervision__filters,
.supervision__form,
.supervision__pager {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  align-items: end;
  margin: 1rem 0;
}
label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.9rem;
}
input,
select,
button {
  padding: 0.45rem 0.6rem;
}
button {
  background: var(--color-accent);
  color: white;
  border: 0;
  cursor: pointer;
}
button:disabled {
  opacity: 0.6;
  cursor: default;
}
.supervision__table {
  width: 100%;
  border-collapse: collapse;
}
.supervision__table th,
.supervision__table td {
  border-bottom: 1px solid #dee2e6;
  text-align: left;
  padding: 0.5rem;
}
.supervision__row {
  cursor: pointer;
}
.supervision__row:hover {
  background: #f8f9fa;
}
.supervision__create {
  color: var(--color-accent);
}
.supervision__error {
  color: #c92a2a;
}
</style>
