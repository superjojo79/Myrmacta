<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { computed, onMounted, ref, shallowRef, watch } from 'vue'
import { listAllWorkflowTypeConfigs, listStartVariables } from '@/api/workflows'
import type { WorkflowTypeConfig } from '@/api/types'
import {
  collectDeclaredStartVariables,
  validateStartVariables,
  type DeclaredStartVariable,
  type StartVariableError,
  type StartVariableRow,
} from './startVariables'

const props = defineProps<{
  open: boolean
  defaultWorkflowKey: string
}>()

const emit = defineEmits<{
  close: []
  start: [processDefinitionKey: string, variables: Record<string, string>]
}>()

const useOther = ref(false)
const selectedKey = ref(props.defaultWorkflowKey)
const configs = ref<WorkflowTypeConfig[]>([])
const loading = shallowRef(false)
// TODO §93 — variables de démarrage (texte) : section repliée, validée avant l'envoi.
const rows = ref<StartVariableRow[]>([])
const rowErrors = ref<Record<number, StartVariableError>>({})
const variablesOpen = shallowRef(false)
// TODO §111 — si le BPMN déployé déclare des variables, le formulaire remplace la saisie libre.
const declared = ref<DeclaredStartVariable[]>([])
const declaredValues = ref<Record<string, string>>({})
const declaredMissing = shallowRef('')
const variablesReady = shallowRef(false)
const variablesError = shallowRef(false)
let loadToken = 0

const effectiveKey = computed(() => (useOther.value ? selectedKey.value : props.defaultWorkflowKey))

watch(
  () => props.defaultWorkflowKey,
  (value) => {
    if (!useOther.value) selectedKey.value = value
  },
)

watch(
  () => props.open,
  (open) => {
    if (open) {
      useOther.value = false
      selectedKey.value = props.defaultWorkflowKey
      rows.value = []
      rowErrors.value = {}
      variablesOpen.value = false
      declaredMissing.value = ''
    }
  },
)

watch(
  [() => props.open, effectiveKey],
  () => {
    if (props.open) void loadDeclarations(effectiveKey.value)
  },
  { immediate: true },
)

onMounted(async () => {
  loading.value = true
  try {
    configs.value = await listAllWorkflowTypeConfigs()
  } finally {
    loading.value = false
  }
})

async function loadDeclarations(key: string) {
  const token = ++loadToken
  variablesReady.value = false
  variablesError.value = false
  declaredMissing.value = ''
  if (!key) {
    declared.value = []
    declaredValues.value = {}
    variablesReady.value = true
    return
  }
  try {
    const next = await listStartVariables(key)
    if (token !== loadToken) return
    declared.value = next
    declaredValues.value = Object.fromEntries(next.map((item) => [item.name, '']))
    variablesReady.value = true
  } catch {
    if (token !== loadToken) return
    declared.value = []
    variablesError.value = true
  }
}

function addRow() {
  rows.value.push({ name: '', value: '' })
  variablesOpen.value = true
}

function removeRow(index: number) {
  rows.value.splice(index, 1)
  rowErrors.value = {}
}

function onToggle(event: Event) {
  variablesOpen.value = (event.target as HTMLDetailsElement).open
}

function submit() {
  const key = effectiveKey.value
  if (!key || !variablesReady.value || variablesError.value) return
  if (declared.value.length > 0) {
    const { variables, missing } = collectDeclaredStartVariables(declared.value, declaredValues.value)
    if (missing.length > 0) {
      declaredMissing.value = missing[0].label
      return
    }
    emit('start', key, variables)
    return
  }
  const { variables, errors } = validateStartVariables(rows.value)
  rowErrors.value = errors
  if (Object.keys(errors).length > 0) {
    variablesOpen.value = true
    return
  }
  emit('start', key, variables)
}

const { t } = useI18n()
</script>

