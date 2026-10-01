<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
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
  label: string
  /** Source de valeurs du catalogue des briques (liste déroulante) ; sinon saisie libre. */
  source?: string
  multiline?: boolean
  optional?: boolean
}

const KINDS: Record<StepKind, { label: string; params: ParamSpec[] }> = {
  QUESTION: { label: 'Question à un utilisateur', params: [{ name: 'schemaKey', label: 'Formulaire', source: 'questionSchemas' }] },
  EXTERNAL: {
    label: 'Réponse d’un tiers (portail)',
    params: [
      { name: 'schemaKey', label: 'Formulaire', source: 'questionSchemas' },
      { name: 'portalRecipient', label: 'Destinataire (ex. dossierField:email)' },
      { name: 'portalLinkTtlDays', label: 'Validité du lien (jours)', optional: true },
    ],
  },
  DOCUMENT: {
    label: 'Générer un document',
    params: [
      { name: 'documentType', label: 'Modèle de document', source: 'documentTemplates' },
      { name: 'documentLocale', label: 'Langue', optional: true },
      { name: 'documentVariant', label: 'Variante', optional: true },
      { name: 'attach', label: 'Joindre au dossier (true/false)', optional: true },
    ],
  },
  NOTIFY: { label: 'Notifier', params: [{ name: 'notificationChannels', label: 'Canaux (ex. EMAIL,WEBHOOK)' }] },
  CONNECTOR: { label: 'Appeler un connecteur', params: [{ name: 'connectorInstanceId', label: 'Connecteur', source: 'connectorInstances' }] },
  DMN: { label: 'Décision (table DMN)', params: [{ name: 'decisionTableReferenceKey', label: 'Table', source: 'dmnTables' }] },
  EXPRESSION: {
    label: 'Calculer une valeur',
    params: [
      { name: 'formula', label: 'Formule', multiline: true },
      { name: 'formulaResult', label: 'Variable résultat' },
    ],
  },
  MAP_FIELDS: { label: 'Renseigner des champs du dossier', params: [{ name: 'fieldMapping', label: 'Correspondances', multiline: true }] },
  SET_WORKFLOW_STATUS: { label: 'Changer le statut du parcours', params: [{ name: 'targetWorkflowStatus', label: 'Statut' }] },
  SET_DOSSIER_STATUS: { label: 'Changer le statut du dossier', params: [{ name: 'targetDossierStatus', label: 'Statut du dossier' }] },
  WAIT: { label: 'Attendre', params: [{ name: 'duration', label: 'Durée ISO 8601 (PT24H, P2D)' }] },
  DECISION: { label: 'Aiguillage selon une valeur', params: [] },
}
const KIND_ORDER = Object.keys(KINDS) as StepKind[]
const OPERATORS: Record<Operator, string> = { EQ: '=', NE: '≠', GT: '>', GE: '≥', LT: '<', LE: '≤' }
const DOSSIER_STATUSES = ['DRAFT', 'OUVERT', 'EN_COURS', 'SUSPENDU', 'CLOTURE', 'ARCHIVE']

const items = ref<ParcoursSummary[]>([])
const editing = ref<boolean>(false)
const isNew = ref(true)
const key = ref('')
const label = ref('')
const version = ref<number | null>(null)
const definition = ref<ParcoursDefinition>(blank())
const options = ref<Record<string, ValueOption[] | null>>({})
const result = ref<CompileResult | null>(null)
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

const stepIds = computed(() => definition.value.steps.map((s) => s.id).filter(Boolean))

async function refresh() {
  try {
    items.value = await listParcours()
  } catch (e) {
    error.value = toProblemDetail(e).detail ?? 'Chargement impossible'
  }
}

async function loadOptions() {
  for (const source of new Set(KIND_ORDER.flatMap((k) => KINDS[k].params.map((p) => p.source)).filter(Boolean) as string[])) {
    options.value[source] = await resolveValueSource(source)
  }
}

function fresh() {
  isNew.value = true
  key.value = ''
  label.value = ''
  version.value = null
  definition.value = blank()
  result.value = null
  error.value = null
  notice.value = null
  editing.value = true
  void loadOptions()
}

