<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { formatDateTime } from '@/i18n/format'
import { computed, onMounted, ref, watch } from 'vue'
import { toProblemDetail } from '@/api/http'
import {
  applyImport,
  downloadExport,
  listImports,
  parseParameters,
  planImport,
  type ConfigImportRecord,
  type ImportPlan,
  type ImportReport,
  type PlanEntry,
} from '@/api/configuration'

const sourceLabel = ref('')
const file = ref<File | null>(null)
const parametersText = ref('')
const plan = ref<ImportPlan | null>(null)
const report = ref<ImportReport | null>(null)
const { t } = useI18n()
const journal = ref<ConfigImportRecord[]>([])
const busy = ref(false)
const error = ref<string | null>(null)
// Secrets saisis pour la création de connecteurs : gardés dans ce composant uniquement, effacés après l'application.
const secrets = ref<Record<string, Record<string, string>>>({})

const parameters = computed(() => parseParameters(parametersText.value))
const parametersInvalid = computed(() => parameters.value === null)

const planLabel = (action: string): string => t(`admin.promotion.planActions.${action}`)
const outcomeLabel = (outcome: string): string => t(`admin.promotion.outcomes.${outcome}`)
const artifactLabel = (type: string): string => t(`admin.promotion.artifacts.${type}`)

const applicableCount = computed(() => plan.value?.entries.filter((e) => e.action === 'CREATE' || e.action === 'UPDATE').length ?? 0)
const connectorsNeedingSecrets = computed(() =>
  (plan.value?.entries ?? []).filter((e) => e.type === 'CONNECTOR_INSTANCE' && e.secretsRequired.length > 0),
)

/** Les secrets sont indexés par le NOM exact du connecteur (revue D4 H1), pas par la clé de fichier (un slug). */
function connectorName(entry: PlanEntry): string {
  return entry.connectorName ?? entry.key
}

// Le plan relu a été calculé avec ces paramètres : les modifier l'invalide (revue D4 L4).
watch(parametersText, () => {
  plan.value = null
  report.value = null
})

async function runExport() {
  error.value = null
  try {
    await downloadExport(sourceLabel.value)
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('admin.promotion.errors.export')
  }
}

function onFile(event: Event) {
  const input = event.target as HTMLInputElement
  file.value = input.files?.[0] ?? null
  plan.value = null
  report.value = null
  secrets.value = {}
}

function setSecret(connector: string, field: string, value: string) {
  secrets.value = { ...secrets.value, [connector]: { ...(secrets.value[connector] ?? {}), [field]: value } }
}

async function loadJournal() {
  try {
    journal.value = await listImports(10)
  } catch {
    journal.value = []
  }
}

async function runPlan() {
  if (!file.value || parameters.value === null) return
  busy.value = true
  error.value = null
  report.value = null
  try {
    plan.value = await planImport(file.value, parameters.value)
  } catch (err) {
    plan.value = null
    error.value = toProblemDetail(err).detail ?? t('admin.promotion.errors.plan')
  } finally {
    busy.value = false
  }
}

async function runApply() {
  if (!file.value || !plan.value || parameters.value === null) return
  if (!window.confirm(t('admin.promotion.confirmApply', { count: applicableCount.value }))) return
  busy.value = true
  error.value = null
  try {
    const nonEmpty: Record<string, Record<string, string>> = {}
    for (const [connector, fields] of Object.entries(secrets.value)) {
      const filled = Object.fromEntries(Object.entries(fields).filter(([, v]) => v !== ''))
      if (Object.keys(filled).length) nonEmpty[connector] = filled
    }
    report.value = await applyImport(file.value, {
      parameters: parameters.value,
      secrets: nonEmpty,
      expectedBundleSha256: plan.value.bundleSha256,
    })
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('admin.promotion.errors.apply')
  } finally {
    secrets.value = {}
    busy.value = false
    await loadJournal()
  }
}

onMounted(loadJournal)
</script>

