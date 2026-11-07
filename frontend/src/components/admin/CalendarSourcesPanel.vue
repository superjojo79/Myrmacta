<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { toProblemDetail } from '@/api/http'
import {
  addBelgianFederalHolidays,
  createSource,
  deleteSource,
  previewNewSource,
  previewReload,
  reloadSource,
  type CalendarSource,
  type SourcePreview,
  type TenantCalendar,
} from '@/api/tenantCalendar'
import CalendarImportPreview from './CalendarImportPreview.vue'

/**
 * TODO §121 (D-CAL-SOURCE, D-CAL-ICS, D-CAL-ICS-PREVIEW, D-CAL-BE-SOURCE) — sources du calendrier : ajout d'un fichier
 * ICS (aperçu, puis confirmation), rechargement d'un fichier, suppression ; les fériés fédéraux belges sont l'une
 * d'elles. Chaque action enregistre tout de suite et rend les sources à jour.
 */
const props = defineProps<{ sources: CalendarSource[] }>()
const emit = defineEmits<{ changed: [sources: CalendarSource[]] }>()

const { t } = useI18n()
const error = ref<string | null>(null)
const busy = ref(false)

const newName = ref('')
const newFile = ref<File | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

const reloadInput = ref<HTMLInputElement | null>(null)
let reloadTarget: CalendarSource | null = null

/** Import en attente de confirmation : l'aperçu, le fichier qui l'a produit, et la source rechargée (nulle : création). */
const pending = ref<{ preview: SourcePreview; file: File; name: string; sourceId: string | null } | null>(null)
const pendingError = ref<string | null>(null)
const confirmingDelete = ref<string | null>(null)

function message(err: unknown, fallback: string): string {
  return toProblemDetail(err).detail ?? fallback
}

function done(calendar: TenantCalendar) {
  emit('changed', calendar.sources)
}

async function belgian() {
  error.value = null
  busy.value = true
  try {
    done(await addBelgianFederalHolidays())
  } catch (err) {
    error.value = message(err, t('admin.types.calendar.errors.save'))
  } finally {
    busy.value = false
  }
}

function onNewFile(event: Event) {
  newFile.value = (event.target as HTMLInputElement).files?.[0] ?? null
}

async function previewCreate() {
  error.value = null
  if (!newName.value.trim() || !newFile.value) {
    error.value = t('admin.types.calendar.errors.nameAndFile')
    return
  }
  busy.value = true
  try {
    const file = newFile.value
    pending.value = { preview: await previewNewSource(newName.value.trim(), file), file, name: newName.value.trim(), sourceId: null }
    pendingError.value = null
  } catch (err) {
    error.value = message(err, t('admin.types.calendar.errors.preview'))
  } finally {
    busy.value = false
  }
}

function askReload(source: CalendarSource) {
  reloadTarget = source
  if (reloadInput.value) {
    reloadInput.value.value = ''
    reloadInput.value.click()
  }
}

async function onReloadFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  const source = reloadTarget
  if (!file || !source) return
  error.value = null
  busy.value = true
  try {
    pending.value = { preview: await previewReload(source.id, file), file, name: source.name, sourceId: source.id }
    pendingError.value = null
  } catch (err) {
    error.value = message(err, t('admin.types.calendar.errors.preview'))
  } finally {
    busy.value = false
  }
}

async function confirmImport() {
  const current = pending.value
  if (!current) return
  pendingError.value = null
  busy.value = true
  try {
    const calendar = current.sourceId
      ? await reloadSource(current.sourceId, current.file, current.preview.sha256)
      : await createSource(current.name, current.file, current.preview.sha256)
    pending.value = null
    if (!current.sourceId) {
      newName.value = ''
      newFile.value = null
      if (fileInput.value) fileInput.value.value = ''
    }
    done(calendar)
  } catch (err) {
    // Un 409 à la confirmation : le contenu ou le nom a changé depuis l'aperçu.
    pendingError.value = toProblemDetail(err).status === 409 ? t('admin.types.calendar.import.changed') : message(err, t('admin.types.calendar.errors.save'))
  } finally {
    busy.value = false
  }
}