async function open(k: string) {
  error.value = null
  notice.value = null
  result.value = null
  try {
    const p = await getParcours(k)
    isNew.value = false
    key.value = p.key
    label.value = p.label
    version.value = p.version
    definition.value = p.definition
    editing.value = true
    void loadOptions()
  } catch (e) {
    error.value = toProblemDetail(e).detail ?? 'Chargement impossible'
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
    error.value = toProblemDetail(e).detail ?? 'Opération impossible'
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
    notice.value = `Enregistré (version ${saved.version}).`
    await refresh()
  }
}

async function preview() {
  const r = await run(() => previewParcours(key.value.trim(), label.value.trim(), cleaned()))
  if (r) result.value = r
}

async function compile(dryRun: boolean) {
  if (isNew.value || version.value === null) {
    error.value = 'Enregistrez le parcours avant de le compiler.'
    return
  }
  const r = await run(() => compileParcours(key.value, dryRun))
  if (r) {
    result.value = r
    notice.value = dryRun ? 'Le parcours est valide : rien n’a été écrit.' : 'Brouillon BPMN déposé.'
    if (!dryRun) await refresh()
  }
}

async function remove() {
  if (!window.confirm(`Supprimer le parcours « ${key.value} » ? Les brouillons et processus déjà générés ne sont pas touchés.`)) return
  const done = await run(async () => {
    await deleteParcours(key.value)
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
    <h1>Parcours</h1>
    <p class="parcours__hint">
      Décrivez un parcours étape par étape ; il est compilé en processus BPMN et déposé en <strong>brouillon</strong>
      (<RouterLink to="/admin/drafts">Admin → Brouillons</RouterLink>) pour relecture avant publication. Le sens est unique : on
      modifie le parcours ici, pas le BPMN généré.
    </p>
    <p v-if="error" class="error" role="alert" data-testid="parcours-error">{{ error }}</p>
    <p v-if="notice" class="parcours__notice" role="status" data-testid="parcours-notice">{{ notice }}</p>

    <div v-if="!editing">
      <button type="button" data-testid="parcours-new" @click="fresh">Nouveau parcours</button>
      <p v-if="items.length === 0" data-testid="parcours-empty">Aucun parcours.</p>
      <table v-else class="parcours__list">
        <thead><tr><th>Clé</th><th>Libellé</th><th>Étapes</th><th>Version</th><th>Dernière compilation</th><th /></tr></thead>
        <tbody>
          <tr v-for="p in items" :key="p.key" data-testid="parcours-row">
            <td>{{ p.key }}</td><td>{{ p.label }}</td><td>{{ p.steps }}</td><td>{{ p.version }}</td>
            <td>{{ p.lastCompiledAt ?? '—' }}</td>
            <td><button type="button" :data-testid="`parcours-open-${p.key}`" @click="open(p.key)">Ouvrir</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <form v-else class="parcours__editor" data-testid="parcours-editor" @submit.prevent="save">
      <div class="parcours__head">
        <label>Clé du processus
          <input v-model="key" :disabled="!isNew" required pattern="[A-Za-z][A-Za-z0-9_-]*" data-testid="parcours-key" />
        </label>
        <label>Libellé <input v-model="label" required data-testid="parcours-label" /></label>
      </div>

      <fieldset>
        <legend>Statuts du parcours</legend>
        <div v-for="(s, i) in definition.statuses" :key="i" class="parcours__row">
          <input v-model="s.code" aria-label="Code du statut" />
          <input v-model="s.label" aria-label="Libellé du statut" />
          <label><input v-model="s.initial" type="radio" name="initial" :value="true" :checked="s.initial"
            @change="definition.statuses.forEach((o, j) => (o.initial = j === i))" /> initial</label>
          <select v-model="s.outcome" aria-label="Issue">
            <option value="NONE">en cours</option><option value="COMPLETED">terminé</option><option value="CANCELLED">annulé</option>
          </select>
          <button type="button" aria-label="Retirer le statut" @click="definition.statuses.splice(i, 1)">✕</button>
        </div>
        <button type="button" @click="definition.statuses.push({ code: '', label: '', initial: false, outcome: 'NONE' })">Ajouter un statut</button>
      </fieldset>

      <fieldset>
        <legend>Dossier</legend>
        <label v-for="d in DOSSIER_STATUSES" :key="d">
          <input v-model="definition.allowedDossierStatuses" type="checkbox" :value="d" /> {{ d }}
        </label>
        <label>À l’ouverture
          <select v-model="definition.onStartDossierStatus">
            <option :value="null">inchangé</option>
            <option v-for="d in DOSSIER_STATUSES" :key="d" :value="d">{{ d }}</option>
          </select>
        </label>
      </fieldset>

      <h2>Étapes</h2>
      <p class="parcours__hint">Sans « suite » explicite, une étape passe à la suivante de la liste ; la dernière termine le parcours.</p>
      <article v-for="(s, i) in definition.steps" :key="i" class="parcours__step" data-testid="parcours-step">
        <header class="parcours__row">
          <strong>{{ i + 1 }}.</strong>
          <input v-model="s.id" aria-label="Identifiant de l’étape" required pattern="[a-z][A-Za-z0-9]{0,39}" data-testid="step-id" />
          <input v-model="s.label" aria-label="Libellé de l’étape" placeholder="Libellé (optionnel)" />
          <select :value="s.kind" aria-label="Type d’étape" data-testid="step-kind" @change="setKind(s, ($event.target as HTMLSelectElement).value as StepKind)">
            <option v-for="k in KIND_ORDER" :key="k" :value="k">{{ KINDS[k].label }}</option>
          </select>
          <button type="button" aria-label="Monter" @click="move(i, -1)">↑</button>
          <button type="button" aria-label="Descendre" @click="move(i, 1)">↓</button>
          <button type="button" aria-label="Supprimer l’étape" data-testid="step-remove" @click="removeStep(i)">✕</button>
        </header>

        <template v-if="s.kind !== 'DECISION'">
          <label v-for="spec in KINDS[s.kind].params" :key="spec.name" class="parcours__param">{{ spec.label }}
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
          <label class="parcours__param">Suite (si ce n’est pas l’étape suivante)
            <select v-model="s.next"><option :value="null">étape suivante</option><option v-for="id in stepIds" :key="id" :value="id">{{ id }}</option></select>
          </label>
        </template>

        <template v-else>
          <div v-for="(b, j) in s.branches" :key="j" class="parcours__row" data-testid="branch">
            Si <input v-model="b.variable" aria-label="Variable" placeholder="variable" />
            <select v-model="b.operator" aria-label="Opérateur"><option v-for="(sym, op) in OPERATORS" :key="op" :value="op">{{ sym }}</option></select>
            <input v-model="b.value" aria-label="Valeur" placeholder="valeur" />
            alors
            <select v-model="b.next" aria-label="Étape cible"><option value="" /><option v-for="id in stepIds" :key="id" :value="id">{{ id }}</option></select>
            <button type="button" aria-label="Retirer la condition" @click="s.branches!.splice(j, 1)">✕</button>
          </div>
          <button type="button" @click="addBranch(s)">Ajouter une condition</button>
          <label class="parcours__param">Sinon
            <select v-model="s.defaultNext" data-testid="step-default"><option :value="null" /><option v-for="id in stepIds" :key="id" :value="id">{{ id }}</option></select>
          </label>
        </template>
      </article>
      <button type="button" data-testid="step-add" @click="addStep">Ajouter une étape</button>

      <div class="parcours__actions">
        <button type="submit" :disabled="busy" data-testid="parcours-save">Enregistrer</button>
        <button type="button" :disabled="busy" data-testid="parcours-preview" @click="preview">Aperçu du BPMN</button>
        <button type="button" :disabled="busy || isNew" data-testid="parcours-validate" @click="compile(true)">Valider (à blanc)</button>
        <button type="button" :disabled="busy || isNew" data-testid="parcours-compile" @click="compile(false)">Compiler en brouillon</button>
        <button type="button" :disabled="busy || isNew" data-testid="parcours-delete" @click="remove">Supprimer</button>
        <button type="button" @click="editing = false">Fermer</button>
      </div>

      <section v-if="result" class="parcours__result" data-testid="parcours-result">
        <p v-if="result.draftWritten" data-testid="parcours-draft-link">
          Brouillon déposé<span v-if="result.replacedExistingDraft"> (il remplace un brouillon existant)</span> :
          <RouterLink to="/admin/drafts">relire dans Admin → Brouillons</RouterLink>
        </p>
        <ul v-if="result.warnings.length" data-testid="parcours-warnings">
          <li v-for="w in result.warnings" :key="w">{{ w }}</li>
        </ul>
        <details><summary>BPMN généré (empreinte {{ result.sha256.slice(0, 12) }})</summary><pre data-testid="parcours-xml">{{ result.xml }}</pre></details>
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