<template>
  <section class="promotion">
    <h1>{{ t('admin.promotion.title') }}</h1>
    <i18n-t keypath="admin.promotion.intro" tag="p" class="promotion__intro">
      <template #drafts><strong>{{ t('admin.promotion.introDrafts') }}</strong></template>
      <template #disabled><strong>{{ t('admin.promotion.introDisabled') }}</strong></template>
    </i18n-t>

    <h2>{{ t('admin.promotion.export') }}</h2>
    <form class="promotion__export" @submit.prevent>
      <label>
        {{ t('admin.promotion.sourceName') }}
        <input v-model="sourceLabel" type="text" maxlength="200" :placeholder="t('admin.promotion.sourcePlaceholder')" data-testid="source-label" />
      </label>
      <button type="button" class="promotion__button" data-testid="export-button" @click="runExport">{{ t('admin.promotion.download') }}</button>
    </form>

    <h2>{{ t('admin.promotion.import') }}</h2>
    <p v-if="error" class="promotion__error" role="alert" data-testid="promotion-error">{{ error }}</p>
    <form class="promotion__import" @submit.prevent="runPlan">
      <label>
        {{ t('admin.promotion.bundle') }}
        <input type="file" accept=".zip,application/zip" data-testid="bundle-file" @change="onFile" />
      </label>
      <label>
        <i18n-t keypath="admin.promotion.parameters" tag="span">
          <template #example><code>&#123;&#123;nom&#125;&#125;</code></template>
        </i18n-t>
        <textarea v-model="parametersText" rows="2" placeholder='{"url_erp": "https://erp.exemple.fr"}' data-testid="parameters" />
      </label>
      <p v-if="parametersInvalid" class="promotion__error" role="alert">{{ t('admin.promotion.parametersInvalid') }}</p>
      <button type="submit" :disabled="!file || busy || parametersInvalid" data-testid="plan-button">{{ t('admin.promotion.plan') }}</button>
    </form>

    <template v-if="plan">
      <h3>{{ t('admin.promotion.planTitle') }}</h3>
      <table class="promotion__table" data-testid="plan-table">
        <thead>
          <tr><th>{{ t('admin.promotion.artifact') }}</th><th>{{ t('admin.promotion.key') }}</th><th>{{ t('admin.promotion.action') }}</th><th>{{ t('admin.promotion.detail') }}</th></tr>
        </thead>
        <tbody>
          <tr v-for="e in plan.entries" :key="`${e.type}:${e.key}`" :data-testid="`plan-${e.type}-${e.key}`">
            <td>{{ artifactLabel(e.type) }}</td>
            <td>{{ e.key }}</td>
            <td><span :class="['promotion__badge', `promotion__badge--${e.action.toLowerCase()}`]">{{ planLabel(e.action) }}</span></td>
            <td>{{ e.reason }}</td>
          </tr>
        </tbody>
      </table>

      <div v-if="connectorsNeedingSecrets.length" class="promotion__secrets" data-testid="secrets-block">
        <h4>{{ t('admin.promotion.secretsTitle') }}</h4>
        <p>{{ t('admin.promotion.secretsHint') }}</p>
        <fieldset v-for="e in connectorsNeedingSecrets" :key="e.key">
          <legend>{{ connectorName(e) }}</legend>
          <label v-for="field in e.secretsRequired" :key="field">
            {{ field }}
            <input
              type="password"
              autocomplete="new-password"
              :data-testid="`secret-${e.key}-${field}`"
              :value="secrets[connectorName(e)]?.[field] ?? ''"
              @input="setSecret(connectorName(e), field, ($event.target as HTMLInputElement).value)"
            />
          </label>
        </fieldset>
      </div>

      <button type="button" :disabled="busy || applicableCount === 0" data-testid="apply-button" @click="runApply">
        {{ t('admin.promotion.apply', { count: applicableCount }) }}
      </button>
    </template>

    <template v-if="report">
      <h3>{{ t('admin.promotion.report') }}</h3>
      <table class="promotion__table" data-testid="report-table">
        <thead>
          <tr><th>{{ t('admin.promotion.artifact') }}</th><th>{{ t('admin.promotion.key') }}</th><th>{{ t('admin.promotion.result') }}</th><th>{{ t('admin.promotion.detail') }}</th></tr>
        </thead>
        <tbody>
          <tr v-for="e in report.entries" :key="`${e.type}:${e.key}`">
            <td>{{ artifactLabel(e.type) }}</td>
            <td>{{ e.key }}</td>
            <td><span :class="['promotion__badge', `promotion__badge--${e.outcome.toLowerCase()}`]">{{ outcomeLabel(e.outcome) }}</span></td>
            <td>{{ e.message }}</td>
          </tr>
        </tbody>
      </table>
      <i18n-t keypath="admin.promotion.reportHint" tag="p">
        <template #link><RouterLink to="/admin/drafts">{{ t('admin.promotion.draftsLink') }}</RouterLink></template>
      </i18n-t>
    </template>

    <h2>{{ t('admin.promotion.lastImports') }}</h2>
    <p v-if="journal.length === 0">{{ t('admin.promotion.noImport') }}</p>
    <ul v-else class="promotion__log" data-testid="import-log">
      <li v-for="j in journal" :key="j.id">
        {{ formatDateTime(j.performedAt, {}) }} — {{ j.performedBy }}
        <template v-if="j.sourceLabel">({{ j.sourceLabel }})</template> :
        {{ t('admin.promotion.logLine', { applied: j.applied, unchanged: j.unchanged, skipped: j.skipped, failed: j.failed }) }}
        <small>[{{ j.bundleSha256.slice(0, 8) }}]</small>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.promotion__intro {
  max-width: 60rem;
}
.promotion__error {
  color: #c92a2a;
}
.promotion__export,
.promotion__import {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  max-width: 40rem;
}
.promotion__button {
  align-self: flex-start;
}
.promotion__table {
  border-collapse: collapse;
  width: 100%;
}
.promotion__table th,
.promotion__table td {
  border-bottom: 1px solid #ddd;
  padding: 0.35rem 0.6rem;
  text-align: left;
  vertical-align: top;
}
.promotion__badge {
  border-radius: 0.75rem;
  padding: 0.1rem 0.5rem;
  font-size: 0.85em;
  background: #e9ecef;
}
.promotion__badge--create,
.promotion__badge--applied {
  background: #d3f9d8;
}
.promotion__badge--update {
  background: #fff3bf;
}
.promotion__badge--conflict,
.promotion__badge--blocked,
.promotion__badge--failed {
  background: #ffe3e3;
}
.promotion__secrets fieldset {
  margin: 0.5rem 0;
}
</style>
