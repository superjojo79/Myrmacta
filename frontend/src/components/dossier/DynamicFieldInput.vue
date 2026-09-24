<script setup lang="ts">
import type { FieldDefinition } from '@/api/types'

// TODO §68 (portail-externe) : saisie d'un champ, extraite de DynamicFields.vue pour être
// réutilisable par la page publique du portail sans entraîner SensitiveValue.vue (qui dépend du
// store d'authentification du SPA interne). Composant pur : aucune dépendance store/API/routeur.
defineProps<{
  field: Pick<FieldDefinition, 'name' | 'label' | 'type' | 'required' | 'options'>
  modelValue: unknown
  inputId?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [unknown]
}>()
</script>

<template>
  <input
    v-if="field.type === 'BOOLEAN'"
    :id="inputId"
    type="checkbox"
    :checked="Boolean(modelValue)"
    @change="emit('update:modelValue', ($event.target as HTMLInputElement).checked)"
  />
  <select
    v-else-if="field.type === 'SELECT'"
    :id="inputId"
    :value="String(modelValue ?? '')"
    @change="emit('update:modelValue', ($event.target as HTMLSelectElement).value)"
  >
    <option value="">—</option>
    <option v-for="opt in field.options ?? []" :key="opt" :value="opt">{{ opt }}</option>
  </select>
  <input
    v-else
    :id="inputId"
    :type="field.type === 'NUMBER' ? 'number' : field.type === 'DATE' ? 'date' : 'text'"
    :value="modelValue == null ? '' : String(modelValue)"
    @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
  />
</template>
