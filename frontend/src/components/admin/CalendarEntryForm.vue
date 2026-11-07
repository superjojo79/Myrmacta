<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { CalendarEntry, EntryNature } from '@/api/tenantCalendar'

/** TODO §121 (D-CAL-ENTRY) — saisie d'une date ou d'une période : Du, Au facultatif, nature, libellé. */
const emit = defineEmits<{ add: [entry: CalendarEntry] }>()

const MAX_DAYS = 366
const MAX_LABEL = 120

const { t } = useI18n()
const start = ref('')
const end = ref('')
const nature = ref<EntryNature>('CLOSED')
const label = ref('')
const error = ref<string | null>(null)

const endMin = computed(() => start.value || undefined)

function daysBetween(from: string, to: string): number {
  return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000) + 1
}

function validate(): string | null {
  if (!start.value) return t('admin.types.calendar.errors.startRequired')
  const last = end.value || start.value
  if (last < start.value) return t('admin.types.calendar.errors.endBeforeStart')
  if (daysBetween(start.value, last) > MAX_DAYS) return t('admin.types.calendar.errors.tooLong', { max: MAX_DAYS })
  if (label.value.trim().length > MAX_LABEL) return t('admin.types.calendar.errors.labelTooLong', { max: MAX_LABEL })
  return null
}

function submit() {
  error.value = validate()
  if (error.value) return
  emit('add', {
    start: start.value,
    end: end.value || start.value,
    nature: nature.value,
    label: label.value.trim() || null,
  })
  start.value = ''
  end.value = ''
  label.value = ''
}
</script>

<template>
  <fieldset class="admin-form" data-testid="calendar-entry-form">
    <legend>{{ t('admin.types.calendar.entries.add') }}</legend>
    <p v-if="error" class="error" role="alert" data-testid="entry-error">{{ error }}</p>
    <label>
      {{ t('admin.types.calendar.entries.start') }}
      <input v-model="start" type="date" required data-testid="entry-start" />
    </label>
    <label>
      {{ t('admin.types.calendar.entries.end') }}
      <input v-model="end" type="date" :min="endMin" data-testid="entry-end" />
    </label>
    <label>
      {{ t('admin.types.calendar.entries.nature') }}
      <select v-model="nature" data-testid="entry-nature">
        <option value="CLOSED">{{ t('admin.types.calendar.entries.closed') }}</option>
        <option value="OPEN">{{ t('admin.types.calendar.entries.open') }}</option>
      </select>
    </label>
    <label>
      {{ t('admin.types.calendar.entries.label') }}
      <input v-model="label" :maxlength="MAX_LABEL" data-testid="entry-label" />
    </label>
    <button type="button" data-testid="entry-add" @click="submit">{{ t('admin.types.calendar.entries.addButton') }}</button>
  </fieldset>
</template>
