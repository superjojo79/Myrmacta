<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { toProblemDetail } from '@/api/http'
import {
  addBelgianFederalHolidays,
  getTenantCalendar,
  replaceTenantCalendar,
  type TenantCalendar,
  type WeekendPattern,
} from '@/api/tenantCalendar'

/** TODO §120 (D-CAL-ZONE, D-CAL-WEEKEND, D-CAL-HOLIDAYS) — fuseau, week-end et jours fériés du tenant. */
const { t } = useI18n()
const zoneId = ref('UTC')
const weekend = ref<WeekendPattern>('SATURDAY_SUNDAY')
const holidaysText = ref('')
const error = ref<string | null>(null)
const saved = ref(false)

function apply(calendar: TenantCalendar) {
  zoneId.value = calendar.zoneId
  weekend.value = calendar.weekend
  holidaysText.value = calendar.holidays.join('\n')
}

function dates(): string[] {
  return holidaysText.value
    .split(/[\s,]+/)
    .map((value) => value.trim())
    .filter((value) => value.length > 0)
}

onMounted(async () => {
  try {
    apply(await getTenantCalendar())
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('admin.types.calendar.errors.load')
  }
})

async function save() {
  error.value = null
  saved.value = false
  try {
    apply(await replaceTenantCalendar({ zoneId: zoneId.value.trim(), weekend: weekend.value, holidays: dates() }))
    saved.value = true
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('admin.types.calendar.errors.save')
  }
}

async function belgian() {
  error.value = null
  saved.value = false
  try {
    apply(await addBelgianFederalHolidays())
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
      <label>
        {{ t('admin.types.calendar.holidays') }}
        <textarea v-model="holidaysText" rows="4" data-testid="calendar-holidays" />
      </label>
      <button type="submit" data-testid="calendar-save">{{ t('admin.types.calendar.save') }}</button>
      <button type="button" data-testid="calendar-belgian" @click="belgian">{{ t('admin.types.calendar.belgian') }}</button>
      <span v-if="saved" class="hint" data-testid="calendar-saved">{{ t('admin.types.calendar.saved') }}</span>
    </form>
  </section>
</template>
