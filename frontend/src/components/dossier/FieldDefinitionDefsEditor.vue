<script setup lang="ts">
import { useI18n } from 'vue-i18n'
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

const { t } = useI18n()
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
      // Un groupe répétable occupe toute la largeur : une demi-colonne le rendrait inutilisable.
      ...(type === 'REPEATABLE_GROUP' ? { width: null } : {}),
    })
    return
  }
  updateRow(index, { type })
}

function parseRows(raw: string): number | null {
  const n = Number.parseInt(raw, 10)
  return Number.isNaN(n) ? null : n
}

const SEARCHABLE_TYPES = ['STRING', 'NUMBER', 'DATE', 'BOOLEAN', 'SELECT']

function updateRow(index: number, patch: Partial<FieldDefinition>) {
  const next = { ...rows.value[index]!, ...patch }
  // TODO §81 (D2) : un champ sensible ou d'un type non scalaire ne peut pas être cherchable.
  if (next.searchable && (next.sensitive || !SEARCHABLE_TYPES.includes(next.type))) next.searchable = false
  rows.value[index] = next
  emitRows()
}

// TODO §85 (LC4a) : glisser-déposer pour réordonner (les boutons ↑ ↓ restent, accessibles au clavier).
const dragIndex = ref<number | null>(null)
function onDragStart(index: number, event: DragEvent) {
  dragIndex.value = index
  event.dataTransfer?.setData('text/plain', String(index))
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}
function onDrop(index: number) {
  const from = dragIndex.value
  dragIndex.value = null
  if (from == null || from === index) return
  const [moved] = rows.value.splice(from, 1)
  rows.value.splice(index, 0, moved!)
  emitRows()
}

/** Sections déjà utilisées : proposées à la saisie du groupe (une faute de frappe créerait une section de plus). */
const knownGroups = computed(() => [...new Set(rows.value.map((row) => (row.group ?? '').trim()).filter((g) => g !== ''))])
const groupListId = `groups-${Math.random().toString(36).slice(2, 8)}`

function parseOptions(raw: string): string[] {
  return raw
    .split(',')
    .map((option) => option.trim())
    .filter((option) => option.length > 0)
}
</script>