function cancelImport() {
  pending.value = null
  pendingError.value = null
}

async function remove(source: CalendarSource) {
  error.value = null
  busy.value = true
  try {
    await deleteSource(source.id)
    confirmingDelete.value = null
    emit('changed', props.sources.filter((candidate) => candidate.id !== source.id))
  } catch (err) {
    error.value = message(err, t('admin.types.calendar.errors.save'))
  } finally {
    busy.value = false
  }
}

function loaded(source: CalendarSource): string {
  if (!source.loadedAt) return ''
  return t('admin.types.calendar.sources.loaded', {
    date: new Date(source.loadedAt).toLocaleDateString(),
    by: source.loadedBy ?? '—',
  })
}
</script>

<template>
  <div data-testid="calendar-sources">
    <h3>{{ t('admin.types.calendar.sources.title') }}</h3>
    <p v-if="error" class="error" role="alert" data-testid="sources-error">{{ error }}</p>
    <p v-if="sources.length === 0" class="hint" data-testid="calendar-sources-empty">{{ t('admin.types.calendar.sources.empty') }}</p>
    <ul v-else class="source-list">
      <li v-for="source in sources" :key="source.id" data-testid="calendar-source">
        <strong>{{ source.name }}</strong>
        <span class="hint">
          {{ t(`admin.types.calendar.sources.kind.${source.kind}`) }} ·
          {{ t('admin.types.calendar.sources.count', { count: source.entryCount }) }} · {{ loaded(source) }}
        </span>
        <span v-if="source.lastError" class="error" data-testid="source-error">{{ source.lastError }}</span>
        <button v-if="source.kind === 'ICS_FILE'" type="button" :disabled="busy" data-testid="source-reload" @click="askReload(source)">
          {{ t('admin.types.calendar.sources.reload') }}
        </button>
        <template v-if="confirmingDelete === source.id">
          <span class="hint">{{ t('admin.types.calendar.sources.deleteConfirm', { name: source.name }) }}</span>
          <button type="button" :disabled="busy" data-testid="source-delete-confirm" @click="remove(source)">
            {{ t('admin.types.calendar.sources.delete') }}
          </button>
          <button type="button" :disabled="busy" data-testid="source-delete-cancel" @click="confirmingDelete = null">
            {{ t('admin.types.calendar.import.cancel') }}
          </button>
        </template>
        <button v-else type="button" :disabled="busy" data-testid="source-delete" @click="confirmingDelete = source.id">
          {{ t('admin.types.calendar.sources.delete') }}
        </button>
      </li>
    </ul>
    <input ref="reloadInput" type="file" accept=".ics,text/calendar" hidden data-testid="source-reload-file" @change="onReloadFile" />

    <fieldset class="admin-form" data-testid="source-add">
      <legend>{{ t('admin.types.calendar.sources.add') }}</legend>
      <label>
        {{ t('admin.types.calendar.sources.name') }}
        <input v-model="newName" maxlength="80" data-testid="source-name" />
      </label>
      <label>
        {{ t('admin.types.calendar.sources.file') }}
        <input ref="fileInput" type="file" accept=".ics,text/calendar" data-testid="source-file" @change="onNewFile" />
      </label>
      <button type="button" :disabled="busy" data-testid="source-preview" @click="previewCreate">
        {{ t('admin.types.calendar.sources.preview') }}
      </button>
    </fieldset>
    <button type="button" :disabled="busy" data-testid="calendar-belgian" @click="belgian">
      {{ t('admin.types.calendar.belgian') }}
    </button>

    <CalendarImportPreview
      v-if="pending"
      :preview="pending.preview"
      :busy="busy"
      :error="pendingError"
      @confirm="confirmImport"
      @cancel="cancelImport"
    />
  </div>
</template>
