<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { listTenantIncidents, retryIncident } from '@/api/incidents'
import { toProblemDetail } from '@/api/http'
import type { TenantIncident } from '@/api/types'
import AdminPager from '@/components/admin/AdminPager.vue'
import { formatDateTime } from '@/i18n/format'

/** TODO §94 — supervision des étapes en échec du tenant (TENANT_ADMIN / WORKFLOW_ADMIN). */
const PAGE_SIZE = 20

const { t } = useI18n()
const rows = ref<TenantIncident[]>([])
const page = ref(0)
const totalPages = ref(0)
const truncated = ref(false)
const loading = ref(false)
const error = ref<string | null>(null)
const retryingJobId = ref<string | null>(null)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const result = await listTenantIncidents({ page: page.value, size: PAGE_SIZE })
    // Revue L6 : un rejeu qui vide la dernière page ramène à la précédente plutôt qu'à un faux « aucun échec ».
    if (result.content.length === 0 && page.value > 0) {
      page.value -= 1
      return
    }
    rows.value = result.content
    totalPages.value = result.totalPages
    truncated.value = result.truncated
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('admin.incidents.loadError')
    rows.value = []
  } finally {
    loading.value = false
  }
}

async function onRetry(row: TenantIncident): Promise<void> {
  error.value = null
  retryingJobId.value = row.jobId
  try {
    await retryIncident(row.linkId, row.jobId)
    await load()
  } catch (err) {
    const problem = toProblemDetail(err)
    error.value =
      problem.status === 409
        ? t('workflow.incidents.alreadyRetried')
        : (problem.detail ?? t('workflow.incidents.retryError'))
  } finally {
    retryingJobId.value = null
  }
}

watch(page, () => void load())
onMounted(load)
</script>

<template>
  <section class="incidents-admin">
    <h1>{{ t('admin.incidents.title') }}</h1>
    <p class="incidents-admin__intro">{{ t('admin.incidents.intro') }}</p>
    <p v-if="error" class="incidents-admin__error" role="alert">{{ error }}</p>
    <p v-if="truncated" class="incidents-admin__truncated" data-testid="incident-admin-truncated">
      {{ t('admin.incidents.truncated') }}
    </p>
    <p v-if="!loading && !error && rows.length === 0">{{ t('admin.incidents.empty') }}</p>
    <table v-if="rows.length > 0">
      <thead>
        <tr>
          <th>{{ t('admin.incidents.columns.dossier') }}</th>
          <th>{{ t('admin.incidents.columns.workflow') }}</th>
          <th>{{ t('admin.incidents.columns.step') }}</th>
          <th>{{ t('admin.incidents.columns.state') }}</th>
          <th>{{ t('admin.incidents.columns.since') }}</th>
          <th>{{ t('admin.incidents.columns.message') }}</th>
          <th>{{ t('admin.incidents.columns.actions') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.jobId" data-testid="incident-admin-row">
          <td>
            <RouterLink :to="`/dossiers/${row.dossierId}`">{{
              row.dossierReference ?? row.dossierId
            }}</RouterLink>
          </td>
          <td>{{ row.processDefinitionKey ?? '—' }}</td>
          <td>{{ row.activityName }}</td>
          <td>
            {{
              row.state === 'INCIDENT'
                ? t('workflow.incidents.state.INCIDENT')
                : t('workflow.incidents.state.RETRYING', row.retriesLeft)
            }}
          </td>
          <td>{{ row.since ? formatDateTime(row.since) : '—' }}</td>
          <td class="incidents-admin__message">{{ row.message ?? '—' }}</td>
          <td>
            <button
              v-if="row.state === 'INCIDENT'"
              type="button"
              data-testid="incident-admin-retry"
              :disabled="retryingJobId === row.jobId"
              :aria-label="t('workflow.incidents.retryStep', { step: row.activityName })"
              @click="onRetry(row)"
            >
              {{ t('workflow.incidents.retry') }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>
    <AdminPager v-if="totalPages > 1" v-model="page" :total-pages="totalPages" />
  </section>
</template>

<style scoped>
.incidents-admin table {
  width: 100%;
  border-collapse: collapse;
}
.incidents-admin th,
.incidents-admin td {
  text-align: left;
  padding: 0.4rem 0.5rem;
  border-bottom: 1px solid #e2e2e5;
  vertical-align: top;
}
.incidents-admin__message {
  max-width: 28rem;
  word-break: break-word;
  font-size: 0.85rem;
}
.incidents-admin__error {
  color: #a12a2a;
}
.incidents-admin__truncated,
.incidents-admin__intro {
  color: #55595f;
}
</style>
