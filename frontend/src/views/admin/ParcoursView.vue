<script setup lang="ts">
import { labelOr } from '@/i18n'
import { useI18n } from 'vue-i18n'
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { toProblemDetail } from '@/api/http'
import {
  compileParcours,
  deleteParcours,
  getParcours,
  listParcours,
  previewParcours,
  saveParcours,
  type CompileResult,
  type Operator,
  type ParcoursDefinition,
  type ParcoursStep,
  type ParcoursSummary,
  type StepKind,
} from '@/api/parcours'
import { resolveValueSource } from '@/components/workflow/bpmn-blocks/valueSourceResolver'
import type { ValueOption } from '@/components/workflow/bpmn-blocks/types'

/**
 * TODO §88 (LC5) — concepteur de parcours : on enchaîne des étapes lisibles (question, document, notification, décision…) ;
 * le serveur les compile en BPMN (sens unique, liste blanche inchangée) et dépose un BROUILLON à relire dans Admin → Brouillons.
 * Un parcours se modifie ici, pas dans le BPMN généré.
 */
interface ParamSpec {
  name: string
  /** Source de valeurs du catalogue des briques (liste déroulante) ; sinon saisie libre. */
  source?: string
  multiline?: boolean
  optional?: boolean
}

const KINDS: Record<StepKind, { params: ParamSpec[] }> = {
  QUESTION: { params: [{ name: 'schemaKey', source: 'questionSchemas' }] },
  EXTERNAL: {
    params: [
      { name: 'schemaKey', source: 'questionSchemas' },
      { name: 'portalRecipient' },
      { name: 'portalLinkTtlDays', optional: true },
    ],
  },
  DOCUMENT: {
    params: [
      { name: 'documentType', source: 'documentTemplates' },
      { name: 'documentLocale', optional: true },
      { name: 'documentVariant', optional: true },
      { name: 'attach', optional: true },
    ],
  },
  NOTIFY: { params: [{ name: 'notificationChannels' }] },
  CONNECTOR: { params: [{ name: 'connectorInstanceId', source: 'connectorInstances' }] },
  DMN: { params: [{ name: 'decisionTableReferenceKey', source: 'dmnTables' }] },
  EXPRESSION: {
    params: [
      { name: 'formula', multiline: true },
      { name: 'formulaResult' },
    ],
  },
  MAP_FIELDS: { params: [{ name: 'fieldMapping', multiline: true }] },
  SET_WORKFLOW_STATUS: { params: [{ name: 'targetWorkflowStatus' }] },
  SET_DOSSIER_STATUS: { params: [{ name: 'targetDossierStatus' }] },
  WAIT: { params: [{ name: 'duration' }] },
  DECISION: { params: [] },
}
const KIND_ORDER = Object.keys(KINDS) as StepKind[]
const OPERATORS: Record<Operator, string> = { EQ: '=', NE: '≠', GT: '>', GE: '≥', LT: '<', LE: '≤' }
const DOSSIER_STATUSES = ['DRAFT', 'OUVERT', 'EN_COURS', 'SUSPENDU', 'CLOTURE', 'ARCHIVE']

const { t } = useI18n()
const items = ref<ParcoursSummary[]>([])
const editing = ref<boolean>(false)
const isNew = ref(true)
const key = ref('')
const label = ref('')
const version = ref<number | null>(null)
const definition = ref<ParcoursDefinition>(blank())
const options = ref<Record<string, ValueOption[] | null>>({})
const result = ref<CompileResult | null>(null)
const replaceDraft = ref(false)
const savedSnapshot = ref('')
const busy = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)

function blank(): ParcoursDefinition {
  return {
    statuses: [
      { code: 'OUVERT', label: 'Ouvert', initial: true, outcome: 'NONE' },
      { code: 'TERMINE', label: 'Terminé', initial: false, outcome: 'COMPLETED' },
    ],
    allowedDossierStatuses: ['OUVERT', 'EN_COURS'],
    onStartDossierStatus: null,
    steps: [],
  }
}

