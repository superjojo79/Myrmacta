<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listAllDossierTypes } from '@/api/dossierTypes'
import { getSlaReport, downloadSlaReportCsv, type SlaReport } from '@/api/slaReport'
import { getOperationalCounts, type OperationalCounts } from '@/api/operations'
import { downloadBiExport, listBiExports, type BiExportFile } from '@/api/biExports'
import { toSlaPeriodParam } from '@/api/slaPeriod'
import { toProblemDetail } from '@/api/http'
import type { DossierType } from '@/api/types'
import { useI18n } from 'vue-i18n'
import { formatDate, formatDateTime } from '@/i18n/format'

const { t } = useI18n()

const report = ref<SlaReport | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const from = ref('')
const to = ref('')
const typeFilter = ref('')
const types = ref<DossierType[]>([])
const operations = ref<OperationalCounts | null>(null)
const biExports = ref<BiExportFile[]>([])

function queryParams() {
  return {
    from: toSlaPeriodParam(from.value),
    to: toSlaPeriodParam(to.value),
    dossierTypeCode: typeFilter.value || undefined,
  }
}

async function refresh() {
  loading.value = true
  error.value = null
  try {
    report.value = await getSlaReport(queryParams())
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('reports.errors.load')
  } finally {
    loading.value = false
  }
  try {
    operations.value = await getOperationalCounts()
  } catch {
    operations.value = null
  }
  try {
    biExports.value = (await listBiExports()).content
  } catch {
    biExports.value = []
  }
}

async function downloadExport(file: BiExportFile) {
  try {
    await downloadBiExport(file)
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('reports.errors.download')
  }
}

async function exportCsv() {
  try {
    await downloadSlaReportCsv(queryParams())
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('reports.errors.export')
  }
}

onMounted(() => {
  void listAllDossierTypes()
    .then((items) => {
      types.value = items
    })
    .catch(() => {
      types.value = []
    })
  void refresh()
})
</script>

