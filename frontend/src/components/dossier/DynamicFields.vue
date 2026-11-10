<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type { FieldDefinition, FieldError, FieldRulesPreview } from '@/api/types'
import SensitiveValue from './SensitiveValue.vue'
import DynamicFieldInput from './DynamicFieldInput.vue'
import { formatFieldValue, isStructured } from '@/dossier/fieldValues'
import { describeFieldError } from '@/dossier/fieldErrors'
import { evaluateFieldRules } from '@/api/dossierTypes'
import {
  hasFieldRules,
  isComputedField,
  isFieldApplicable,
  isFieldRequired,
  isReadOnlyOutputField,
  isWorkflowManagedField,
} from '@/dossier/fieldRules'

const { t } = useI18n()

const props = withDefaults(
  defineProps<{
    definitions: FieldDefinition[]
    modelValue: Record<string, unknown>
    readonly?: boolean
    applyDefaults?: boolean
    /**
     * TODO §80 (D1, règles) : code du type de dossier. Quand le type porte des règles (champ calculé, visibilité, obligation
     * conditionnelle) et que le formulaire est éditable, les règles sont évaluées côté serveur (même moteur que
     * l'enregistrement) pendant la saisie : champs calculés affichés en lecture seule, champs sans objet masqués (et leur
     * saisie retirée), obligations mises à jour.
     */
    typeCode?: string
    /** Erreurs de champ renvoyées par le serveur (`errors[]`), affichées sous le champ concerné. */
    serverErrors?: FieldError[]
  }>(),
  { applyDefaults: true, serverErrors: () => [] },
)

const emit = defineEmits<{
  'update:modelValue': [Record<string, unknown>]
  /** Erreurs serveur dont le champ n'est pas rendu (masqué par une règle, ou inconnu du schéma affiché). */
  'undisplayed-errors': [FieldError[]]
}>()

const rules = ref<FieldRulesPreview | null>(null)
let timer: ReturnType<typeof setTimeout> | undefined
let sequence = 0
const live = computed(() => !props.readonly && !!props.typeCode && hasFieldRules(props.definitions))

function scheduleEvaluation() {
  if (!live.value) return
  clearTimeout(timer)
  timer = setTimeout(async () => {
    const mine = ++sequence
    try {
      const preview = await evaluateFieldRules(props.typeCode!, props.modelValue)
      if (mine !== sequence) return // réponse périmée : une saisie plus récente est en cours d'évaluation
      rules.value = preview
      dropHiddenValues(preview)
    } catch {
      // Aperçu indisponible : le formulaire reste utilisable, le serveur valide à l'enregistrement.
    }
  }, 300)
}

/**
 * Un champ devenu sans objet ne doit pas être envoyé (le serveur refuse une valeur fournie pour un champ sans objet).
 * Les champs calculés ou {@code managedByWorkflow} restent dans le brouillon : les retirer ferait envoyer {@code null}
 * via le diff et le serveur refuserait la clé (TODO §126).
 */
function dropHiddenValues(preview: FieldRulesPreview) {
  const protectedNames = new Set(
    props.definitions.filter((d) => isReadOnlyOutputField(d)).map((d) => d.name),
  )
  const stale = preview.hidden.filter(
    (name) => props.modelValue[name] !== undefined && !protectedNames.has(name),
  )
  if (stale.length === 0) return
  const next = { ...props.modelValue }
  for (const name of stale) delete next[name]
  emit('update:modelValue', next)
}

watch(() => [props.modelValue, props.typeCode, live.value], scheduleEvaluation, { deep: true, immediate: true })
onBeforeUnmount(() => clearTimeout(timer))

/** En saisie, un champ calculé affiche l'aperçu serveur ; en lecture, la valeur stockée. */
function computedValue(field: FieldDefinition): unknown {
  return rules.value?.computed[field.name] ?? props.modelValue[field.name]
}

const groups = computed(() => {
  const map = new Map<string, FieldDefinition[]>()
  for (const def of props.definitions) {
    if (!props.readonly && !isFieldApplicable(def, rules.value)) continue
    const key = def.group?.trim() || t('dossier.defaultGroup')
    const list = map.get(key) ?? []
    list.push(def)
    map.set(key, list)
  }
  return [...map.entries()]
})

watch(
  () => props.definitions,
  (defs) => {
    if (props.readonly || !props.applyDefaults) return
    const next = { ...props.modelValue }
    let changed = false
    for (const def of defs) {
      if (next[def.name] === undefined && def.defaultValue != null) {
        next[def.name] = def.defaultValue
        changed = true
      }
    }
    if (changed) emit('update:modelValue', next)
  },
  { immediate: true },
)

function setField(name: string, value: unknown) {
  emit('update:modelValue', { ...props.modelValue, [name]: value })
}

function messagesFor(name: string): string[] {
  return (props.serverErrors ?? []).filter((error) => error.field === name).map(describeFieldError)
}

const undisplayedErrors = computed(() => {
  const rendered = new Set(groups.value.flatMap(([, fields]) => fields.map((field) => field.name)))
  return (props.serverErrors ?? []).filter((error) => !rendered.has(error.field))
})

watch(undisplayedErrors, (errors) => emit('undisplayed-errors', errors), { immediate: true })

