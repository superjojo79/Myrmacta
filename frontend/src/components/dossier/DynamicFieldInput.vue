<script setup lang="ts">
import { computed } from 'vue'
import type { FieldDefinition } from '@/api/types'

// TODO §68 (portail-externe) : saisie d'un champ, extraite de DynamicFields.vue pour être
// réutilisable par la page publique du portail sans entraîner SensitiveValue.vue (qui dépend du
// store d'authentification du SPA interne). Composant pur : aucune dépendance store/API/routeur.
// TODO §76 (D1) : types structurés — montant {amount, currency}, référence {system, id, label},
// groupe répétable (tableau de lignes dont les cellules sont ce même composant, un seul niveau).
const props = defineProps<{
  field: Pick<FieldDefinition, 'name' | 'label' | 'type' | 'required' | 'options' | 'fields' | 'minRows' | 'maxRows'>
  modelValue: unknown
  inputId?: string
  /** Nom accessible d'un contrôle sans libellé visible propre (cellule d'un groupe). */
  ariaLabel?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [unknown]
}>()

type Rec = Record<string, unknown>

function asRec(value: unknown): Rec {
  return value !== null && typeof value === 'object' && !Array.isArray(value) ? (value as Rec) : {}
}
function str(value: unknown): string {
  return value === undefined || value === null ? '' : String(value)
}

const options = computed(() => props.field.options ?? [])

// --- MONEY
const money = computed(() => asRec(props.modelValue))
function setMoney(patch: Rec) {
  const next = { amount: str(money.value.amount), currency: str(money.value.currency), ...patch }
  // Une seule devise autorisée : inutile de la faire saisir.
  if (options.value.length === 1 && next.currency === '' && next.amount !== '') next.currency = options.value[0]!
  emit('update:modelValue', next)
}

// --- EXTERNAL_REF
const ref = computed(() => asRec(props.modelValue))
function setRef(patch: Rec) {
  const next = { system: str(ref.value.system), id: str(ref.value.id), label: str(ref.value.label), ...patch }
  if (options.value.length === 1 && next.system === '' && next.id !== '') next.system = options.value[0]!
  emit('update:modelValue', next)
}

// --- REPEATABLE_GROUP
const rows = computed<Rec[]>(() => (Array.isArray(props.modelValue) ? (props.modelValue as unknown[]).map(asRec) : []))
const subFields = computed(() => props.field.fields ?? [])
const maxRows = computed(() => props.field.maxRows ?? 100)
const minRows = computed(() => props.field.minRows ?? 0)
function addRow() {
  if (rows.value.length >= maxRows.value) return
  emit('update:modelValue', [...rows.value, {}])
}
function removeRow(index: number) {
  emit('update:modelValue', rows.value.filter((_, i) => i !== index))
}
function setCell(index: number, name: string, value: unknown) {
  emit(
    'update:modelValue',
    rows.value.map((row, i) => (i === index ? { ...row, [name]: value } : row)),
  )
}
</script>

