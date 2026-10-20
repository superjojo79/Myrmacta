<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { validateExpression, type ExpressionValidation } from '@/api/expressions'
import { insertAt } from './fieldMapping'

/**
 * Saisie d'une expression métier (JSONata borné) : liste « Insérer… » des champs, variables et opérateurs connus, insérés au
 * curseur, et validation à blanc côté serveur pendant la frappe (mêmes règles qu'au déploiement).
 */
export interface Suggestion {
  value: string
  label: string
}
export interface SuggestionGroup {
  label: string
  items: Suggestion[]
}

const props = withDefaults(
  defineProps<{
    modelValue: string | undefined
    suggestions: SuggestionGroup[]
    multiline?: boolean
    placeholder?: string
    ariaLabel?: string
    testid?: string
  }>(),
  { multiline: false, placeholder: '', ariaLabel: undefined, testid: undefined },
)
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const { t } = useI18n()

const input = ref<HTMLInputElement | HTMLTextAreaElement | null>(null)
const status = ref<ExpressionValidation | null>(null)
let timer: ReturnType<typeof setTimeout> | undefined
let seq = 0

function insert(event: Event) {
  const select = event.target as HTMLSelectElement
  const snippet = select.value
  select.value = ''
  if (!snippet) return
  const text = props.modelValue ?? ''
  const el = input.value
  const start = el?.selectionStart ?? text.length
  const end = el?.selectionEnd ?? text.length
  const next = insertAt(text, start, end, snippet)
  emit('update:modelValue', next.text)
  void nextTick(() => {
    el?.focus()
    el?.setSelectionRange(next.caret, next.caret)
  })
}

watch(
  () => props.modelValue,
  (value) => {
    clearTimeout(timer)
    const expression = (value ?? '').trim()
    const mine = ++seq
    if (!expression) {
      status.value = null
      return
    }
    timer = setTimeout(async () => {
      try {
        const result = await validateExpression(expression)
        if (mine === seq) status.value = result
      } catch {
        // Pas de droit sur l'endpoint ou panne : on n'affiche rien plutôt qu'une fausse erreur.
        if (mine === seq) status.value = null
      }
    }, 400)
  },
  { immediate: true },
)

onBeforeUnmount(() => clearTimeout(timer))
</script>

<template>
  <div class="expr">
    <div class="expr__line">
      <textarea
        v-if="multiline"
        ref="input"
        :value="modelValue ?? ''"
        rows="2"
        spellcheck="false"
        :placeholder="placeholder"
        :aria-label="ariaLabel"
        :data-testid="testid"
        @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
      />
      <input
        v-else
        ref="input"
        :value="modelValue ?? ''"
        spellcheck="false"
        :placeholder="placeholder"
        :aria-label="ariaLabel"
        :data-testid="testid"
        @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
      />
      <select class="expr__insert" :aria-label="t('admin.parcours.expr.insert')" :data-testid="testid && `${testid}-insert`" @change="insert">
        <option value="">{{ t('admin.parcours.expr.insert') }}</option>
        <optgroup v-for="g in suggestions.filter((x) => x.items.length)" :key="g.label" :label="g.label">
          <option v-for="s in g.items" :key="s.value" :value="s.value">{{ s.label }}</option>
        </optgroup>
      </select>
    </div>
    <small v-if="status?.valid" class="expr__ok" :data-testid="testid && `${testid}-status`">✓ {{ t('admin.parcours.expr.valid') }}</small>
    <small v-else-if="status" class="expr__ko" role="alert" :data-testid="testid && `${testid}-status`">
      ✗ {{ status.message }}<template v-if="status.position != null"> — {{ t('admin.parcours.expr.position', { position: status.position }) }}</template>
    </small>
  </div>
</template>

<style scoped>
.expr { display: flex; flex-direction: column; gap: 0.15rem; flex: 1; min-width: 14rem; }
.expr__line { display: flex; gap: 0.4rem; align-items: flex-start; }
.expr__line > input, .expr__line > textarea { flex: 1; min-width: 0; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; }
.expr__insert { max-width: 11rem; }
.expr__ok { color: #2f9e44; }
.expr__ko { color: #c92a2a; }
</style>