function fieldErrorId(name: string): string {
  return `field-error-${name}`
}
</script>

<template>
  <div class="dynamic-fields">
    <section v-for="[group, fields] in groups" :key="group" class="dynamic-fields__group">
      <h3>{{ group }}</h3>
      <!--
        Un champ structuré (montant, référence, groupe) porte plusieurs contrôles, voire un tableau : dans un <label>, un clic
        sur le libellé activerait le premier contrôle (ajout de ligne, case à cocher) et le HTML serait invalide.
      -->
      <div
        v-for="field in fields"
        :key="field.name"
        class="dynamic-fields__field"
        :class="{ 'dynamic-fields__field--half': field.width === 'HALF' }"
        :role="isStructured(field.type) ? 'group' : undefined"
        :aria-label="isStructured(field.type) ? field.label : undefined"
      >
        <component :is="isStructured(field.type) ? 'div' : 'label'" class="dynamic-fields__control">
        <span>
          {{ field.label }}<em v-if="isFieldRequired(field, rules)"> *</em>
          <small v-if="isComputedField(field)" class="dynamic-fields__computed-hint"> {{ t('dossier.computed') }}</small>
          <small v-else-if="isWorkflowManagedField(field)" class="dynamic-fields__computed-hint">
            {{ t('dossier.managedByWorkflow') }}
          </small>
        </span>
        <output
          v-if="!readonly && isReadOnlyOutputField(field)"
          class="dynamic-fields__computed"
          :data-testid="isWorkflowManagedField(field) ? `managed-${field.name}` : `computed-${field.name}`"
          :aria-invalid="messagesFor(field.name).length > 0 ? 'true' : undefined"
          :aria-describedby="messagesFor(field.name).length > 0 ? fieldErrorId(field.name) : undefined"
        >{{
          formatFieldValue(field, isComputedField(field) ? computedValue(field) : modelValue[field.name]) || '—'
        }}</output>
        <template v-else-if="readonly">
          <SensitiveValue :value="formatFieldValue(field, modelValue[field.name])" :sensitive="!!field.sensitive">
            <!-- TODO §76 : les lignes d'un groupe se lisent en tableau (visible seulement si la valeur est révélée). -->
            <table
              v-if="field.type === 'REPEATABLE_GROUP' && Array.isArray(modelValue[field.name]) && (modelValue[field.name] as unknown[]).length > 0"
              class="dynamic-fields__rows"
            >
              <thead>
                <tr>
                  <th v-for="sub in field.fields ?? []" :key="sub.name">{{ sub.label }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, index) in modelValue[field.name] as Record<string, unknown>[]" :key="index">
                  <td v-for="sub in field.fields ?? []" :key="sub.name">{{ formatFieldValue(sub, row[sub.name]) }}</td>
                </tr>
              </tbody>
            </table>
            <template v-else>{{ formatFieldValue(field, modelValue[field.name]) }}</template>
          </SensitiveValue>
        </template>
        <DynamicFieldInput
          v-else
          :field="field"
          :model-value="modelValue[field.name]"
          :invalid="messagesFor(field.name).length > 0"
          :described-by="messagesFor(field.name).length > 0 ? fieldErrorId(field.name) : undefined"
          @update:model-value="(value) => setField(field.name, value)"
        />
        </component>
        <small v-if="field.helpText && !readonly" class="dynamic-fields__help" :data-testid="`help-${field.name}`">{{ field.helpText }}</small>
        <p
          v-if="!readonly && messagesFor(field.name).length > 0"
          :id="fieldErrorId(field.name)"
          class="dynamic-fields__error"
          role="alert"
          data-testid="field-error"
        >
          {{ messagesFor(field.name).join(' ') }}
        </p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.dynamic-fields__group {
  margin-bottom: 1rem;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 1rem;
}
.dynamic-fields__group > h3 {
  grid-column: 1 / -1;
}
.dynamic-fields__field {
  display: grid;
  gap: 0.25rem;
  margin-bottom: 0.75rem;
  grid-column: 1 / -1;
  align-content: start;
}
.dynamic-fields__field--half {
  grid-column: auto;
}
.dynamic-fields__control {
  display: grid;
  gap: 0.25rem;
}
.dynamic-fields__help {
  color: #6b6b70;
  white-space: pre-line;
}
.dynamic-fields__error {
  color: #c92a2a;
  margin: 0;
}
@media (max-width: 640px) {
  .dynamic-fields__field--half {
    grid-column: 1 / -1;
  }
}
.dynamic-fields__computed-hint {
  color: #6b6b70;
  font-weight: normal;
}
.dynamic-fields__computed {
  padding: 0.45rem 0.55rem;
  background: #f4f4f6;
  border-radius: 4px;
}
.dynamic-fields__rows {
  border-collapse: collapse;
}
.dynamic-fields__rows th,
.dynamic-fields__rows td {
  padding: 0.2rem 0.5rem;
  text-align: left;
  border-bottom: 1px solid #e3e3e6;
}
.dynamic-fields__field input,
.dynamic-fields__field select {
  padding: 0.45rem 0.55rem;
  border: 1px solid #c5c5c8;
  border-radius: 4px;
}
</style>
