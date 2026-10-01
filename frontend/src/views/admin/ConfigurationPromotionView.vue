<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { toProblemDetail } from '@/api/http'
import {
  ARTIFACT_LABELS,
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
const journal = ref<ConfigImportRecord[]>([])
const busy = ref(false)
const error = ref<string | null>(null)
// Secrets saisis pour la création de connecteurs : gardés dans ce composant uniquement, effacés après l'application.
const secrets = ref<Record<string, Record<string, string>>>({})

const parameters = computed(() => parseParameters(parametersText.value))
const parametersInvalid = computed(() => parameters.value === null)

const PLAN_LABELS: Record<string, string> = {
  CREATE: 'Créer',
  UPDATE: 'Modifier',
  UNCHANGED: 'Inchangé',
  CONFLICT: 'Conflit',
  BLOCKED: 'Bloqué',
}
const OUTCOME_LABELS: Record<string, string> = {
  APPLIED: 'Appliqué',
  UNCHANGED: 'Inchangé',
  SKIPPED: 'Ignoré',
  FAILED: 'Échec',
}

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
    error.value = toProblemDetail(err).detail ?? 'Export impossible'
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
    error.value = toProblemDetail(err).detail ?? 'Plan impossible'
  } finally {
    busy.value = false
  }
}

async function runApply() {
  if (!file.value || !plan.value || parameters.value === null) return
  if (!window.confirm(`Appliquer ce bundle (${applicableCount.value} artefact(s) à créer ou modifier) ? Les processus BPMN et tables DMN arrivent en brouillons à publier.`)) return
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
    error.value = toProblemDetail(err).detail ?? 'Application impossible'
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
    <h1>Promotion de configuration</h1>
    <p class="promotion__intro">
      Exportez la configuration d’un environnement (recette, par exemple), puis importez-la sur un autre : un plan à
      blanc montre ce qui serait créé, modifié ou bloqué avant toute écriture. Aucun secret n’est exporté ; les
      processus BPMN et tables DMN arrivent en <strong>brouillons à publier</strong> ; un connecteur est créé
      <strong>désactivé</strong> (relisez ses adresses, puis activez-le).
    </p>

    <h2>Exporter</h2>
    <form class="promotion__export" @submit.prevent>
      <label>
        Nom de cet environnement
        <input v-model="sourceLabel" type="text" maxlength="200" placeholder="recette" data-testid="source-label" />
      </label>
      <button type="button" class="promotion__button" data-testid="export-button" @click="runExport">Télécharger le bundle (ZIP)</button>
    </form>

    <h2>Importer</h2>
    <p v-if="error" class="promotion__error" role="alert" data-testid="promotion-error">{{ error }}</p>
    <form class="promotion__import" @submit.prevent="runPlan">
      <label>
        Bundle (ZIP)
        <input type="file" accept=".zip,application/zip" data-testid="bundle-file" @change="onFile" />
      </label>
      <label>
        Paramètres d’environnement (JSON, pour les valeurs <code>&#123;&#123;nom&#125;&#125;</code> des connecteurs)
        <textarea v-model="parametersText" rows="2" placeholder='{"url_erp": "https://erp.exemple.fr"}' data-testid="parameters" />
      </label>
      <p v-if="parametersInvalid" class="promotion__error" role="alert">Les paramètres doivent être un objet JSON {nom: valeur}.</p>
      <button type="submit" :disabled="!file || busy || parametersInvalid" data-testid="plan-button">Planifier (à blanc)</button>
    </form>

    <template v-if="plan">
      <h3>Plan — rien n’est encore écrit</h3>
      <table class="promotion__table" data-testid="plan-table">
        <thead>
          <tr><th>Artefact</th><th>Clé</th><th>Action</th><th>Détail</th></tr>
        </thead>
        <tbody>
          <tr v-for="e in plan.entries" :key="`${e.type}:${e.key}`" :data-testid="`plan-${e.type}-${e.key}`">
            <td>{{ ARTIFACT_LABELS[e.type] }}</td>
            <td>{{ e.key }}</td>
            <td><span :class="['promotion__badge', `promotion__badge--${e.action.toLowerCase()}`]">{{ PLAN_LABELS[e.action] }}</span></td>
            <td>{{ e.reason }}</td>
          </tr>
        </tbody>
      </table>

      <div v-if="connectorsNeedingSecrets.length" class="promotion__secrets" data-testid="secrets-block">
        <h4>Secrets des connecteurs à créer</h4>
        <p>Saisissez-les ici (envoyés avec l’application seulement, jamais conservés) ou plus tard dans l’écran Connecteurs.</p>
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
        Appliquer ({{ applicableCount }})
      </button>
    </template>

    <template v-if="report">
      <h3>Rapport</h3>
      <table class="promotion__table" data-testid="report-table">
        <thead>
          <tr><th>Artefact</th><th>Clé</th><th>Résultat</th><th>Détail</th></tr>
        </thead>
        <tbody>
          <tr v-for="e in report.entries" :key="`${e.type}:${e.key}`">
            <td>{{ ARTIFACT_LABELS[e.type] }}</td>
            <td>{{ e.key }}</td>
            <td><span :class="['promotion__badge', `promotion__badge--${e.outcome.toLowerCase()}`]">{{ OUTCOME_LABELS[e.outcome] }}</span></td>
            <td>{{ e.message }}</td>
          </tr>
        </tbody>
      </table>
      <p>
        Les brouillons BPMN/DMN se publient depuis <RouterLink to="/admin/drafts">Brouillons</RouterLink> ; rejouer l’import
        est sans danger (il converge).
      </p>
    </template>

    <h2>Derniers imports</h2>
    <p v-if="journal.length === 0">Aucun import enregistré.</p>
    <ul v-else class="promotion__log" data-testid="import-log">
      <li v-for="j in journal" :key="j.id">
        {{ new Date(j.performedAt).toLocaleString('fr-FR') }} — {{ j.performedBy }}
        <template v-if="j.sourceLabel">({{ j.sourceLabel }})</template> :
        {{ j.applied }} appliqué(s), {{ j.unchanged }} inchangé(s), {{ j.skipped }} ignoré(s), {{ j.failed }} échec(s)
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
