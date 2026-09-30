<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { FieldDefinition, FieldType } from '@/api/types'

// TODO §76 (D1) : `mode` choisit les types proposés — `fields` (champs de dossier : tous), `task` (questions de tâche :
// les six types historiques, le serveur refuse les types structurés), `subfields` (colonnes d'un groupe répétable).
const props = withDefaults(
  defineProps<{
    modelValue: FieldDefinition[]
    mode?: 'fields' | 'task' | 'subfields'
  }>(),
  { mode: 'fields' },
)

const emit = defineEmits<{
  'update:modelValue': [FieldDefinition[]]
}>()

const LEGACY_TYPES: FieldType[] = ['STRING', 'NUMBER', 'DATE', 'BOOLEAN', 'SELECT', 'FILE']
const STRUCTURED: FieldType[] = ['MONEY', 'REPEATABLE_GROUP', 'EXTERNAL_REF']
const fieldTypes = computed<FieldType[]>(() => {
  if (props.mode === 'task') return LEGACY_TYPES
  if (props.mode === 'subfields') return ['STRING', 'NUMBER', 'DATE', 'BOOLEAN', 'SELECT', 'MONEY', 'EXTERNAL_REF']
  return [...LEGACY_TYPES, ...STRUCTURED]
})
const isSub = computed(() => props.mode === 'subfields')

// Local reactive copy so the editor re-renders immediately on every keystroke/toggle without
// waiting for the parent to feed the emitted list back through the modelValue prop (same pattern
// as DossierFieldsEditor.vue's `draft` ref).
const rows = ref<FieldDefinition[]>([...props.modelValue])

watch(
  () => props.modelValue,
  (value) => {
    rows.value = [...value]
  },
)

function emitRows() {
  emit('update:modelValue', [...rows.value])
}

function addRow() {
  rows.value.push({
    name: '',
    label: '',
    type: 'STRING',
    required: false,
    defaultValue: '',
    options: [],
    group: '',
    sensitive: false,
  })
  emitRows()
}

function removeRow(index: number) {
  rows.value.splice(index, 1)
  emitRows()
}

function moveRow(index: number, delta: number) {
  const target = index + delta
  if (target < 0 || target >= rows.value.length) return
  const moved = rows.value[index]!
  rows.value[index] = rows.value[target]!
  rows.value[target] = moved
  emitRows()
}

function changeType(index: number, type: FieldType) {
  const before = rows.value[index]!.type
  if (STRUCTURED.includes(type) || STRUCTURED.includes(before)) {
    // Options, valeur par défaut et sous-champs d'un type ne valent pas pour un autre : on repart propre.
    updateRow(index, {
      type,
      options: [],
      defaultValue: type === 'STRING' || type === 'NUMBER' || type === 'DATE' || type === 'BOOLEAN' ? '' : null,
      fields: [],
      minRows: null,
      maxRows: null,
    })
    return
  }
  updateRow(index, { type })
}

function parseRows(raw: string): number | null {
  const n = Number.parseInt(raw, 10)
  return Number.isNaN(n) ? null : n
}

function updateRow(index: number, patch: Partial<FieldDefinition>) {
  rows.value[index] = { ...rows.value[index]!, ...patch }
  emitRows()
}

function parseOptions(raw: string): string[] {
  return raw
    .split(',')
    .map((option) => option.trim())
    .filter((option) => option.length > 0)
}
</script>

