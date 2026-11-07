<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DayRange, IgnoreReason, SourcePreview } from '@/api/tenantCalendar'

/**
 * TODO §121 (D-CAL-ICS-PREVIEW) — aperçu d'un import ICS : jours ajoutés, retirés, masqués par un jour ouvré forcé,
 * événements écartés. Boîte modale : le focus y reste (Tab), Échap l'annule, le focus revient à l'appelant.
 */
const props = defineProps<{ preview: SourcePreview; busy?: boolean; error?: string | null }>()
const emit = defineEmits<{ confirm: []; cancel: [] }>()

const MAX_SHOWN = 30

const { t } = useI18n()
const dialog = ref<HTMLElement | null>(null)
let opener: HTMLElement | null = null

const ignoredReasons = computed(() => (Object.entries(props.preview.ignored) as [IgnoreReason, number][]).filter(([, count]) => count > 0))
const nothingChanges = computed(() => props.preview.added.length === 0 && props.preview.removed.length === 0)

function label(range: DayRange): string {
  return range.start === range.end ? range.start : `${range.start} → ${range.end}`
}

function shown(ranges: DayRange[]): DayRange[] {
  return ranges.slice(0, MAX_SHOWN)
}

function focusable(): HTMLElement[] {
  if (!dialog.value) return []
  return [...dialog.value.querySelectorAll<HTMLElement>('button:not([disabled]), [href], input, select, textarea, [tabindex]:not([tabindex="-1"])')]
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.stopPropagation()
    emit('cancel')
    return
  }
  if (event.key !== 'Tab') return
  const items = focusable()
  if (items.length === 0) {
    event.preventDefault()
    return
  }
  const first = items[0]
  const last = items[items.length - 1]
  const active = document.activeElement
  if (event.shiftKey && (active === first || active === dialog.value)) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && active === last) {
    event.preventDefault()
    first.focus()
  }
}

onMounted(async () => {
  opener = document.activeElement instanceof HTMLElement ? document.activeElement : null
  await nextTick()
  dialog.value?.focus()
})

onBeforeUnmount(() => opener?.focus())
</script>

<template>
  <div
    ref="dialog"
    class="dialog"
    role="dialog"
    aria-modal="true"
    aria-labelledby="calendar-import-title"
    tabindex="-1"
    data-testid="calendar-import-preview"
    @keydown="onKeydown"
  >
    <h3 id="calendar-import-title">{{ t('admin.types.calendar.import.title', { name: preview.name }) }}</h3>
    <p class="hint" data-testid="import-summary">{{ t('admin.types.calendar.import.summary', { count: preview.periodCount }) }}</p>
    <p v-if="error" class="error" role="alert" data-testid="import-error">{{ error }}</p>
    <p v-if="nothingChanges" class="hint" data-testid="import-nothing">{{ t('admin.types.calendar.import.nothing') }}</p>
    <section v-if="preview.added.length > 0" data-testid="import-added">
      <h4>{{ t('admin.types.calendar.import.added', { count: preview.added.length }) }}</h4>
      <ul>
        <li v-for="range in shown(preview.added)" :key="range.start + range.end">{{ label(range) }}</li>
        <li v-if="preview.added.length > MAX_SHOWN" class="hint">{{ t('admin.types.calendar.import.more', { count: preview.added.length - MAX_SHOWN }) }}</li>
      </ul>
    </section>
    <section v-if="preview.removed.length > 0" data-testid="import-removed">
      <h4>{{ t('admin.types.calendar.import.removed', { count: preview.removed.length }) }}</h4>
      <ul>
        <li v-for="range in shown(preview.removed)" :key="range.start + range.end">{{ label(range) }}</li>
        <li v-if="preview.removed.length > MAX_SHOWN" class="hint">{{ t('admin.types.calendar.import.more', { count: preview.removed.length - MAX_SHOWN }) }}</li>
      </ul>
    </section>
    <section v-if="preview.masked.length > 0" data-testid="import-masked">
      <h4>{{ t('admin.types.calendar.import.masked', { count: preview.masked.length }) }}</h4>
      <p class="hint">{{ t('admin.types.calendar.import.maskedHint') }}</p>
      <ul>
        <li v-for="range in shown(preview.masked)" :key="range.start + range.end">{{ label(range) }}</li>
      </ul>
    </section>
    <section v-if="ignoredReasons.length > 0" data-testid="import-ignored">
      <h4>{{ t('admin.types.calendar.import.ignored') }}</h4>
      <ul>
        <li v-for="[reason, count] in ignoredReasons" :key="reason">{{ t(`admin.types.calendar.import.reason.${reason}`, { count }) }}</li>
      </ul>
    </section>
    <div class="actions">
      <button type="button" class="primary" :disabled="busy" data-testid="import-confirm" @click="emit('confirm')">
        {{ t('admin.types.calendar.import.confirm') }}
      </button>
      <button type="button" :disabled="busy" data-testid="import-cancel" @click="emit('cancel')">
        {{ t('admin.types.calendar.import.cancel') }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.dialog {
  border: 1px solid #ccc;
  padding: 1rem;
  margin-top: 1rem;
  background: white;
}
.actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.75rem;
}
.error {
  color: #c92a2a;
}
button.primary {
  background: var(--color-accent);
  color: white;
  border-color: transparent;
}
</style>
