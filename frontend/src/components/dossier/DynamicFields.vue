<script setup lang="ts">
import { computed, watch } from 'vue'
import type { FieldDefinition } from '@/api/types'
import SensitiveValue from './SensitiveValue.vue'

const props = withDefaults(
  defineProps<{
    definitions: FieldDefinition[]
    modelValue: Record<string, unknown>
    readonly?: boolean
    applyDefaults?: boolean
  }>(),
  { applyDefaults: true },
)

const emit = defineEmits<{
  'update:modelValue': [Record<string, unknown>]
}>()

const groups = computed(() => {
  const map = new Map<string, FieldDefinition[]>()
  for (const def of props.definitions) {
    const key = def.group || 'Général'
    const list = map.get(key) ?? []
    list.push(def)
    map.set(key, list)
  }
  return [...map.entries()]
})

watch(
  () => props.definitions,
  (defs) => {
    if (props.readonly || !props.applyDefaults) return
    const next = { ...props.modelValue }
    let changed = false
    for (const def of defs) {
      if (next[def.name] === undefined && def.defaultValue != null) {
        next[def.name] = def.defaultValue
        changed = true
      }
    }
    if (changed) emit('update:modelValue', next)
  },
  { immediate: true },
)

function setField(name: string, value: unknown) {
  emit('update:modelValue', { ...props.modelValue, [name]: value })
}
</script>

<template>
  <div class="dynamic-fields">
    <section v-for="[group, fields] in groups" :key="group" class="dynamic-fields__group">
      <h3>{{ group }}</h3>
      <label v-for="field in fields" :key="field.name" class="dynamic-fields__field">
        <span>{{ field.label }}<em v-if="field.required"> *</em></span>
        <template v-if="readonly">
          <SensitiveValue :value="modelValue[field.name]" :sensitive="!!field.sensitive" />
        </template>
        <template v-else-if="field.type === 'BOOLEAN'">
          <input
            type="checkbox"
            :checked="Boolean(modelValue[field.name])"
            @change="setField(field.name, ($event.target as HTMLInputElement).checked)"
          />
        </template>
        <template v-else-if="field.type === 'SELECT'">
          <select
            :value="String(modelValue[field.name] ?? '')"
            @change="setField(field.name, ($event.target as HTMLSelectElement).value)"
          >
            <option value="">—</option>
            <option v-for="opt in field.options ?? []" :key="opt" :value="opt">{{ opt }}</option>
          </select>
        </template>
        <template v-else>
          <input
            :type="field.type === 'NUMBER' ? 'number' : field.type === 'DATE' ? 'date' : 'text'"
            :value="modelValue[field.name] == null ? '' : String(modelValue[field.name])"
            @input="setField(field.name, ($event.target as HTMLInputElement).value)"
          />
        </template>
      </label>
    </section>
  </div>
</template>

<style scoped>
.dynamic-fields__group {
  margin-bottom: 1rem;
}
.dynamic-fields__field {
  display: grid;
  gap: 0.25rem;
  margin-bottom: 0.75rem;
}
.dynamic-fields__field input,
.dynamic-fields__field select {
  padding: 0.45rem 0.55rem;
  border: 1px solid #c5c5c8;
  border-radius: 4px;
}
</style>