<template>
  <div v-if="open" class="dialog" role="dialog" aria-modal="true">
    <div class="dialog__panel">
      <h2>{{ t('workflow.startDialog.title') }}</h2>
      <label class="dialog__row">
        <input v-model="useOther" type="checkbox" />
        {{ t('workflow.startDialog.useOther') }}
      </label>
      <p v-if="!useOther">{{ t('workflow.startDialog.defaultKey') }} <strong>{{ defaultWorkflowKey || '—' }}</strong></p>
      <label v-else class="dialog__row">
        {{ t('workflow.startDialog.configuration') }}
        <select v-model="selectedKey" :disabled="loading">
          <option v-for="cfg in configs" :key="cfg.workflowKey" :value="cfg.workflowKey">
            {{ cfg.label }} ({{ cfg.workflowKey }})
          </option>
        </select>
      </label>
      <p v-if="variablesError" class="dialog__error" role="alert">{{ t('workflow.startDialog.variables.loadError') }}</p>
      <div
        v-else-if="variablesReady && declared.length > 0"
        data-testid="declared-start-variables"
        class="dialog__declared"
      >
        <p class="dialog__hint">{{ t('workflow.startDialog.variables.declaredHint') }}</p>
        <label v-for="field in declared" :key="field.name" class="dialog__row">
          <span>
            {{ field.label }}
            <abbr v-if="field.required" :title="t('workflow.startDialog.variables.required')">*</abbr>
          </span>
          <input
            v-model="declaredValues[field.name]"
            data-testid="declared-start-variable"
            :aria-label="field.label"
            :aria-required="field.required ? 'true' : undefined"
          />
        </label>
        <p v-if="declaredMissing" data-testid="declared-start-error" class="dialog__error" role="alert">
          {{ t('workflow.startDialog.variables.missing', { label: declaredMissing }) }}
        </p>
      </div>
      <details
        v-else-if="variablesReady"
        data-testid="start-variables"
        class="dialog__variables"
        :open="variablesOpen"
        @toggle="onToggle"
      >
        <summary>{{ t('workflow.startDialog.variables.title') }}</summary>
        <p class="dialog__hint">{{ t('workflow.startDialog.variables.hint') }}</p>
        <div v-for="(row, index) in rows" :key="index" data-testid="start-variable-row" class="dialog__variable">
          <input
            v-model="row.name"
            data-testid="start-variable-name"
            :aria-label="t('workflow.startDialog.variables.name')"
            :placeholder="t('workflow.startDialog.variables.name')"
            :aria-invalid="rowErrors[index] ? 'true' : undefined"
          />
          <input
            v-model="row.value"
            data-testid="start-variable-value"
            :aria-label="t('workflow.startDialog.variables.value')"
            :placeholder="t('workflow.startDialog.variables.value')"
          />
          <button
            type="button"
            data-testid="start-variable-remove"
            :aria-label="t('workflow.startDialog.variables.remove')"
            @click="removeRow(index)"
          >
            ×
          </button>
          <p v-if="rowErrors[index]" data-testid="start-variable-error" class="dialog__error" role="alert">
            {{ t(`workflow.startDialog.variables.errors.${rowErrors[index]}`, { name: row.name.trim() }) }}
          </p>
        </div>
        <button type="button" data-testid="start-variables-add" @click="addRow">
          {{ t('workflow.startDialog.variables.add') }}
        </button>
      </details>
      <div class="dialog__actions">
        <button type="button" @click="emit('close')">{{ t('common.cancel') }}</button>
        <button
          type="button"
          class="primary"
          :disabled="(!selectedKey && !defaultWorkflowKey) || !variablesReady || variablesError"
          @click="submit"
        >
          {{ t('workflow.startDialog.start') }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dialog {
  position: fixed;
  inset: 0;
  background: rgba(20, 30, 40, 0.45);
  display: grid;
  place-items: center;
  z-index: 40;
}
.dialog__panel {
  background: white;
  padding: 1.25rem;
  width: min(420px, 92vw);
  border-radius: 6px;
  display: grid;
  gap: 0.75rem;
}
.dialog__row {
  display: grid;
  gap: 0.35rem;
}
.dialog__variables,
.dialog__declared {
  display: grid;
  gap: 0.5rem;
}
.dialog__variables summary {
  cursor: pointer;
}
.dialog__variable {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 0.35rem;
  align-items: center;
}
.dialog__variable input {
  min-width: 0;
}
.dialog__hint {
  margin: 0;
  font-size: 0.85rem;
  color: #55595f;
}
.dialog__error {
  grid-column: 1 / -1;
  margin: 0;
  color: #a12a2a;
  font-size: 0.85rem;
}
.dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
}
button {
  border: 1px solid #b7b7ba;
  background: white;
  padding: 0.4rem 0.75rem;
  cursor: pointer;
}
button.primary {
  background: var(--color-accent, #5980a6);
  color: white;
  border-color: transparent;
}
button:disabled {
  opacity: 0.6;
  cursor: default;
}
</style>
