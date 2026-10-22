<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import ExpressionInput, { type SuggestionGroup } from './ExpressionInput.vue'
import { parseFieldMapping, serializeFieldMapping, type MappingRow } from './fieldMapping'

/**
 * Éditeur du paramètre `fieldMapping` : un tableau « champ du dossier ← expression » plutôt qu'un texte à la syntaxe à retenir.
 * Les champs proposés sont ceux du type de dossier choisi (sans type : saisie libre). Le texte brut reste accessible.
 */
const props = defineProps<{
  modelValue: string | undefined
  /** Champs cibles possibles ; `null` quand aucun type de dossier n'est choisi. */
  targets: { name: string; label: string }[] | null
  suggestions: SuggestionGroup[]
}>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const { t } = useI18n()

const rows = ref<MappingRow[]>([])
const raw = ref(false)

function load(value: string | undefined) {
  const parsed = parseFieldMapping(value)
  rows.value = parsed.length ? parsed : [{ field: '', expression: '' }]
}

load(props.modelValue)
watch(
  () => props.modelValue,
  (value) => {
    if (serializeFieldMapping(rows.value) !== (value ?? '')) load(value)
  },
)

function update() {
  emit('update:modelValue', serializeFieldMapping(rows.value))
}

function add() {
  rows.value.push({ field: '', expression: '' })
}

function remove(i: number) {
  rows.value.splice(i, 1)
  if (rows.value.length === 0) add()
  update()
}

const declared = computed(() => new Set((props.targets ?? []).map((f) => f.name)))
const duplicates = computed(() => {
  const seen = new Set<string>()
  const dup = new Set<string>()
  for (const r of rows.value) {
    const f = r.field.trim()
    if (f && seen.has(f)) dup.add(f)
    seen.add(f)
  }
  return dup
})

function problem(row: MappingRow): string | null {
  const field = row.field.trim()
  if (!field) return row.expression.trim() ? t('admin.parcours.mapping.noField') : null
  if (props.targets && !declared.value.has(field)) return t('admin.parcours.mapping.undeclared', { field })
  if (duplicates.value.has(field)) return t('admin.parcours.mapping.duplicate', { field })
  if (!row.expression.trim()) return t('admin.parcours.mapping.noExpression', { field })
  return null
}
</script>

<template>
  <div class="mapping" data-testid="mapping-editor">
    <template v-if="!raw">
      <div class="mapping__head" aria-hidden="true">
        <span>{{ t('admin.parcours.mapping.field') }}</span><span /><span>{{ t('admin.parcours.mapping.expression') }}</span>
      </div>
      <div v-for="(r, i) in rows" :key="i" class="mapping__row" data-testid="mapping-row">
        <select v-if="targets" v-model="r.field" :aria-label="t('admin.parcours.mapping.field')" data-testid="mapping-field" @change="update">
          <option value="" />
          <option v-if="r.field && !declared.has(r.field)" :value="r.field">{{ r.field }} ⚠</option>
          <option v-for="f in targets" :key="f.name" :value="f.name">{{ f.label }} ({{ f.name }})</option>
        </select>
        <input v-else v-model="r.field" :aria-label="t('admin.parcours.mapping.field')" :placeholder="t('admin.parcours.mapping.fieldPlaceholder')"
          data-testid="mapping-field" @input="update" />
        <span class="mapping__arrow" aria-hidden="true">←</span>
        <ExpressionInput v-model="r.expression" :suggestions="suggestions" :aria-label="t('admin.parcours.mapping.expression')"
          :placeholder="t('admin.parcours.mapping.expressionPlaceholder')" testid="mapping-expression" @update:model-value="update" />
        <button type="button" :aria-label="t('admin.parcours.mapping.remove')" @click="remove(i)">✕</button>
        <small v-if="problem(r)" class="mapping__warn" data-testid="mapping-warning">{{ problem(r) }}</small>
      </div>
      <button type="button" data-testid="mapping-add" @click="add">{{ t('admin.parcours.mapping.add') }}</button>
    </template>
    <textarea v-else :value="modelValue ?? ''" rows="4" spellcheck="false" data-testid="mapping-raw"
      @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)" />
    <label class="mapping__toggle"><input v-model="raw" type="checkbox" data-testid="mapping-raw-toggle" /> {{ t('admin.parcours.mapping.rawToggle') }}</label>
  </div>
</template>

<style scoped>
.mapping { display: flex; flex-direction: column; gap: 0.35rem; }
.mapping__head { display: grid; grid-template-columns: minmax(10rem, 16rem) 1.2rem 1fr; gap: 0.4rem; font-size: 0.85em; color: var(--color-text-muted, #555); }
.mapping__row { display: grid; grid-template-columns: minmax(10rem, 16rem) 1.2rem 1fr auto; gap: 0.4rem; align-items: start; }
.mapping__arrow { padding-top: 0.25rem; text-align: center; }
.mapping__warn { grid-column: 1 / -1; color: #8a4b08; }
.mapping__toggle { font-size: 0.85em; }
.mapping > button { align-self: flex-start; }
@media (max-width: 640px) {
  .mapping__head { display: none; }
  .mapping__row { grid-template-columns: 1fr auto; }
  .mapping__arrow { display: none; }
  .mapping__row > select, .mapping__row > input { grid-column: 1 / -1; }
}
</style>
