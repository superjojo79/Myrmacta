<script setup lang="ts">
import { DOSSIER_STATUSES, type DossierStatus } from '@/api/types'

defineProps<{
  modelValue: DossierStatus
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [DossierStatus]
}>()
</script>

<template>
  <label class="status-select">
    <span>Statut</span>
    <select
      :value="modelValue"
      :disabled="disabled"
      @change="emit('update:modelValue', ($event.target as HTMLSelectElement).value as DossierStatus)"
    >
      <option v-for="status in DOSSIER_STATUSES" :key="status" :value="status">{{ status }}</option>
    </select>
  </label>
</template>

<style scoped>
.status-select {
  display: inline-flex;
  flex-direction: column;
  gap: 0.25rem;
}
.status-select select {
  padding: 0.4rem 0.55rem;
  border: 1px solid #c5c5c8;
  border-radius: 4px;
}
</style>