/** Les boutons de compilation travaillent sur la version enregistrée : on les bloque tant que le formulaire a des modifications. */
const dirty = computed(() => JSON.stringify([label.value, definition.value]) !== savedSnapshot.value)
const stepIds = computed(() => definition.value.steps.map((s) => s.id).filter(Boolean))

async function refresh() {
  try {
    items.value = await listParcours()
  } catch (e) {
    error.value = toProblemDetail(e).detail ?? t('admin.parcours.errors.load')
  }
}

async function loadOptions() {
  for (const source of new Set(KIND_ORDER.flatMap((k) => KINDS[k].params.map((p) => p.source)).filter(Boolean) as string[])) {
    options.value[source] = await resolveValueSource(source)
  }
}

// Un aperçu ou un résultat de compilation décrit la version qui a été compilée : il n'est plus d'actualité dès que l'on modifie.
watch(dirty, (isDirty) => {
  if (isDirty) result.value = null
})

function snapshot() {
  savedSnapshot.value = JSON.stringify([label.value, definition.value])
}

function fresh() {
  isNew.value = true
  key.value = ''
  label.value = ''
  version.value = null
  definition.value = blank()
  result.value = null
  replaceDraft.value = false
  error.value = null
  notice.value = null
  editing.value = true
  snapshot()
  void loadOptions()
}

async function open(k: string) {
  error.value = null
  notice.value = null
  result.value = null
  replaceDraft.value = false
  try {
    const p = await getParcours(k)
    isNew.value = false
    key.value = p.key
    label.value = p.label
    version.value = p.version
    definition.value = p.definition
    editing.value = true
    snapshot()
    void loadOptions()
  } catch (e) {
    error.value = toProblemDetail(e).detail ?? t('admin.parcours.errors.load')
  }
}

function addStep() {
  const base = 'etape'
  let n = definition.value.steps.length + 1
  while (stepIds.value.includes(`${base}${n}`)) n++
  definition.value.steps.push({ id: `${base}${n}`, label: '', kind: 'QUESTION', params: {}, next: null, branches: [], defaultNext: null })
}

function removeStep(i: number) {
  definition.value.steps.splice(i, 1)
}

function move(i: number, delta: number) {
  const steps = definition.value.steps
  const j = i + delta
  if (j < 0 || j >= steps.length) return
  ;[steps[i], steps[j]] = [steps[j], steps[i]]
}

function setKind(step: ParcoursStep, kind: StepKind) {
  step.kind = kind
  step.params = {}
  step.branches = []
  step.next = null
  step.defaultNext = null
  if (kind === 'DECISION') step.branches = [{ variable: '', operator: 'EQ', value: '', next: '' }]
}

function addBranch(step: ParcoursStep) {
  step.branches = [...(step.branches ?? []), { variable: '', operator: 'EQ', value: '', next: '' }]
}

function cleaned(): ParcoursDefinition {
  return {
    ...definition.value,
    steps: definition.value.steps.map((s) => ({
      ...s,
      label: s.label?.trim() || null,
      params: Object.fromEntries(Object.entries(s.params).filter(([, v]) => v !== undefined && String(v).trim() !== '')),
      next: s.next || null,
      defaultNext: s.defaultNext || null,
      branches: s.kind === 'DECISION' ? s.branches : [],
    })),
  }
}

async function run<T>(action: () => Promise<T>): Promise<T | undefined> {
  busy.value = true
  error.value = null
  notice.value = null
  try {
    return await action()
  } catch (e) {
    error.value = toProblemDetail(e).detail ?? t('admin.parcours.errors.operation')
    return undefined
  } finally {
    busy.value = false
  }
}

async function save() {
  const saved = await run(() => saveParcours(key.value.trim(), label.value.trim(), cleaned(), isNew.value ? null : version.value))
  if (saved) {
    isNew.value = false
    version.value = saved.version
    definition.value = saved.definition
    snapshot()
    notice.value = t('admin.parcours.saved', { version: saved.version })
    await refresh()
  }
}

async function preview() {
  const r = await run(() => previewParcours(key.value.trim(), label.value.trim(), cleaned()))
  if (r) result.value = r
}

