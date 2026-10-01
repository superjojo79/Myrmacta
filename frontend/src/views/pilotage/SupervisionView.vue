<script setup lang="ts">
import { useI18n } from 'vue-i18n'
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

const { t } = useI18n()
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
  tooManyFieldFilters(fieldFilters.value) ? t('supervision.errors.tooManyFilters', { max: MAX_FIELD_FILTERS }) : null,
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
    listError.value = toProblemDetail(err).detail ?? t('supervision.errors.listFailed')
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
    error.value = t('supervision.errors.needReference')
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
    error.value = toProblemDetail(err).detail ?? t('supervision.errors.notFound')
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
    <h1>{{ t('supervision.title') }}</h1>

    <form class="supervision__filters" @submit.prevent="applyFilters">
      <label>
        {{ t('supervision.status') }}
        <select v-model="statusFilter">
          <option value="">{{ t('supervision.all') }}</option>
          <option v-for="status in STATUSES" :key="status" :value="status">{{ status }}</option>
        </select>
      </label>
      <label>
        {{ t('supervision.type') }}
        <select v-model="typeFilter" data-testid="type-filter" @change="fieldFilters = {}">
          <option value="">{{ t('supervision.all') }}</option>
          <option v-for="type in types" :key="type.code" :value="type.code">
            {{ type.label }} ({{ type.code }})
          </option>
        </select>
      </label>
      <label>
        {{ t('supervision.reference') }}
        <input v-model="referenceFilter" type="text" :placeholder="t('supervision.referencePlaceholder')" />
      </label>
      <fieldset v-if="fieldInputs.length" class="supervision__fields" data-testid="field-filters">
        <legend>{{ t('supervision.typeFields') }}</legend>
        <label v-for="input in fieldInputs" :key="input.key">
          {{ input.label }}
          <select v-if="input.kind === 'select'" v-model="fieldFilters[input.key]" :data-testid="`field-filter-${input.key}`">
            <option value="">{{ t('supervision.all') }}</option>
            <option v-for="option in input.options" :key="option" :value="option">{{ option }}</option>
          </select>
          <select v-else-if="input.kind === 'boolean'" v-model="fieldFilters[input.key]" :data-testid="`field-filter-${input.key}`">
            <option value="">{{ t('supervision.all') }}</option>
            <option value="true">{{ t('common.yes') }}</option>
            <option value="false">{{ t('common.no') }}</option>
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
      <button type="submit" :disabled="listLoading || !!fieldFilterError">{{ t('supervision.filter') }}</button>
    </form>

    <p v-if="listError" class="supervision__error">{{ listError }}</p>
    <p v-if="listLoading">{{ t('common.loading') }}</p>

    <table v-else class="supervision__table">
      <thead>
        <tr>
          <th>{{ t('supervision.reference') }}</th>
          <th>{{ t('supervision.label') }}</th>
          <th>{{ t('supervision.type') }}</th>
          <th>{{ t('supervision.status') }}</th>
          <th>{{ t('supervision.updated') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="items.length === 0">
          <td colspan="5">{{ t('supervision.empty') }}</td>
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
        {{ t('common.previous') }}
      </button>
      <span>{{ t('supervision.page', { page: page + 1, pages: Math.max(totalPages, 1), total: totalElements }) }}</span>
      <button type="button" :disabled="!hasNext || listLoading" @click="goPage(page + 1)">
        {{ t('common.next') }}
      </button>
    </div>

    <h2>{{ t('supervision.quickOpen') }}</h2>
    <p>{{ t('supervision.quickOpenHint') }}</p>
    <form class="supervision__form" @submit.prevent="openDossier">
      <input v-model="query" type="text" :placeholder="t('supervision.quickPlaceholder')" />
      <button type="submit" :disabled="loading">{{ t('supervision.open') }}</button>
      <RouterLink class="supervision__create" to="/dossiers/new">{{ t('supervision.create') }}</RouterLink>
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
