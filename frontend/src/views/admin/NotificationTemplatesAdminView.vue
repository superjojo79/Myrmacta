<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ref, watch } from 'vue'
import {
  activateNotificationTemplate,
  archiveNotificationTemplate,
  createNotificationTemplate,
  getNotificationTemplate,
  listNotificationTemplates,
  newNotificationTemplateVersion,
  previewNotificationTemplate,
  updateNotificationTemplate,
  type NotificationTemplate,
  type RenderedNotification,
} from '@/api/notificationTemplates'
import { toProblemDetail } from '@/api/http'
import AdminPager from '@/components/admin/AdminPager.vue'
import { useAdminListQuery } from '@/composables/useAdminListQuery'

const { t } = useI18n()
const { page, size } = useAdminListQuery()
const templates = ref<NotificationTemplate[]>([])
const totalPages = ref(0)
const error = ref<string | null>(null)

const emptyForm = () => ({
  templateKey: '',
  variantKey: '',
  locale: '',
  label: '',
  subject: '',
  textBody: '',
  htmlBody: '',
})
const form = ref(emptyForm())
/** Identifiant du brouillon en cours de modification ; null = création. */
const editingId = ref<string | null>(null)

// Exemple de syntaxe cité dans l'aide (jeton technique, passé en paramètre pour ne pas être lu comme un {placeholder}).
const VARIABLE_EXAMPLE = '[[${dossier.reference}]]'
const SAMPLE_MODEL = '{\n  "dossier": { "reference": "DOS-1", "fields": {} },\n  "variables": {}\n}'
const modelText = ref(SAMPLE_MODEL)
const modelError = ref<string | null>(null)
const preview = ref<RenderedNotification | null>(null)

/** 400 (contenu refusé : la règle violée est nommée) et 409 (état ou doublon) restent lisibles, avec le détail du serveur. */
function describeError(err: unknown, fallbackKey: string): string {
  const problem = toProblemDetail(err)
  const detail = problem.detail ?? ''
  if (problem.status === 400) return t('admin.notificationTemplates.errors.badRequest', { detail })
  if (problem.status === 409) return t('admin.notificationTemplates.errors.conflict', { detail })
  return detail || t(fallbackKey)
}

async function refresh() {
  const result = await listNotificationTemplates({ page: page.value, size: size.value })
  templates.value = result.content
  totalPages.value = result.totalPages
}

watch(
  [page, size],
  () => {
    void refresh().catch((err) => {
      error.value = describeError(err, 'admin.notificationTemplates.errors.load')
    })
  },
  { immediate: true },
)

function parseModel(): Record<string, unknown> | null {
  try {
    const parsed: unknown = JSON.parse(modelText.value)
    if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
      modelError.value = t('admin.notificationTemplates.errors.modelObject')
      return null
    }
    modelError.value = null
    return parsed as Record<string, unknown>
  } catch {
    modelError.value = t('admin.notificationTemplates.errors.modelInvalid')
    return null
  }
}

async function run(action: () => Promise<unknown>, fallbackKey: string): Promise<boolean> {
  error.value = null
  try {
    await action()
    return true
  } catch (err) {
    error.value = describeError(err, fallbackKey)
    return false
  }
}

function resetForm() {
  form.value = emptyForm()
  editingId.value = null
}

async function save() {
  const f = form.value
  const ok = await run(async () => {
    if (editingId.value) {
      await updateNotificationTemplate(editingId.value, {
        label: f.label,
        subject: f.subject,
        textBody: f.textBody,
        htmlBody: f.htmlBody,
      })
    } else {
      await createNotificationTemplate({
        templateKey: f.templateKey,
        variantKey: f.variantKey || undefined,
        locale: f.locale || undefined,
        label: f.label,
        subject: f.subject,
        textBody: f.textBody,
        htmlBody: f.htmlBody || null,
      })
    }
    await refresh()
  }, 'admin.notificationTemplates.errors.save')
  if (ok) resetForm()
}

async function edit(template: NotificationTemplate) {
  // Le détail fait foi : jamais un formulaire vide qui écraserait les corps du brouillon à l'enregistrement.
  await run(async () => {
    const full = await getNotificationTemplate(template.id)
    form.value = {
      templateKey: full.templateKey,
      variantKey: full.variantKey ?? '',
      locale: full.locale ?? '',
      label: full.label,
      subject: full.subject,
      textBody: full.textBody,
      htmlBody: full.htmlBody ?? '',
    }
    editingId.value = full.id
  }, 'admin.notificationTemplates.errors.loadOne')
}

async function lifecycle(action: () => Promise<unknown>, fallbackKey: string) {
  const ok = await run(async () => {
    await action()
    await refresh()
  }, fallbackKey)
  if (ok) preview.value = null
}

async function previewForm() {
  const model = parseModel()
  if (!model) return
  const f = form.value
  await run(async () => {
    preview.value = await previewNotificationTemplate({
      template: { subject: f.subject, textBody: f.textBody, htmlBody: f.htmlBody || null },
      model,
    })
  }, 'admin.notificationTemplates.errors.preview')
}

async function previewStored(template: NotificationTemplate) {
  const model = parseModel()
  if (!model) return
  await run(async () => {
    preview.value = await previewNotificationTemplate({ id: template.id, model })
  }, 'admin.notificationTemplates.errors.preview')
}
</script>

