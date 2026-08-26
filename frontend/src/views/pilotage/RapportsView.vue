<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getSlaReport, downloadSlaReportCsv, type SlaReport } from '@/api/slaReport'
import { toProblemDetail } from '@/api/http'

const report = ref<SlaReport | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const from = ref('')
const to = ref('')

async function refresh() {
  loading.value = true
  error.value = null
  try {
    report.value = await getSlaReport({
      from: from.value || undefined,
      to: to.value || undefined,
    })
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? 'Chargement impossible'
  } finally {
    loading.value = false
  }
}

async function exportCsv() {
  try {
    await downloadSlaReportCsv({
      from: from.value || undefined,
      to: to.value || undefined,
    })
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? 'Export impossible'
  }
}

onMounted(() => {
  void refresh()
})
</script>

<template>
  <section class="rapports" data-testid="rapports-page">
    <header class="rapports__header">
      <div>
        <h1>Rapports &amp; SLA</h1>
        <p v-if="report" class="muted">
          Période : {{ new Date(report.from).toLocaleDateString('fr-FR') }} →
          {{ new Date(report.to).toLocaleDateString('fr-FR') }}
        </p>
      </div>
      <div class="rapports__actions">
        <label>
          Du
          <input v-model="from" type="datetime-local" />
        </label>
        <label>
          Au
          <input v-model="to" type="datetime-local" />
        </label>
        <button type="button" data-testid="rapports-refresh" :disabled="loading" @click="refresh">
          Appliquer
        </button>
        <button type="button" data-testid="rapports-export" @click="exportCsv">Exporter CSV</button>
        <button type="button" data-testid="rapports-schedule" disabled title="Bientôt">
          Planifier l'envoi
        </button>
      </div>
    </header>
    <p v-if="loading">Chargement…</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <template v-else-if="report">
      <div class="kpi-grid">
        <article class="kpi">
          <p class="kpi__label">Conformité SLA</p>
          <p class="kpi__value" data-testid="kpi-compliance">
            {{ report.compliancePercent == null ? 'n/a' : `${report.compliancePercent} %` }}
          </p>
          <p class="muted">
            Objectif {{ report.complianceTargetPercent }} % · {{ report.overdueCount }} dossiers hors
            délai
          </p>
        </article>
        <article class="kpi">
          <p class="kpi__label">Délai moyen de traitement</p>
          <p class="kpi__value">
            {{ report.meanProcessingDays == null ? 'n/a' : `${report.meanProcessingDays} j` }}
          </p>
          <p class="muted">
            {{
              report.meanDeltaVsPreviousDays == null
                ? '—'
                : `${report.meanDeltaVsPreviousDays} j vs période précédente`
            }}
            · médiane {{ report.medianProcessingDays ?? 'n/a' }} j
          </p>
        </article>
      </div>
      <section>
        <h2>Répartition par type</h2>
        <div v-for="share in report.typeShares" :key="share.code" class="bar">
          <span>{{ share.label }}</span>
          <span class="bar__track">
            <i :style="{ width: share.percent + '%' }" />
          </span>
          <span>{{ share.percent }} %</span>
        </div>
      </section>
      <section>
        <h2>Temps passé par étape</h2>
        <div v-for="step in report.stepDurationsHours" :key="step.name" class="bar">
          <span>{{ step.name }}</span>
          <span class="bar__track">
            <i :style="{ width: Math.min(100, step.meanHours) + '%' }" />
          </span>
          <span>{{ step.meanHours }} h</span>
        </div>
      </section>
      <section>
        <h2>Charge par gestionnaire</h2>
        <table>
          <thead>
            <tr>
              <th>Gestionnaire</th>
              <th>En cours</th>
              <th>Hors délai</th>
              <th>SLA</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in report.managerLoads" :key="row.assigneeId ?? 'unassigned'">
              <td>{{ row.displayName }}</td>
              <td>{{ row.inProgress }}</td>
              <td>{{ row.overdue }}</td>
              <td>{{ row.slaPercent == null ? '—' : `${row.slaPercent} %` }}</td>
            </tr>
          </tbody>
        </table>
      </section>
      <section>
        <h2>Détail par type de dossier</h2>
        <table>
          <thead>
            <tr>
              <th>Type</th>
              <th>Workflow</th>
              <th>Volume</th>
              <th>Délai moyen</th>
              <th>SLA respecté</th>
              <th>Documents produits</th>
              <th>Tendance</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in report.types" :key="row.typeCode">
              <td>{{ row.typeLabel }}</td>
              <td>{{ row.workflowKey }}</td>
              <td>{{ row.volume }}</td>
              <td>{{ row.meanDays == null ? '—' : `${row.meanDays} j` }}</td>
              <td>{{ row.slaPercent == null ? '—' : `${row.slaPercent} %` }}</td>
              <td>{{ row.producedDocCount }}</td>
              <td>
                {{
                  row.volumeTrendPercent == null || row.volumeTrendPercent === 0
                    ? 'stable'
                    : `${row.volumeTrendPercent > 0 ? '+' : ''}${row.volumeTrendPercent} %`
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
