<script setup lang="ts">
import { computed } from 'vue'
import DynamicFieldInput from '@/components/dossier/DynamicFieldInput.vue'
import type { PortalField } from './api'

const props = defineProps<{
  fields: PortalField[]
  modelValue: Record<string, unknown>
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [Record<string, unknown>]
}>()

const groups = computed(() => {
  const map = new Map<string, PortalField[]>()
  for (const field of props.fields) {
    const key = field.group || ''
    map.set(key, [...(map.get(key) ?? []), field])
  }
  return [...map.entries()]
})

function setField(name: string, value: unknown) {
  emit('update:modelValue', { ...props.modelValue, [name]: value })
}
</script>

<template>
  <fieldset class="portal-form" :disabled="disabled">
    <section v-for="[group, groupFields] in groups" :key="group" class="portal-form__group">
      <h3 v-if="group">{{ group }}</h3>
      <div
        v-for="field in groupFields"
        :key="field.name"
        class="portal-form__field"
        :class="{ 'portal-form__field--half': field.width === 'HALF' }"
      >
        <label :for="`field-${field.name}`">
          {{ field.label }}<span v-if="field.required" aria-hidden="true"> *</span>
        </label>
        <DynamicFieldInput
          :input-id="`field-${field.name}`"
          :field="field"
          :model-value="modelValue[field.name]"
          @update:model-value="(value) => setField(field.name, value)"
        />
        <small v-if="field.helpText" class="portal-form__help">{{ field.helpText }}</small>
      </div>
    </section>
  </fieldset>
</template>
