<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { formatDateTime } from '@/i18n/format'
import { toProblemDetail } from '@/api/http'
import {
  decidePrecheckFinding,
  downloadPrecheckExport,
  listPrechecks,
  rerunPrecheck,
  type FindingDecision,
  type PrecheckFinding,
  type PrecheckRun,
} from '@/api/prechecks'

// TODO §106 (issue #211, lot C, D-PRECHECK-PANEL) — pré-vérification IA du dossier : constats sourcés, ceux du modèle marqués « IA »
// (transparence, AI Act), décision de l'agent (accepter, écarter), relance et export du journal. Masqué tant que la
// fonction est désactivée sur l'instance ou qu'aucune pré-vérification n'a tourné (la brique du workflow lance la première).
const props = withDefaults(
  defineProps<{
    dossierId: string
    /** EDIT sur le dossier et dossier non clos (calculé par la fiche). */
    canEdit?: boolean
    /** TENANT_ADMIN : export du journal complet. */
    canExport?: boolean
  }>(),
  { canEdit: false, canExport: false },
)
const { t, te } = useI18n()

/** Intervalle de rafraîchissement tant qu'une exécution est en file ou en cours. */
const POLL_MS = 5000
const MAX_POLLS = 60

const runs = ref<PrecheckRun[]>([])
const loaded = ref(false)
const selectedId = ref<string | null>(null)
const busy = ref(false)
const error = ref('')
let pollTimer: ReturnType<typeof setTimeout> | null = null
let polls = 0

const selected = computed(() => runs.value.find((run) => run.id === selectedId.value) ?? runs.value[0] ?? null)
const latest = computed(() => runs.value[0] ?? null)
const active = computed(() => latest.value?.status === 'PENDING' || latest.value?.status === 'RUNNING')
const finished = computed(() => selected.value?.status === 'SUCCEEDED' || selected.value?.status === 'FAILED')
const counts = computed(() => {
  const findings = selected.value?.findings ?? []
  return {
    missing: findings.filter((f) => f.kind === 'MISSING').length,
    inconsistent: findings.filter((f) => f.kind === 'INCONSISTENT').length,
    toCheck: findings.filter((f) => f.kind === 'TO_CHECK').length,
  }
})

/** Libellé d'un code serveur : traduit s'il est connu, sinon le code tel quel (un nouveau code ne casse pas l'écran). */
function label(group: 'kind' | 'status' | 'trigger' | 'sourceType' | 'decision', code: string): string {
  const key = `dossier.prechecks.${group}.${code}`
  return te(key) ? t(key) : code
}

/** Motifs génériques du serveur (jamais le message du fournisseur) ; inconnu = texte reçu. */
const FAILURES: Record<string, string> = {
  'daily token budget reached': 'budget',
  'AI pre-check is disabled': 'disabled',
  'the language model provider is unavailable': 'unavailable',
  'the language model answer is not valid JSON': 'invalidAnswer',
  'the dossier type no longer exists': 'typeGone',
  'the dossier no longer exists': 'dossierGone',
}

function failureText(run: PrecheckRun): string {
  const code = run.failure ? FAILURES[run.failure] : undefined
  if (code) return t(`dossier.prechecks.failure.${code}`)
  if (run.failure?.startsWith('abandoned after')) return t('dossier.prechecks.failure.abandoned')
  return run.failure ?? t('dossier.prechecks.failure.unknown')
}

function runOption(run: PrecheckRun): string {
  return t('dossier.prechecks.runOption', {
    date: formatDateTime(run.createdAt),
    status: label('status', run.status),
    trigger: label('trigger', run.trigger),
  })
}

function source(finding: PrecheckFinding): string {
  return finding.sourceRef === '*'
    ? label('sourceType', finding.sourceType)
    : `${label('sourceType', finding.sourceType)} « ${finding.sourceRef} »`
}

function confidence(finding: PrecheckFinding): string {
  return finding.confidence == null ? '' : t('dossier.prechecks.confidence', { value: Math.round(finding.confidence * 100) })
}

