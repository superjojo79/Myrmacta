<script setup lang="ts">
import { ref, watch } from 'vue'
import type { FieldDefinition, FieldType } from '@/api/types'

const props = defineProps<{
  modelValue: FieldDefinition[]
}>()

const emit = defineEmits<{
  'update:modelValue': [FieldDefinition[]]
}>()

const FIELD_TYPES: FieldType[] = ['STRING', 'NUMBER', 'DATE', 'BOOLEAN', 'SELECT', 'FILE']

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
          @change="updateRow(index, { type: ($event.target as HTMLSelectElement).value as FieldType })"
        >
          <option v-for="type in FIELD_TYPES" :key="type" :value="type">{{ type }}</option>
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
      <label>
        Valeur par défaut
        <input
          :value="def.defaultValue ?? ''"
          data-testid="field-def-default"
          @input="updateRow(index, { defaultValue: ($event.target as HTMLInputElement).value })"
        />
      </label>
      <label v-if="def.type === 'SELECT'" data-testid="field-def-options">
        Options (séparées par des virgules)
        <input
          :value="(def.options ?? []).join(', ')"
          data-testid="field-def-options-input"
          @input="updateRow(index, { options: parseOptions(($event.target as HTMLInputElement).value) })"
        />
      </label>
      <label>
        Groupe
        <input
          :value="def.group ?? ''"
          data-testid="field-def-group"
          @input="updateRow(index, { group: ($event.target as HTMLInputElement).value })"
        />
      </label>
      <label class="field-def-editor__checkbox">
        <input
          type="checkbox"
          :checked="!!def.sensitive"
          data-testid="field-def-sensitive"
          @change="updateRow(index, { sensitive: ($event.target as HTMLInputElement).checked })"
        />
        Sensible
      </label>
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
