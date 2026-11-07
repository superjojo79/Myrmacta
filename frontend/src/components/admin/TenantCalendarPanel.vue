<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { toProblemDetail } from '@/api/http'
import {
  getTenantCalendar,
  replaceTenantCalendar,
  type CalendarEntry,
  type CalendarSource,
  type TenantCalendar,
  type WeekendPattern,
} from '@/api/tenantCalendar'
import CalendarEntryForm from './CalendarEntryForm.vue'
import CalendarEntryList from './CalendarEntryList.vue'
import CalendarSourcesPanel from './CalendarSourcesPanel.vue'

/**
 * TODO §120 (D-CAL-ZONE, D-CAL-WEEKEND) et §121 (D-CAL-ENTRY, D-CAL-OVERRIDE, D-CAL-SOURCE) — fuseau, week-end,
 * saisies manuelles et sources du tenant. Les saisies s'enregistrent avec le fuseau et le week-end ; les sources
 * s'enregistrent d'elles-mêmes.
 */
const { t } = useI18n()
const zoneId = ref('UTC')
const weekend = ref<WeekendPattern>('SATURDAY_SUNDAY')
const entries = ref<CalendarEntry[]>([])
const sources = ref<CalendarSource[]>([])
const error = ref<string | null>(null)
const saved = ref(false)

function apply(calendar: TenantCalendar) {
  zoneId.value = calendar.zoneId
  weekend.value = calendar.weekend
  entries.value = calendar.entries
  sources.value = calendar.sources
}

onMounted(async () => {
  try {
    apply(await getTenantCalendar())
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('admin.types.calendar.errors.load')
  }
})

function add(entry: CalendarEntry) {
  saved.value = false
  entries.value = [...entries.value, entry]
}

function remove(index: number) {
  saved.value = false
  entries.value = entries.value.filter((_, i) => i !== index)
}

function sourcesChanged(next: CalendarSource[]) {
  saved.value = false
  sources.value = next
}

async function save() {
  error.value = null
  saved.value = false
  try {
    apply(await replaceTenantCalendar({ zoneId: zoneId.value.trim(), weekend: weekend.value, entries: entries.value }))
    saved.value = true
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('admin.types.calendar.errors.save')
  }
}
</script>

<template>
  <section class="retention-block" aria-labelledby="calendar-heading" data-testid="tenant-calendar">
    <h2 id="calendar-heading">{{ t('admin.types.calendar.title') }}</h2>
    <p class="hint">{{ t('admin.types.calendar.hint') }}</p>
    <p v-if="error" class="error" role="alert" data-testid="calendar-error">{{ error }}</p>
    <form class="admin-form" @submit.prevent="save">
      <label>
        {{ t('admin.types.calendar.zone') }}
        <input v-model="zoneId" required maxlength="64" data-testid="calendar-zone" />
      </label>
      <label>
        {{ t('admin.types.calendar.weekend') }}
        <select v-model="weekend" data-testid="calendar-weekend">
          <option value="SATURDAY_SUNDAY">{{ t('admin.types.calendar.saturdaySunday') }}</option>
          <option value="FRIDAY_SATURDAY">{{ t('admin.types.calendar.fridaySaturday') }}</option>
          <option value="SUNDAY_ONLY">{{ t('admin.types.calendar.sundayOnly') }}</option>
        </select>
      </label>
      <button type="submit" data-testid="calendar-save">{{ t('admin.types.calendar.save') }}</button>
      <span v-if="saved" class="hint" data-testid="calendar-saved">{{ t('admin.types.calendar.saved') }}</span>
    </form>
    <h3>{{ t('admin.types.calendar.entries.title') }}</h3>
    <CalendarEntryForm @add="add" />
    <CalendarEntryList :manual="entries" :sources="sources" @remove="remove" />
    <CalendarSourcesPanel :sources="sources" @changed="sourcesChanged" />
  </section>
</template>