function decisionText(finding: PrecheckFinding): string {
  if (!finding.decision) return ''
  return t('dossier.prechecks.decidedBy', {
    decision: label('decision', finding.decision),
    by: finding.decidedBy ?? '—',
    at: formatDateTime(finding.decidedAt),
  })
}

function stopPolling() {
  if (pollTimer) clearTimeout(pollTimer)
  pollTimer = null
}

function schedulePoll() {
  stopPolling()
  if (!active.value || polls >= MAX_POLLS) return
  polls++
  pollTimer = setTimeout(() => void load(), POLL_MS)
}

async function load() {
  try {
    const result = await listPrechecks(props.dossierId)
    runs.value = Array.isArray(result) ? result : [] // null = fonction désactivée
    if (selectedId.value && !runs.value.some((run) => run.id === selectedId.value)) selectedId.value = null
  } catch {
    runs.value = []
  } finally {
    loaded.value = true
  }
  schedulePoll()
}

async function act(action: () => Promise<unknown>, fallback: string) {
  busy.value = true
  error.value = ''
  try {
    await action()
    await load()
  } catch (e) {
    error.value = toProblemDetail(e).detail ?? t(fallback)
  } finally {
    busy.value = false
  }
}

function onRerun() {
  polls = 0
  selectedId.value = null
  return act(() => rerunPrecheck(props.dossierId), 'dossier.prechecks.errors.rerun')
}

function onDecide(finding: PrecheckFinding, decision: FindingDecision | null) {
  const run = selected.value
  if (!run) return
  return act(() => decidePrecheckFinding(props.dossierId, run.id, finding.id, decision), 'dossier.prechecks.errors.decide')
}

function onExport() {
  const run = selected.value
  if (!run) return
  return act(() => downloadPrecheckExport(props.dossierId, run.id), 'dossier.prechecks.errors.export')
}

onMounted(load)
onBeforeUnmount(stopPolling)
watch(
  () => props.dossierId,
  () => {
    selectedId.value = null
    polls = 0
    void load()
  },
)
</script>

