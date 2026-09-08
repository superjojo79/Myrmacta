<script setup lang="ts">
import type { ConfigField } from '@/api/admin'
import type { FormValue, FormValues } from './connectorConfig'

const props = defineProps<{
  schema: ConfigField[]
}>()

const values = defineModel<FormValues>({ required: true })

function inputType(type: ConfigField['type']): string {
  switch (type) {
    case 'PASSWORD':
      return 'password'
    case 'URL':
      return 'url'
    case 'INTEGER':
      return 'number'
    default:
      return 'text'
  }
}

function setField(name: string, value: FormValue) {
  values.value = { ...values.value, [name]: value }
}

function isRequired(field: ConfigField): boolean {
  return field.required && field.type !== 'BOOLEAN'
}
</script>

<template>
  <div class="connector-config-form" data-testid="connector-config-form">
    <label v-for="field in props.schema" :key="field.name" class="connector-config-form__field">
      <span>
        {{ field.label }}<em v-if="field.required"> *</em>
      </span>
      <select
        v-if="field.type === 'SELECT'"
        :data-testid="`connector-config-field-${field.name}`"
        :value="String(values[field.name] ?? '')"
        :required="isRequired(field)"
        @change="setField(field.name, ($event.target as HTMLSelectElement).value)"
      >
        <option v-if="!field.required" value="">—</option>
        <option v-for="option in field.options ?? []" :key="option" :value="option">
          {{ option }}
        </option>
      </select>
      <input
        v-else-if="field.type === 'BOOLEAN'"
        :data-testid="`connector-config-field-${field.name}`"
        type="checkbox"
        :checked="Boolean(values[field.name])"
        @change="setField(field.name, ($event.target as HTMLInputElement).checked)"
      />
      <input
        v-else
        :data-testid="`connector-config-field-${field.name}`"
        :type="inputType(field.type)"
        :value="values[field.name] == null ? '' : String(values[field.name])"
        :required="isRequired(field)"
        :autocomplete="field.type === 'PASSWORD' ? 'new-password' : undefined"
        @input="setField(field.name, ($event.target as HTMLInputElement).value)"
      />
      <small
        v-if="field.helpText"
        :data-testid="`connector-config-help-${field.name}`"
        class="connector-config-form__help"
      >
        {{ field.helpText }}
      </small>
    </label>
  </div>
</template>

<style scoped>
.connector-config-form {
  display: grid;
  gap: 0.5rem;
}

.connector-config-form__field {
  display: grid;
  gap: 0.25rem;
}

.connector-config-form__field input,
.connector-config-form__field select {
  padding: 0.45rem 0.55rem;
  border: 1px solid #c5c5c8;
  border-radius: 4px;
}

.connector-config-form__help {
  font-size: 0.85rem;
  opacity: 0.75;
}
</style>
