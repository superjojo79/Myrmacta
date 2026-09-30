<script setup lang="ts">
import { computed, watch } from 'vue'
import type { FieldDefinition } from '@/api/types'
import SensitiveValue from './SensitiveValue.vue'
import DynamicFieldInput from './DynamicFieldInput.vue'
import { formatFieldValue, isStructured } from '@/dossier/fieldValues'

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
      <!--
        Un champ structuré (montant, référence, groupe) porte plusieurs contrôles, voire un tableau : dans un <label>, un clic
        sur le libellé activerait le premier contrôle (ajout de ligne, case à cocher) et le HTML serait invalide.
      -->
      <component
        :is="isStructured(field.type) ? 'div' : 'label'"
        v-for="field in fields"
        :key="field.name"
        class="dynamic-fields__field"
        :role="isStructured(field.type) ? 'group' : undefined"
        :aria-label="isStructured(field.type) ? field.label : undefined"
      >
        <span>{{ field.label }}<em v-if="field.required"> *</em></span>
        <template v-if="readonly">
          <SensitiveValue :value="formatFieldValue(field, modelValue[field.name])" :sensitive="!!field.sensitive">
            <!-- TODO §76 : les lignes d'un groupe se lisent en tableau (visible seulement si la valeur est révélée). -->
            <table
              v-if="field.type === 'REPEATABLE_GROUP' && Array.isArray(modelValue[field.name]) && (modelValue[field.name] as unknown[]).length > 0"
              class="dynamic-fields__rows"
            >
              <thead>
                <tr>
                  <th v-for="sub in field.fields ?? []" :key="sub.name">{{ sub.label }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, index) in modelValue[field.name] as Record<string, unknown>[]" :key="index">
                  <td v-for="sub in field.fields ?? []" :key="sub.name">{{ formatFieldValue(sub, row[sub.name]) }}</td>
                </tr>
              </tbody>
            </table>
            <template v-else>{{ formatFieldValue(field, modelValue[field.name]) }}</template>
          </SensitiveValue>
        </template>
        <DynamicFieldInput
          v-else
          :field="field"
          :model-value="modelValue[field.name]"
          @update:model-value="(value) => setField(field.name, value)"
        />
      </component>
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
.dynamic-fields__rows {
  border-collapse: collapse;
}
.dynamic-fields__rows th,
.dynamic-fields__rows td {
  padding: 0.2rem 0.5rem;
  text-align: left;
  border-bottom: 1px solid #e3e3e6;
}
.dynamic-fields__field input,
.dynamic-fields__field select {
  padding: 0.45rem 0.55rem;
  border: 1px solid #c5c5c8;
  border-radius: 4px;
}
</style>