async function compile(dryRun: boolean) {
  if (isNew.value || version.value === null) {
    error.value = t('admin.parcours.errors.saveFirst')
    return
  }
  if (dirty.value) {
    error.value = t('admin.parcours.errors.saveChanges')
    return
  }
  const r = await run(() => compileParcours(key.value, dryRun, replaceDraft.value))
  if (r) {
    result.value = r
    notice.value = dryRun ? t('admin.parcours.validNothingWritten') : t('admin.parcours.draftDeposited')
    if (!dryRun) await refresh()
  }
}

async function remove() {
  if (!window.confirm(t('admin.parcours.confirmDelete', { key: key.value }))) return
  const done = await run(async () => {
    await deleteParcours(key.value, version.value)
    return true
  })
  if (done) {
    editing.value = false
    await refresh()
  }
}

function optionsFor(spec: ParamSpec): ValueOption[] | null {
  return spec.source ? (options.value[spec.source] ?? null) : null
}

onMounted(refresh)
</script>

<template>
  <section class="parcours" data-testid="parcours-view">
    <h1>{{ t('admin.parcours.title') }}</h1>
    <i18n-t keypath="admin.parcours.hint" tag="p" class="parcours__hint">
      <template #draft><strong>{{ t('admin.parcours.hintDraft') }}</strong></template>
      <template #link><RouterLink to="/admin/drafts">{{ t('admin.parcours.hintLink') }}</RouterLink></template>
    </i18n-t>
    <p v-if="error" class="error" role="alert" data-testid="parcours-error">{{ error }}</p>
    <p v-if="notice" class="parcours__notice" role="status" data-testid="parcours-notice">{{ notice }}</p>

    <div v-if="!editing">
      <button type="button" data-testid="parcours-new" @click="fresh">{{ t('admin.parcours.new') }}</button>
      <p v-if="items.length === 0" data-testid="parcours-empty">{{ t('admin.parcours.empty') }}</p>
      <table v-else class="parcours__list">
        <thead><tr><th>{{ t('admin.parcours.key') }}</th><th>{{ t('admin.parcours.label') }}</th><th>{{ t('admin.parcours.stepsCount') }}</th><th>{{ t('admin.parcours.version') }}</th><th>{{ t('admin.parcours.lastCompiled') }}</th><th /></tr></thead>
        <tbody>
          <tr v-for="p in items" :key="p.key" data-testid="parcours-row">
            <td>{{ p.key }}</td><td>{{ p.label }}</td><td>{{ p.steps }}</td><td>{{ p.version }}</td>
            <td>{{ p.lastCompiledAt ?? '—' }}</td>
            <td><button type="button" :data-testid="`parcours-open-${p.key}`" @click="open(p.key)">{{ t('admin.parcours.open') }}</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <form v-else class="parcours__editor" data-testid="parcours-editor" @submit.prevent="save">
      <div class="parcours__head">
        <label>{{ t('admin.parcours.processKey') }}
          <input v-model="key" :disabled="!isNew" required pattern="[A-Za-z][A-Za-z0-9_-]*" data-testid="parcours-key" />
        </label>
        <label>{{ t('admin.parcours.label') }} <input v-model="label" required data-testid="parcours-label" /></label>
      </div>

      <fieldset>
        <legend>{{ t('admin.parcours.statuses') }}</legend>
        <div v-for="(s, i) in definition.statuses" :key="i" class="parcours__row">
          <input v-model="s.code" :aria-label="t('admin.parcours.statusCode')" />
          <input v-model="s.label" :aria-label="t('admin.parcours.statusLabel')" />
          <label><input v-model="s.initial" type="radio" name="initial" :value="true" :checked="s.initial"
            @change="definition.statuses.forEach((o, j) => (o.initial = j === i))" /> {{ t('admin.parcours.initial') }}</label>
          <select v-model="s.outcome" :aria-label="t('admin.parcours.outcome')">
            <option value="NONE">{{ t('admin.parcours.outcomes.NONE') }}</option><option value="COMPLETED">{{ t('admin.parcours.outcomes.COMPLETED') }}</option><option value="CANCELLED">{{ t('admin.parcours.outcomes.CANCELLED') }}</option>
          </select>
          <button type="button" :aria-label="t('admin.parcours.removeStatus')" @click="definition.statuses.splice(i, 1)">✕</button>
        </div>
        <button type="button" @click="definition.statuses.push({ code: '', label: '', initial: false, outcome: 'NONE' })">{{ t('admin.parcours.addStatus') }}</button>
      </fieldset>

      <fieldset>
        <legend>{{ t('admin.parcours.dossier') }}</legend>
        <label v-for="d in DOSSIER_STATUSES" :key="d">
          <input v-model="definition.allowedDossierStatuses" type="checkbox" :value="d" /> {{ d }}
        </label>
        <label>{{ t('admin.parcours.onStart') }}
          <select v-model="definition.onStartDossierStatus">
            <option :value="null">{{ t('admin.parcours.unchanged') }}</option>
            <option v-for="d in DOSSIER_STATUSES" :key="d" :value="d">{{ d }}</option>
          </select>
        </label>
      </fieldset>

      <h2>{{ t('admin.parcours.steps') }}</h2>
      <p class="parcours__hint">{{ t('admin.parcours.stepsHint') }}</p>
      <article v-for="(s, i) in definition.steps" :key="i" class="parcours__step" data-testid="parcours-step">
        <header class="parcours__row">
          <strong>{{ i + 1 }}.</strong>
          <input v-model="s.id" :aria-label="t('admin.parcours.stepId')" required pattern="[a-z][A-Za-z0-9]{0,39}" data-testid="step-id" />
          <input v-model="s.label" :aria-label="t('admin.parcours.stepLabel')" :placeholder="t('admin.parcours.stepLabelPlaceholder')" />
          <select :value="s.kind" :aria-label="t('admin.parcours.stepKind')" data-testid="step-kind" @change="setKind(s, ($event.target as HTMLSelectElement).value as StepKind)">
            <option v-for="k in KIND_ORDER" :key="k" :value="k">{{ labelOr(`admin.parcours.kinds.${k}`, k) }}</option>
          </select>
          <button type="button" :aria-label="t('admin.parcours.up')" @click="move(i, -1)">↑</button>
          <button type="button" :aria-label="t('admin.parcours.down')" @click="move(i, 1)">↓</button>
          <button type="button" :aria-label="t('admin.parcours.removeStep')" data-testid="step-remove" @click="removeStep(i)">✕</button>
        </header>

        <template v-if="s.kind !== 'DECISION'">
          <label v-for="spec in KINDS[s.kind].params" :key="spec.name" class="parcours__param">{{ labelOr(`admin.parcours.params.${spec.name}`, spec.name) }}
            <select v-if="spec.name === 'targetWorkflowStatus'" v-model="s.params[spec.name]" :data-testid="`param-${spec.name}`">
              <option value="" />
              <option v-for="st in definition.statuses.filter((x) => x.code)" :key="st.code" :value="st.code">{{ st.label || st.code }}</option>
            </select>
            <select v-else-if="spec.name === 'targetDossierStatus'" v-model="s.params[spec.name]" :data-testid="`param-${spec.name}`">
              <option value="" /><option v-for="d in DOSSIER_STATUSES" :key="d" :value="d">{{ d }}</option>
            </select>
            <select v-else-if="optionsFor(spec)" v-model="s.params[spec.name]" :data-testid="`param-${spec.name}`">
              <option value="" /><option v-for="o in optionsFor(spec)!" :key="o.value" :value="o.value">{{ o.label }}</option>
            </select>
            <textarea v-else-if="spec.multiline" v-model="s.params[spec.name]" rows="2" :data-testid="`param-${spec.name}`" />
            <input v-else v-model="s.params[spec.name]" :data-testid="`param-${spec.name}`" />
          </label>
          <label class="parcours__param">{{ t('admin.parcours.next') }}
            <select v-model="s.next"><option :value="null">{{ t('admin.parcours.nextStep') }}</option><option v-for="id in stepIds" :key="id" :value="id">{{ id }}</option></select>
          </label>
        </template>

        <template v-else>
          <div v-for="(b, j) in s.branches" :key="j" class="parcours__row" data-testid="branch">
            {{ t('admin.parcours.if') }} <input v-model="b.variable" :aria-label="t('admin.parcours.variableAria')" :placeholder="t('admin.parcours.variable')" />
            <select v-model="b.operator" :aria-label="t('admin.parcours.operator')"><option v-for="(sym, op) in OPERATORS" :key="op" :value="op">{{ sym }}</option></select>
            <input v-model="b.value" :aria-label="t('admin.parcours.valueAria')" :placeholder="t('admin.parcours.value')" />
            {{ t('admin.parcours.then') }}
            <select v-model="b.next" :aria-label="t('admin.parcours.target')"><option value="" /><option v-for="id in stepIds" :key="id" :value="id">{{ id }}</option></select>
            <button type="button" :aria-label="t('admin.parcours.removeCondition')" @click="s.branches!.splice(j, 1)">✕</button>
          </div>
          <button type="button" @click="addBranch(s)">{{ t('admin.parcours.addCondition') }}</button>
          <label class="parcours__param">{{ t('admin.parcours.otherwise') }}
            <select v-model="s.defaultNext" data-testid="step-default"><option :value="null" /><option v-for="id in stepIds" :key="id" :value="id">{{ id }}</option></select>
          </label>
        </template>
      </article>
      <button type="button" data-testid="step-add" @click="addStep">{{ t('admin.parcours.addStep') }}</button>

      <div class="parcours__actions">
        <button type="submit" :disabled="busy" data-testid="parcours-save">{{ t('admin.parcours.save') }}</button>
        <button type="button" :disabled="busy" data-testid="parcours-preview" @click="preview">{{ t('admin.parcours.preview') }}</button>
        <button type="button" :disabled="busy || isNew || dirty" data-testid="parcours-validate" @click="compile(true)">{{ t('admin.parcours.validate') }}</button>
        <button type="button" :disabled="busy || isNew || dirty" data-testid="parcours-compile" @click="compile(false)">{{ t('admin.parcours.compile') }}</button>
        <label class="parcours__replace"><input v-model="replaceDraft" type="checkbox" data-testid="parcours-replace" /> {{ t('admin.parcours.replace') }}</label>
        <button type="button" :disabled="busy || isNew" data-testid="parcours-delete" @click="remove">{{ t('admin.parcours.delete') }}</button>
        <button type="button" @click="editing = false">{{ t('admin.parcours.close') }}</button>
      </div>

      <section v-if="result" class="parcours__result" data-testid="parcours-result">
        <p v-if="result.draftWritten" data-testid="parcours-draft-link">
          {{ t('admin.parcours.draftWritten') }}<span v-if="result.replacedExistingDraft"> {{ t('admin.parcours.replacesDraft') }}</span> :
          <RouterLink to="/admin/drafts">{{ t('admin.parcours.reviewDrafts') }}</RouterLink>
        </p>
        <p v-if="result.problem" class="error" role="alert" data-testid="parcours-problem">
          {{ t('admin.parcours.problem', { problem: result.problem }) }}
        </p>
        <ul v-if="result.warnings.length" data-testid="parcours-warnings">
          <li v-for="w in result.warnings" :key="w">{{ w }}</li>
        </ul>
        <details><summary>{{ t('admin.parcours.bpmnSummary', { sha: result.sha256.slice(0, 12) }) }}</summary><pre data-testid="parcours-xml">{{ result.xml }}</pre></details>
      </section>
    </form>
  </section>
</template>

<style scoped>
.parcours__row { display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap; margin-bottom: 0.4rem; }
.parcours__step { border: 1px solid var(--color-border, #ccc); border-radius: 6px; padding: 0.6rem; margin-bottom: 0.6rem; }
.parcours__param { display: flex; flex-direction: column; gap: 0.2rem; margin: 0.3rem 0; }
.parcours__actions { display: flex; gap: 0.5rem; flex-wrap: wrap; margin: 1rem 0; }
.parcours__hint { color: var(--color-text-muted, #555); }
.parcours__result pre { overflow: auto; max-height: 24rem; }
</style>