<template>
  <input
    v-if="field.type === 'BOOLEAN'"
    :id="inputId"
    type="checkbox"
    :aria-label="ariaLabel"
    :checked="Boolean(modelValue)"
    @change="emit('update:modelValue', ($event.target as HTMLInputElement).checked)"
  />
  <select
    v-else-if="field.type === 'SELECT'"
    :id="inputId"
    :aria-label="ariaLabel"
    :value="String(modelValue ?? '')"
    @change="emit('update:modelValue', ($event.target as HTMLSelectElement).value)"
  >
    <option value="">—</option>
    <option v-for="opt in field.options ?? []" :key="opt" :value="opt">{{ opt }}</option>
  </select>

  <span v-else-if="field.type === 'MONEY'" class="structured-input structured-input--money">
    <input
      :id="inputId"
      type="text"
      inputmode="decimal"
      autocomplete="off"
      :aria-label="`${ariaLabel ?? field.label} — montant`"
      :value="str(money.amount)"
      @input="setMoney({ amount: ($event.target as HTMLInputElement).value })"
    />
    <select
      v-if="options.length > 0"
      :aria-label="`${ariaLabel ?? field.label} — devise`"
      :value="str(money.currency)"
      @change="setMoney({ currency: ($event.target as HTMLSelectElement).value })"
    >
      <option value="">—</option>
      <option v-for="code in options" :key="code" :value="code">{{ code }}</option>
    </select>
    <input
      v-else
      type="text"
      maxlength="3"
      size="4"
      placeholder="EUR"
      autocomplete="off"
      :aria-label="`${ariaLabel ?? field.label} — devise`"
      :value="str(money.currency)"
      @input="setMoney({ currency: ($event.target as HTMLInputElement).value.toUpperCase() })"
    />
  </span>

  <span v-else-if="field.type === 'EXTERNAL_REF'" class="structured-input structured-input--ref">
    <select
      v-if="options.length > 0"
      :aria-label="`${ariaLabel ?? field.label} — système`"
      :value="str(ref.system)"
      @change="setRef({ system: ($event.target as HTMLSelectElement).value })"
    >
      <option value="">—</option>
      <option v-for="system in options" :key="system" :value="system">{{ system }}</option>
    </select>
    <input
      v-else
      type="text"
      placeholder="système"
      autocomplete="off"
      :aria-label="`${ariaLabel ?? field.label} — système`"
      :value="str(ref.system)"
      @input="setRef({ system: ($event.target as HTMLInputElement).value })"
    />
    <input
      :id="inputId"
      type="text"
      placeholder="identifiant"
      autocomplete="off"
      :aria-label="`${ariaLabel ?? field.label} — identifiant`"
      :value="str(ref.id)"
      @input="setRef({ id: ($event.target as HTMLInputElement).value })"
    />
    <input
      type="text"
      placeholder="libellé (facultatif)"
      autocomplete="off"
      :aria-label="`${ariaLabel ?? field.label} — libellé`"
      :value="str(ref.label)"
      @input="setRef({ label: ($event.target as HTMLInputElement).value })"
    />
  </span>

  <span v-else-if="field.type === 'REPEATABLE_GROUP'" class="structured-input structured-input--group">
    <table v-if="rows.length > 0" class="group-table">
      <thead>
        <tr>
          <th v-for="sub in subFields" :key="sub.name">{{ sub.label }}<em v-if="sub.required"> *</em></th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, index) in rows" :key="index" data-testid="group-row">
          <td v-for="sub in subFields" :key="sub.name">
            <DynamicFieldInput
              :field="sub"
              :model-value="row[sub.name]"
              :aria-label="`${sub.label} (ligne ${index + 1})`"
              @update:model-value="(value) => setCell(index, sub.name, value)"
            />
          </td>
          <td>
            <button
              type="button"
              :disabled="rows.length <= minRows"
              :aria-label="`Supprimer la ligne ${index + 1}`"
              @click="removeRow(index)"
            >
              ✕
            </button>
          </td>
        </tr>
      </tbody>
    </table>
    <button type="button" data-testid="group-add-row" :disabled="rows.length >= maxRows" @click="addRow">
      Ajouter une ligne
    </button>
    <small v-if="rows.length >= maxRows">Maximum {{ maxRows }} lignes.</small>
  </span>

  <input
    v-else
    :id="inputId"
    :aria-label="ariaLabel"
    :type="field.type === 'NUMBER' ? 'number' : field.type === 'DATE' ? 'date' : 'text'"
    :value="modelValue == null ? '' : String(modelValue)"
    @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
  />
</template>

<style scoped>
.structured-input {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  align-items: center;
}
.structured-input--group {
  display: block;
}
.group-table {
  border-collapse: collapse;
  margin-bottom: 0.4rem;
}
.group-table th,
.group-table td {
  padding: 0.2rem 0.35rem;
  text-align: left;
  font-weight: normal;
}
</style>
