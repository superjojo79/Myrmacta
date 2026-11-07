<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { toProblemDetail } from '@/api/http'
import {
  addBelgianFederalHolidays,
  createSource,
  createUrlSource,
  deleteSource,
  previewNewSource,
  previewNewUrlSource,
  previewReload,
  previewUrlReload,
  reloadSource,
  reloadUrlSource,
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
/** `file` : un fichier .ics ; `url` : un agenda en ligne (l'URL est un secret : jamais réaffichée après l'envoi). */
const mode = ref<'file' | 'url'>('file')
const newUrl = ref('')
/** Source par URL dont on saisit l'URL (remplacement, ou URL à ressaisir après une promotion). */
const editingUrl = ref<string | null>(null)
const editedUrl = ref('')

const reloadInput = ref<HTMLInputElement | null>(null)
let reloadTarget: CalendarSource | null = null

/**
 * Import en attente de confirmation : l'aperçu, d'où vient le contenu (un fichier, ou une URL ; URL nulle : celle que le
 * serveur conserve) et la source rechargée (nulle : création).
 */
type Origin = { file: File } | { url: string | null }
const pending = ref<{ preview: SourcePreview; origin: Origin; name: string; sourceId: string | null } | null>(null)
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
  const name = newName.value.trim()
  const url = newUrl.value.trim()
  if (!name || (mode.value === 'file' ? !newFile.value : !url)) {
    error.value = t(mode.value === 'file' ? 'admin.types.calendar.errors.nameAndFile' : 'admin.types.calendar.errors.nameAndUrl')
    return
  }
  busy.value = true
  try {
    if (mode.value === 'file' && newFile.value) {
      const file = newFile.value
      pending.value = { preview: await previewNewSource(name, file), origin: { file }, name, sourceId: null }
    } else {
      pending.value = { preview: await previewNewUrlSource(name, url), origin: { url }, name, sourceId: null }
    }
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
    pending.value = { preview: await previewReload(source.id, file), origin: { file }, name: source.name, sourceId: source.id }
    pendingError.value = null
  } catch (err) {
    error.value = message(err, t('admin.types.calendar.errors.preview'))
  } finally {
    busy.value = false
  }
}

/** Recharger une source par URL : l'URL conservée, ou celle qu'on vient de saisir (remplacement). */
async function previewUrlSourceReload(source: CalendarSource, url: string | null) {
  error.value = null
  busy.value = true
  try {
    pending.value = { preview: await previewUrlReload(source.id, url ?? undefined), origin: { url }, name: source.name, sourceId: source.id }
    pendingError.value = null
    editingUrl.value = null
    editedUrl.value = ''
  } catch (err) {
    error.value = message(err, t('admin.types.calendar.errors.preview'))
  } finally {
    busy.value = false
  }
}

function askUrlReload(source: CalendarSource) {
  // Sans URL conservée (source importée par promotion), il faut la saisir ; sinon on relit celle du serveur.
  if (!source.maskedUrl) {
    startEditingUrl(source)
    return
  }
  void previewUrlSourceReload(source, null)
}

function startEditingUrl(source: CalendarSource) {
  editingUrl.value = source.id
  editedUrl.value = ''
}

function submitEditedUrl(source: CalendarSource) {
  const url = editedUrl.value.trim()
  if (!url) {
    error.value = t('admin.types.calendar.errors.urlRequired')
    return
  }
  void previewUrlSourceReload(source, url)
}

async function confirmImport() {
  const current = pending.value
  if (!current) return
  pendingError.value = null
  busy.value = true
  try {
    const sha = current.preview.sha256
    let calendar: TenantCalendar
    if ('file' in current.origin) {
      calendar = current.sourceId
        ? await reloadSource(current.sourceId, current.origin.file, sha)
        : await createSource(current.name, current.origin.file, sha)
    } else {
      calendar = current.sourceId
        ? await reloadUrlSource(current.sourceId, sha, current.origin.url ?? undefined)
        : await createUrlSource(current.name, current.origin.url ?? '', sha)
    }
    pending.value = null
    if (!current.sourceId) {
      newName.value = ''
      newFile.value = null
      newUrl.value = ''
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
        <span v-if="source.kind === 'ICS_URL'" class="hint" data-testid="source-url">
          {{ source.maskedUrl ?? t('admin.types.calendar.sources.urlMissing') }}
        </span>
        <span v-if="source.lastError" class="error" data-testid="source-error">{{ source.lastError }}</span>
        <button v-if="source.kind === 'ICS_FILE'" type="button" :disabled="busy" data-testid="source-reload" @click="askReload(source)">
          {{ t('admin.types.calendar.sources.reload') }}
        </button>
        <template v-if="source.kind === 'ICS_URL'">
          <button type="button" :disabled="busy" data-testid="source-reload" @click="askUrlReload(source)">
            {{ t('admin.types.calendar.sources.reload') }}
          </button>
          <button type="button" :disabled="busy" data-testid="source-url-change" @click="startEditingUrl(source)">
            {{ t('admin.types.calendar.sources.changeUrl') }}
          </button>
          <span v-if="editingUrl === source.id" class="url-edit">
            <label>
              {{ t('admin.types.calendar.sources.url') }}
              <input v-model="editedUrl" type="url" autocomplete="off" spellcheck="false" data-testid="source-url-edit" />
            </label>
            <button type="button" :disabled="busy" data-testid="source-url-edit-preview" @click="submitEditedUrl(source)">
              {{ t('admin.types.calendar.sources.preview') }}
            </button>
            <button type="button" :disabled="busy" data-testid="source-url-edit-cancel" @click="editingUrl = null">
              {{ t('admin.types.calendar.import.cancel') }}
            </button>
          </span>
        </template>
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
        {{ t('admin.types.calendar.sources.mode') }}
        <select v-model="mode" data-testid="source-mode">
          <option value="file">{{ t('admin.types.calendar.sources.kind.ICS_FILE') }}</option>
          <option value="url">{{ t('admin.types.calendar.sources.kind.ICS_URL') }}</option>
        </select>
      </label>
      <label v-if="mode === 'file'">
        {{ t('admin.types.calendar.sources.file') }}
        <input ref="fileInput" type="file" accept=".ics,text/calendar" data-testid="source-file" @change="onNewFile" />
      </label>
      <label v-else>
        {{ t('admin.types.calendar.sources.url') }}
        <input v-model="newUrl" type="url" autocomplete="off" spellcheck="false" data-testid="source-url-input" />
        <span class="hint">{{ t('admin.types.calendar.sources.urlHint') }}</span>
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