<template>
  <div class="field-def-editor">
    <datalist v-if="!isSub" :id="groupListId">
      <option v-for="group in knownGroups" :key="group" :value="group" />
    </datalist>
    <div
      v-for="(def, index) in rows"
      :key="index"
      class="field-def-editor__row"
      :class="{ 'field-def-editor__row--drag': dragIndex === index }"
      data-testid="field-def-row"
      @dragover.prevent
      @drop.prevent="onDrop(index)"
    >
      <span
        v-if="!isSub"
        class="field-def-editor__handle"
        draggable="true"
        :title="t('dossier.defsEditor.drag')"
        aria-hidden="true"
        data-testid="field-def-handle"
        @dragstart="onDragStart(index, $event)"
        @dragend="dragIndex = null"
        >⋮⋮</span
      >
      <label>
        {{ t('dossier.defsEditor.name') }}
        <input
          :value="def.name"
          data-testid="field-def-name"
          @input="updateRow(index, { name: ($event.target as HTMLInputElement).value })"
        />
      </label>
      <label>
        {{ t('dossier.defsEditor.label') }}
        <input
          :value="def.label"
          data-testid="field-def-label"
          @input="updateRow(index, { label: ($event.target as HTMLInputElement).value })"
        />
      </label>
      <label>
        {{ t('dossier.defsEditor.type') }}
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
        {{ t('dossier.defsEditor.required') }}
      </label>
      <label v-if="!STRUCTURED.includes(def.type) && !isSub">
        {{ t('dossier.defsEditor.defaultValue') }}
        <input
          :value="def.defaultValue ?? ''"
          data-testid="field-def-default"
          @input="updateRow(index, { defaultValue: ($event.target as HTMLInputElement).value })"
        />
      </label>
      <label v-if="def.type === 'SELECT' || def.type === 'MONEY' || def.type === 'EXTERNAL_REF'" data-testid="field-def-options">
        {{
          def.type === 'MONEY'
            ? t('dossier.defsEditor.currencies')
            : def.type === 'EXTERNAL_REF'
              ? t('dossier.defsEditor.systems')
              : t('dossier.defsEditor.options')
        }}
        <input
          :value="(def.options ?? []).join(', ')"
          data-testid="field-def-options-input"
          @input="updateRow(index, { options: parseOptions(($event.target as HTMLInputElement).value) })"
        />
      </label>
      <label v-if="!isSub">
        {{ t('dossier.defsEditor.section') }}
        <input
          :value="def.group ?? ''"
          :list="groupListId"
          data-testid="field-def-group"
          @input="updateRow(index, { group: ($event.target as HTMLInputElement).value })"
        />
      </label>
      <label v-if="!isSub && def.type !== 'REPEATABLE_GROUP'">
        {{ t('dossier.defsEditor.width') }}
        <select
          :value="def.width ?? 'FULL'"
          data-testid="field-def-width"
          @change="updateRow(index, { width: ($event.target as HTMLSelectElement).value === 'HALF' ? 'HALF' : null })"
        >
          <option value="FULL">{{ t('dossier.defsEditor.full') }}</option>
          <option value="HALF">{{ t('dossier.defsEditor.half') }}</option>
        </select>
      </label>
      <label v-if="!isSub">
        {{ t('dossier.defsEditor.help') }}
        <input
          :value="def.helpText ?? ''"
          maxlength="500"
          data-testid="field-def-help"
          @input="updateRow(index, { helpText: ($event.target as HTMLInputElement).value || null })"
        />
      </label>
      <label v-if="!isSub" class="field-def-editor__checkbox">
        <input
          type="checkbox"
          :checked="!!def.sensitive"
          data-testid="field-def-sensitive"
          @change="updateRow(index, { sensitive: ($event.target as HTMLInputElement).checked })"
        />
        {{ t('dossier.defsEditor.sensitive') }}
      </label>
      <label
        v-if="!isSub && ['STRING', 'NUMBER', 'DATE', 'BOOLEAN', 'SELECT'].includes(def.type)"
        class="field-def-editor__checkbox"
        :title="def.sensitive ? t('dossier.defsEditor.sensitiveNotSearchable') : t('dossier.defsEditor.searchableHint')"
      >
        <input
          type="checkbox"
          :checked="!!def.searchable"
          :disabled="!!def.sensitive"
          data-testid="field-def-searchable"
          @change="updateRow(index, { searchable: ($event.target as HTMLInputElement).checked })"
        />
        {{ t('dossier.defsEditor.searchable') }}
      </label>
      <details v-if="mode === 'fields' && def.type !== 'REPEATABLE_GROUP' && def.type !== 'FILE'" class="field-def-editor__rules" data-testid="field-def-rules">
        <summary>{{ t('dossier.defsEditor.rules') }}</summary>
        <i18n-t keypath="dossier.defsEditor.rulesHint" tag="p" class="field-def-editor__hint">
          <template #example><code>dossier.fields.nom_du_champ</code></template>
        </i18n-t>
        <label>
          {{ t('dossier.defsEditor.formula') }}
          <input
            :value="def.formula ?? ''"
            data-testid="field-def-formula"
            placeholder="dossier.fields.ht * (1 + dossier.fields.taux)"
            @input="updateRow(index, { formula: ($event.target as HTMLInputElement).value || null })"
          />
        </label>
        <label>
          {{ t('dossier.defsEditor.visibleWhen') }}
          <input
            :value="def.visibleWhen ?? ''"
            data-testid="field-def-visible-when"
            placeholder="dossier.fields.pays = 'FR'"
            @input="updateRow(index, { visibleWhen: ($event.target as HTMLInputElement).value || null })"
          />
        </label>
        <label>
          {{ t('dossier.defsEditor.requiredWhen') }}
          <input
            :value="def.requiredWhen ?? ''"
            data-testid="field-def-required-when"
            placeholder="dossier.fields.montant > 1000"
            @input="updateRow(index, { requiredWhen: ($event.target as HTMLInputElement).value || null })"
          />
        </label>
      </details>
      <template v-if="def.type === 'REPEATABLE_GROUP'">
        <label>
          {{ t('dossier.defsEditor.minRows') }}
          <input
            type="number"
            min="0"
            :value="def.minRows ?? ''"
            data-testid="field-def-min-rows"
            @input="updateRow(index, { minRows: parseRows(($event.target as HTMLInputElement).value) })"
          />
        </label>
        <label>
          {{ t('dossier.defsEditor.maxRows') }}
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
          <legend>{{ t('dossier.defsEditor.columns') }}</legend>
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
        <button type="button" data-testid="field-def-remove" @click="removeRow(index)">{{ t('dossier.defsEditor.remove') }}</button>
      </div>
    </div>
    <button type="button" data-testid="field-def-add" @click="addRow">{{ t('dossier.defsEditor.add') }}</button>
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
.field-def-editor__handle {
  cursor: grab;
  color: #6b6b70;
  align-self: center;
  user-select: none;
}
.field-def-editor__row--drag {
  opacity: 0.5;
}
.field-def-editor__checkbox {
  flex-direction: row !important;
  align-items: center;
  gap: 0.3rem !important;
}
.field-def-editor__rules {
  flex-basis: 100%;
}
.field-def-editor__rules label {
  margin-top: 0.4rem;
}
.field-def-editor__hint {
  font-size: 0.8rem;
  color: #6b6b70;
  margin: 0.3rem 0 0;
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