<template>
  <section>
    <h1>{{ t('admin.notificationTemplates.title') }}</h1>
    <p class="hint">{{ t('admin.notificationTemplates.hint', { example: VARIABLE_EXAMPLE }) }}</p>
    <p v-if="error" class="error" role="alert" data-testid="nt-error">{{ error }}</p>
    <div class="nt-layout">
      <form class="admin-form" data-testid="nt-form" @submit.prevent="save">
        <h2>{{ editingId ? t('admin.notificationTemplates.editTitle') : t('admin.notificationTemplates.createTitle') }}</h2>
        <label>
          {{ t('admin.notificationTemplates.fields.templateKey') }}
          <input v-model="form.templateKey" :disabled="editingId !== null" required />
        </label>
        <label>
          {{ t('admin.notificationTemplates.fields.variantKey') }}
          <input v-model="form.variantKey" :disabled="editingId !== null" />
        </label>
        <label>
          {{ t('admin.notificationTemplates.fields.locale') }}
          <input v-model="form.locale" :disabled="editingId !== null" />
        </label>
        <label>
          {{ t('admin.notificationTemplates.fields.label') }}
          <input v-model="form.label" required />
        </label>
        <label>
          {{ t('admin.notificationTemplates.fields.subject') }}
          <input v-model="form.subject" required />
        </label>
        <label>
          {{ t('admin.notificationTemplates.fields.textBody') }}
          <textarea v-model="form.textBody" rows="6" required />
        </label>
        <label>
          {{ t('admin.notificationTemplates.fields.htmlBody') }}
          <textarea v-model="form.htmlBody" rows="6" />
        </label>
        <div class="actions">
          <button type="submit" data-testid="nt-save">
            {{ editingId ? t('admin.notificationTemplates.save') : t('admin.notificationTemplates.create') }}
          </button>
          <button type="button" data-testid="nt-preview-form" @click="previewForm">{{ t('admin.notificationTemplates.preview') }}</button>
          <button v-if="editingId" type="button" @click="resetForm">{{ t('common.cancel') }}</button>
        </div>
      </form>
      <aside class="nt-preview" :aria-label="t('admin.notificationTemplates.previewAria')">
        <h2>{{ t('admin.notificationTemplates.previewTitle') }}</h2>
        <label>
          {{ t('admin.notificationTemplates.model') }}
          <textarea v-model="modelText" rows="6" />
        </label>
        <p v-if="modelError" class="error" role="alert">{{ modelError }}</p>
        <template v-if="preview">
          <p>
            <strong>{{ t('admin.notificationTemplates.fields.subject') }}</strong>
            <span data-testid="nt-preview-subject">{{ preview.subject }}</span>
          </p>
          <pre data-testid="nt-preview-text">{{ preview.textBody }}</pre>
          <iframe
            v-if="preview.htmlBody"
            class="nt-preview__html"
            sandbox=""
            :title="t('admin.notificationTemplates.htmlTitle')"
            :srcdoc="preview.htmlBody"
          />
        </template>
      </aside>
    </div>
    <ul>
      <li v-for="tpl in templates" :key="tpl.id">
        <div>
          <strong>{{ tpl.templateKey }}</strong> — {{ tpl.label }}
          <span class="meta">
            ({{ t('admin.notificationTemplates.meta', { variant: tpl.variantKey ?? '—', locale: tpl.locale ?? '—', version: tpl.version, status: tpl.status }) }})
          </span>
        </div>
        <div class="actions">
          <button v-if="tpl.status === 'DRAFT'" type="button" data-testid="nt-edit" @click="edit(tpl)">{{ t('admin.notificationTemplates.edit') }}</button>
          <button type="button" data-testid="nt-preview-row" @click="previewStored(tpl)">{{ t('admin.notificationTemplates.preview') }}</button>
          <button v-if="tpl.status === 'DRAFT'" type="button" data-testid="nt-activate" @click="lifecycle(() => activateNotificationTemplate(tpl.id), 'admin.notificationTemplates.errors.activate')">{{ t('admin.notificationTemplates.activate') }}</button>
          <button v-if="tpl.status !== 'ARCHIVED'" type="button" data-testid="nt-archive" @click="lifecycle(() => archiveNotificationTemplate(tpl.id), 'admin.notificationTemplates.errors.archive')">{{ t('admin.notificationTemplates.archive') }}</button>
          <button type="button" data-testid="nt-new-version" @click="lifecycle(() => newNotificationTemplateVersion(tpl.id), 'admin.notificationTemplates.errors.newVersion')">{{ t('admin.notificationTemplates.newVersion') }}</button>
        </div>
      </li>
    </ul>
    <AdminPager v-model="page" :total-pages="totalPages" />
  </section>
</template>

<style scoped>
.nt-layout {
  display: grid;
  grid-template-columns: minmax(16rem, 32rem) minmax(16rem, 1fr);
  gap: 1.5rem;
  align-items: start;
  margin-bottom: 1rem;
}
.admin-form,
.nt-preview {
  display: grid;
  gap: 0.5rem;
}
label {
  display: grid;
  gap: 0.25rem;
}
.nt-preview__html {
  width: 100%;
  min-height: 12rem;
  border: 1px solid #ddd;
  background: #fff;
}
pre {
  white-space: pre-wrap;
  margin: 0;
}
ul {
  list-style: none;
  padding: 0;
}
li {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
  padding: 0.5rem 0;
  border-bottom: 1px solid #ddd;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}
.hint,
.meta {
  color: #555;
}
.error {
  color: #c92a2a;
}
</style>