<template>
  <section
    v-if="loaded && runs.length && selected"
    class="prechecks"
    data-testid="precheck-panel"
    :aria-label="t('dossier.prechecks.title')"
  >
    <header class="prechecks__header">
      <h2>{{ t('dossier.prechecks.title') }}</h2>
      <div class="prechecks__actions">
        <label v-if="runs.length > 1">
          <span class="visually-hidden">{{ t('dossier.prechecks.chooseRun') }}</span>
          <select v-model="selectedId" data-testid="precheck-run-select">
            <option :value="null">{{ runOption(runs[0]!) }}</option>
            <option v-for="run in runs.slice(1)" :key="run.id" :value="run.id">{{ runOption(run) }}</option>
          </select>
        </label>
        <button v-if="canEdit" type="button" :disabled="busy || active" @click="onRerun">
          {{ t('dossier.prechecks.rerun') }}
        </button>
        <button v-if="canExport && finished" type="button" :disabled="busy" @click="onExport">
          {{ t('dossier.prechecks.export') }}
        </button>
      </div>
    </header>

    <p class="prechecks__notice">{{ t('dossier.prechecks.notice') }}</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>

    <p class="prechecks__status" :data-status="selected.status" data-testid="precheck-status">
      {{ label('status', selected.status) }} · {{ formatDateTime(selected.finishedAt ?? selected.createdAt) }} ·
      {{ label('trigger', selected.trigger) }}
      <template v-if="finished">
        · {{ t('dossier.prechecks.counts', counts) }}
      </template>
    </p>
    <p v-if="selected.status === 'FAILED'" class="prechecks__failure" role="status">
      {{ t('dossier.prechecks.failed', { reason: failureText(selected) }) }}
    </p>

    <div v-if="selected.summary" class="prechecks__summary" data-testid="precheck-summary">
      <span class="prechecks__ai">{{ t('dossier.prechecks.ai') }}</span>
      <strong>{{ t('dossier.prechecks.summary') }}</strong>
      <p>{{ selected.summary }}</p>
    </div>

    <p v-if="finished && !selected.findings.length">{{ t('dossier.prechecks.noFinding') }}</p>
    <ul v-else class="prechecks__findings">
      <li
        v-for="finding in selected.findings"
        :key="finding.id"
        :data-kind="finding.kind"
        :data-origin="finding.origin"
        :data-decision="finding.decision ?? ''"
        data-testid="precheck-finding"
      >
        <div class="prechecks__line">
          <span class="prechecks__kind">{{ label('kind', finding.kind) }}</span>
          <span v-if="finding.origin === 'MODEL'" class="prechecks__ai" :title="t('dossier.prechecks.aiHint')">
            {{ t('dossier.prechecks.ai') }}
          </span>
          <span v-else class="prechecks__rule">{{ t('dossier.prechecks.deterministic') }}</span>
          <span class="prechecks__source">{{ source(finding) }}</span>
          <span v-if="finding.confidence != null" class="prechecks__confidence">{{ confidence(finding) }}</span>
        </div>
        <p class="prechecks__message">{{ finding.message }}</p>
        <div class="prechecks__decision">
          <span v-if="finding.decision" data-testid="precheck-decision">{{ decisionText(finding) }}</span>
          <template v-if="canEdit && finished">
            <button
              v-if="finding.decision !== 'ACCEPTED'"
              type="button"
              :disabled="busy"
              :aria-label="t('dossier.prechecks.acceptAria', { message: finding.message })"
              @click="onDecide(finding, 'ACCEPTED')"
            >
              {{ t('dossier.prechecks.accept') }}
            </button>
            <button
              v-if="finding.decision !== 'DISMISSED'"
              type="button"
              :disabled="busy"
              :aria-label="t('dossier.prechecks.dismissAria', { message: finding.message })"
              @click="onDecide(finding, 'DISMISSED')"
            >
              {{ t('dossier.prechecks.dismiss') }}
            </button>
            <button
              v-if="finding.decision"
              type="button"
              :disabled="busy"
              :aria-label="t('dossier.prechecks.undoAria', { message: finding.message })"
              @click="onDecide(finding, null)"
            >
              {{ t('dossier.prechecks.undo') }}
            </button>
          </template>
        </div>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.prechecks__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}
.prechecks__actions {
  display: flex;
  gap: 0.35rem;
  align-items: center;
}
.prechecks__notice,
.prechecks__status {
  font-size: 0.9rem;
  color: var(--text-muted, #5b6773);
}
.prechecks__failure {
  color: #8a1c1c;
}
.prechecks__summary {
  border-left: 3px solid #6b4fbb;
  padding: 0.35rem 0.6rem;
  margin: 0.5rem 0;
}
.prechecks__summary p {
  margin: 0.25rem 0 0;
  white-space: pre-line;
}
.prechecks__findings {
  list-style: none;
  padding: 0;
  margin: 0;
}
.prechecks__findings li {
  border-bottom: 1px solid #ddd;
  padding: 0.45rem 0.2rem;
}
.prechecks__line {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  align-items: baseline;
  font-size: 0.85rem;
}
.prechecks__kind {
  font-weight: 600;
}
.prechecks__ai {
  background: #ede7fb;
  color: #4b2e9a;
  border-radius: 0.25rem;
  padding: 0 0.3rem;
  font-size: 0.75rem;
  font-weight: 600;
}
.prechecks__rule {
  background: #e7eef5;
  color: #1f4466;
  border-radius: 0.25rem;
  padding: 0 0.3rem;
  font-size: 0.75rem;
}
.prechecks__source,
.prechecks__confidence {
  color: var(--text-muted, #5b6773);
}
.prechecks__message {
  margin: 0.2rem 0;
}
.prechecks__decision {
  display: flex;
  gap: 0.35rem;
  align-items: center;
  font-size: 0.85rem;
}
.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
</style>
