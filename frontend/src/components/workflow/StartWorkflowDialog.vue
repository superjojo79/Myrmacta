<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { listAllWorkflowTypeConfigs } from '@/api/workflows'
import type { WorkflowTypeConfig } from '@/api/types'

const props = defineProps<{
  open: boolean
  defaultWorkflowKey: string
}>()

const emit = defineEmits<{
  close: []
  start: [processDefinitionKey: string]
}>()

const useOther = ref(false)
const selectedKey = ref(props.defaultWorkflowKey)
const configs = ref<WorkflowTypeConfig[]>([])
const loading = ref(false)

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
    }
  },
)

onMounted(async () => {
  loading.value = true
  try {
    configs.value = await listAllWorkflowTypeConfigs()
  } finally {
    loading.value = false
  }
})

function submit() {
  const key = useOther.value ? selectedKey.value : props.defaultWorkflowKey
  if (!key) return
  emit('start', key)
}
</script>

<template>
  <div v-if="open" class="dialog" role="dialog" aria-modal="true">
    <div class="dialog__panel">
      <h2>Démarrer un workflow</h2>
      <label class="dialog__row">
        <input v-model="useOther" type="checkbox" />
        Choisir une autre configuration
      </label>
      <p v-if="!useOther">Clé par défaut : <strong>{{ defaultWorkflowKey || '—' }}</strong></p>
      <label v-else class="dialog__row">
        Configuration
        <select v-model="selectedKey" :disabled="loading">
          <option v-for="cfg in configs" :key="cfg.workflowKey" :value="cfg.workflowKey">
            {{ cfg.label }} ({{ cfg.workflowKey }})
          </option>
        </select>
      </label>
      <div class="dialog__actions">
        <button type="button" @click="emit('close')">Annuler</button>
        <button type="button" class="primary" :disabled="!selectedKey && !defaultWorkflowKey" @click="submit">
          Démarrer
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
</style>