<template>
  <div class="field-def-editor">
    <div v-for="(def, index) in rows" :key="index" class="field-def-editor__row" data-testid="field-def-row">
      <label>
        Nom
        <input
          :value="def.name"
          data-testid="field-def-name"
          @input="updateRow(index, { name: ($event.target as HTMLInputElement).value })"
        />
      </label>
      <label>
        Libellé
        <input
          :value="def.label"
          data-testid="field-def-label"
          @input="updateRow(index, { label: ($event.target as HTMLInputElement).value })"
        />
      </label>
      <label>
        Type
        <select
          :value="def.type"
          data-testid="field-def-type"
          @change="changeType(index, ($event.target as HTMLSelectElement).value as FieldType)"
        >
          <option v-for="type in fieldTypes" :key="type" :value="type">{{ type }}</option>
        </select>
      </label>
      <label class="field-def-editor__checkbox">
        <input
          type="checkbox"
          :checked="def.required"
          data-testid="field-def-required"
          @change="updateRow(index, { required: ($event.target as HTMLInputElement).checked })"
        />
        Obligatoire
      </label>
      <label v-if="!STRUCTURED.includes(def.type)">
        Valeur par défaut
        <input
          :value="def.defaultValue ?? ''"
          data-testid="field-def-default"
          @input="updateRow(index, { defaultValue: ($event.target as HTMLInputElement).value })"
        />
      </label>
      <label v-if="def.type === 'SELECT' || def.type === 'MONEY' || def.type === 'EXTERNAL_REF'" data-testid="field-def-options">
        {{
          def.type === 'MONEY'
            ? 'Devises autorisées (codes ISO 4217 séparés par des virgules, vide = toutes)'
            : def.type === 'EXTERNAL_REF'
              ? 'Systèmes autorisés (séparés par des virgules, vide = tous)'
              : 'Options (séparées par des virgules)'
        }}
        <input
          :value="(def.options ?? []).join(', ')"
          data-testid="field-def-options-input"
          @input="updateRow(index, { options: parseOptions(($event.target as HTMLInputElement).value) })"
        />
      </label>
      <label v-if="!isSub">
        Groupe
        <input
          :value="def.group ?? ''"
          data-testid="field-def-group"
          @input="updateRow(index, { group: ($event.target as HTMLInputElement).value })"
        />
      </label>
      <label v-if="!isSub" class="field-def-editor__checkbox">
        <input
          type="checkbox"
          :checked="!!def.sensitive"
          data-testid="field-def-sensitive"
          @change="updateRow(index, { sensitive: ($event.target as HTMLInputElement).checked })"
        />
        Sensible
      </label>
      <template v-if="def.type === 'REPEATABLE_GROUP'">
        <label>
          Lignes min.
          <input
            type="number"
            min="0"
            :value="def.minRows ?? ''"
            data-testid="field-def-min-rows"
            @input="updateRow(index, { minRows: parseRows(($event.target as HTMLInputElement).value) })"
          />
        </label>
        <label>
          Lignes max.
          <input
            type="number"
            min="1"
            max="500"
            :value="def.maxRows ?? ''"
            data-testid="field-def-max-rows"
            @input="updateRow(index, { maxRows: parseRows(($event.target as HTMLInputElement).value) })"
          />
        </label>
        <fieldset class="field-def-editor__subfields" data-testid="field-def-subfields">
          <legend>Colonnes du groupe (sous-champs, un seul niveau ; sensibilité = celle du groupe)</legend>
          <FieldDefinitionDefsEditor
            :model-value="def.fields ?? []"
            mode="subfields"
            @update:model-value="(fields) => updateRow(index, { fields })"
          />
        </fieldset>
      </template>
      <div class="field-def-editor__actions">
        <button type="button" data-testid="field-def-move-up" :disabled="index === 0" @click="moveRow(index, -1)">
          ↑
        </button>
        <button
          type="button"
          data-testid="field-def-move-down"
          :disabled="index === rows.length - 1"
          @click="moveRow(index, 1)"
        >
          ↓
        </button>
        <button type="button" data-testid="field-def-remove" @click="removeRow(index)">Supprimer</button>
      </div>
    </div>
    <button type="button" data-testid="field-def-add" @click="addRow">Ajouter un champ</button>
  </div>
</template>

<style scoped>
.field-def-editor__row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: end;
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 0.5rem;
  margin-bottom: 0.5rem;
}
.field-def-editor__row label {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  font-size: 0.85rem;
}
.field-def-editor__checkbox {
  flex-direction: row !important;
  align-items: center;
  gap: 0.3rem !important;
}
.field-def-editor__subfields {
  flex-basis: 100%;
  border: 1px dashed #c5c5c8;
  border-radius: 4px;
}
.field-def-editor__actions {
  display: flex;
  gap: 0.25rem;
  margin-left: auto;
}
.field-def-editor input,
.field-def-editor select {
  padding: 0.35rem 0.5rem;
}
</style>