<template>
  <section class="rapports" data-testid="rapports-page">
    <header class="rapports__header">
      <div>
        <h1>{{ t('reports.title') }}</h1>
        <p v-if="report" class="muted">
          {{ t('reports.period', { from: formatDate(report.from), to: formatDate(report.to) }) }}
        </p>
      </div>
      <div class="rapports__actions">
        <label>
          {{ t('reports.from') }}
          <input v-model="from" type="datetime-local" data-testid="rapports-from" />
        </label>
        <label>
          {{ t('reports.to') }}
          <input v-model="to" type="datetime-local" data-testid="rapports-to" />
        </label>
        <label>
          {{ t('reports.type') }}
          <select v-model="typeFilter" data-testid="rapports-type">
            <option value="">{{ t('reports.allTypes') }}</option>
            <option v-for="item in types" :key="item.code" :value="item.code">
              {{ item.label }}
            </option>
          </select>
        </label>
        <button type="button" data-testid="rapports-refresh" :disabled="loading" @click="refresh">
          {{ t('reports.apply') }}
        </button>
        <button type="button" data-testid="rapports-export" @click="exportCsv">{{ t('reports.exportCsv') }}</button>
        <button type="button" data-testid="rapports-schedule" disabled :title="t('reports.soon')">
          {{ t('reports.schedule') }}
        </button>
      </div>
    </header>

    <div class="kpi-grid" data-testid="rapports-operations">
      <article class="kpi">
        <p class="kpi__label">{{ t('reports.deadLetterJobs') }}</p>
        <p class="kpi__value" data-testid="kpi-dead-letter">
          {{ operations ? operations.deadLetterJobCount : '—' }}
        </p>
      </article>
      <article class="kpi">
        <p class="kpi__label">{{ t('reports.unassignedTasks') }}</p>
        <p class="kpi__value" data-testid="kpi-unassigned">
          {{ operations ? operations.unassignedOpenTaskCount : '—' }}
        </p>
      </article>
    </div>

    <section>
      <h2>{{ t('reports.biExports') }}</h2>
      <p v-if="biExports.length === 0" class="muted">{{ t('reports.noBiExport') }}</p>
      <table v-else data-testid="rapports-bi-exports">
        <thead>
          <tr>
            <th>{{ t('reports.file') }}</th>
            <th>{{ t('reports.type') }}</th>
            <th>{{ t('reports.date') }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="file in biExports" :key="file.id">
            <td>{{ file.fileName }}</td>
            <td>{{ file.kind === 'SLA' ? 'SLA' : file.dossierTypeCode }}</td>
            <td>{{ formatDateTime(file.createdAt, {}) }}</td>
            <td>
              <button type="button" data-testid="rapports-bi-download" @click="downloadExport(file)">
                {{ t('reports.download') }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <p v-if="loading">{{ t('common.loading') }}</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <template v-else-if="report">
      <div class="kpi-grid">
        <article class="kpi">
          <p class="kpi__label">{{ t('reports.compliance') }}</p>
          <p class="kpi__value" data-testid="kpi-compliance">
            {{ report.compliancePercent == null ? t('reports.na') : t('reports.percent', { n: report.compliancePercent }) }}
          </p>
          <p class="muted">
            {{ t('reports.target', { target: report.complianceTargetPercent, overdue: report.overdueCount }) }}
          </p>
        </article>
        <article class="kpi">
          <p class="kpi__label">{{ t('reports.meanProcessing') }}</p>
          <p class="kpi__value">
            {{ report.meanProcessingDays == null ? t('reports.na') : t('reports.days', { n: report.meanProcessingDays }) }}
          </p>
          <p class="muted">
            {{
              report.meanDeltaVsPreviousDays == null
                ? '—'
                : t('reports.deltaVsPrevious', { n: report.meanDeltaVsPreviousDays })
            }}
            · {{ t('reports.median', { n: report.medianProcessingDays ?? t('reports.na') }) }}
          </p>
        </article>
      </div>

      <section>
        <h2>{{ t('reports.typeShare') }}</h2>
        <div v-for="share in report.typeShares" :key="share.code" class="bar">
          <span>{{ share.label }}</span>
          <span class="bar__track">
            <i :style="{ width: share.percent + '%' }" />
          </span>
          <span>{{ t('reports.percent', { n: share.percent }) }}</span>
        </div>
      </section>

      <section>
        <h2>{{ t('reports.stepTime') }}</h2>
        <div v-for="step in report.stepDurationsHours" :key="step.name" class="bar">
          <span>{{ step.name }}</span>
          <span class="bar__track">
            <i :style="{ width: Math.min(100, step.meanHours) + '%' }" />
          </span>
          <span>{{ t('reports.hours', { n: step.meanHours }) }}</span>
        </div>
      </section>

      <section>
        <h2>{{ t('reports.managerLoad') }}</h2>
        <table>
          <thead>
            <tr>
              <th>{{ t('reports.manager') }}</th>
              <th>{{ t('reports.inProgress') }}</th>
              <th>{{ t('reports.overdue') }}</th>
              <th>{{ t('reports.sla') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in report.managerLoads" :key="row.assigneeId ?? 'unassigned'">
              <td>{{ row.displayName }}</td>
              <td>{{ row.inProgress }}</td>
              <td>{{ row.overdue }}</td>
              <td>{{ row.slaPercent == null ? '—' : t('reports.percent', { n: row.slaPercent }) }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <section>
        <h2>{{ t('reports.typeDetail') }}</h2>
        <table>
          <thead>
            <tr>
              <th>{{ t('reports.type') }}</th>
              <th>{{ t('reports.workflow') }}</th>
              <th>{{ t('reports.volume') }}</th>
              <th>{{ t('reports.meanDelay') }}</th>
              <th>{{ t('reports.slaRespected') }}</th>
              <th>{{ t('reports.producedDocs') }}</th>
              <th>{{ t('reports.trend') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in report.types" :key="row.typeCode">
              <td>{{ row.typeLabel }}</td>
              <td>{{ row.workflowKey }}</td>
              <td>{{ row.volume }}</td>
              <td>{{ row.meanDays == null ? '—' : t('reports.days', { n: row.meanDays }) }}</td>
              <td>{{ row.slaPercent == null ? '—' : t('reports.percent', { n: row.slaPercent }) }}</td>
              <td>{{ row.producedDocCount }}</td>
              <td>
                {{
                  row.volumeTrendPercent == null || row.volumeTrendPercent === 0
                    ? t('reports.stable')
                    : t('reports.percent', { n: `${row.volumeTrendPercent > 0 ? '+' : ''}${row.volumeTrendPercent}` })
                }}
              </td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>
  </section>
</template>

<style scoped>
.rapports {
  display: grid;
  gap: 1.5rem;
}
.rapports__header,
.rapports__actions,
.kpi-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  align-items: end;
  justify-content: space-between;
}
.kpi {
  border: 1px solid #d8dde3;
  padding: 1rem 1.25rem;
  min-width: 16rem;
}
.kpi__label {
  text-transform: uppercase;
  letter-spacing: 0.08em;
  font-size: 0.75rem;
  color: #4d6a7a;
}
.kpi__value {
  font-size: 2.4rem;
  margin: 0.2rem 0;
}
.muted {
  color: #666;
  font-size: 0.9rem;
}
.bar {
  display: grid;
  grid-template-columns: 10rem 1fr 4rem;
  gap: 0.5rem;
  align-items: center;
  margin: 0.35rem 0;
}
.bar__track {
  background: #e8edf1;
  height: 0.55rem;
  display: block;
}
.bar__track i {
  display: block;
  height: 100%;
  background: #3d6b82;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  border-bottom: 1px solid #ddd;
  text-align: left;
  padding: 0.45rem;
}
.error {
  color: #c92a2a;
}
button,
input {
  padding: 0.4rem 0.6rem;
}
</style>
