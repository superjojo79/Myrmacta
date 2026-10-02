<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ref, watch } from 'vue'
import {
  createDossierType,
  listDossierTypes,
  updateDossierType,
  grantDossierTypeAcl,
  revokeDossierTypeAcl,
} from '@/api/dossierTypes'
import { getRetentionSettings, upsertRetentionSettings } from '@/api/retentionSettings'
import { getSlaSettings, upsertSlaSettings } from '@/api/slaSettings'
import { listNotificationWebhooks, type NotificationWebhook } from '@/api/notificationWebhooks'
import type { DossierPermission, DossierType, FieldDefinition } from '@/api/types'
import { toProblemDetail } from '@/api/http'
import AdminPager from '@/components/admin/AdminPager.vue'
import FormDesigner from '@/components/dossier/FormDesigner.vue'
import DocumentTemplateKeysEditor from '@/components/dossier/DocumentTemplateKeysEditor.vue'
import { validateFieldDefinitions } from '@/dossier/validateFieldDefinitions'
import { useAdminListQuery } from '@/composables/useAdminListQuery'

const { page, size } = useAdminListQuery()
const types = ref<DossierType[]>([])
const totalPages = ref(0)
const error = ref<string | null>(null)
const retentionDaysDefault = ref<number | null>(null)
const retentionConfigured = ref(false)
const retentionDraft = ref(365)
const slaTargetPercent = ref(95)
const slaConfigured = ref(false)
const alertingEnabled = ref(false)
const alertIntervalMinutes = ref(60)
const alertEmailsText = ref('')
const alertWebhookIds = ref<string[]>([])
const biExportEnabled = ref(false)
const biIntervalHours = ref(24)
const biRetentionDays = ref(30)
const enabledWebhooks = ref<NotificationWebhook[]>([])
const form = ref({
  code: '',
  label: '',
  workflowKey: '',
  active: true,
  retentionMode: 'inherit' as 'inherit' | 'days' | 'never',
  retentionDays: 365,
  slaTargetHours: null as number | null,
  allowUnknownFields: false,
  fieldDefinitions: [] as FieldDefinition[],
  documentTemplateKeys: [] as string[],
})
const acl = ref({ code: '', subjectUserId: '', permission: 'VIEW' as DossierPermission })

const editingCode = ref<string | null>(null)
const editDraft = ref<{
  allowUnknownFields: boolean
  fieldDefinitions: FieldDefinition[]
  documentTemplateKeys: string[]
}>({
  allowUnknownFields: false,
  fieldDefinitions: [],
  documentTemplateKeys: [],
})
const editFieldErrors = ref<string[]>([])

function retentionLabel(type: DossierType): string {
  if (type.retentionNever) return t('admin.types.retentionLabel.never')
  if (type.retentionDays != null) return t('admin.types.retentionLabel.days', { n: type.retentionDays })
  return t('admin.types.retentionLabel.inherit')
}

async function refresh() {
  const result = await listDossierTypes({ page: page.value, size: size.value })
  types.value = result.content
  totalPages.value = result.totalPages
  try {
    const settings = await getRetentionSettings()
    retentionConfigured.value = true
    retentionDaysDefault.value = settings.defaultRetentionDays
    retentionDraft.value = settings.defaultRetentionDays
  } catch (err) {
    const status = toProblemDetail(err).status
    if (status === 404) {
      retentionConfigured.value = false
      retentionDaysDefault.value = null
    } else {
      throw err
    }
  }
  try {
    const sla = await getSlaSettings()
    slaConfigured.value = true
    slaTargetPercent.value = sla.complianceTargetPercent
    alertingEnabled.value = sla.alertingEnabled
    alertIntervalMinutes.value = sla.alertIntervalMinutes
    alertEmailsText.value = sla.alertEmails.join('\n')
    alertWebhookIds.value = [...sla.webhookIds]
    biExportEnabled.value = sla.biExportEnabled
    biIntervalHours.value = sla.biIntervalHours
    biRetentionDays.value = sla.biRetentionDays
  } catch (err) {
    const status = toProblemDetail(err).status
    if (status === 404) {
      slaConfigured.value = false
    } else {
      throw err
    }
  }
  try {
    const hooks = await listNotificationWebhooks({ page: 0, size: 100 })
    enabledWebhooks.value = hooks.content.filter((hook) => hook.enabled)
  } catch {
    enabledWebhooks.value = []
  }
}

watch(
  [page, size],
  () => {
    void refresh().catch((err) => {
      error.value = toProblemDetail(err).detail ?? t('admin.types.errors.load')
    })
  },
  { immediate: true },
)

async function saveRetention() {
  error.value = null
  try {
    const saved = await upsertRetentionSettings(retentionDraft.value)
    retentionConfigured.value = true
    retentionDaysDefault.value = saved.defaultRetentionDays
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('admin.types.errors.retention')
  }
}

async function saveSla() {
  error.value = null
  try {
    const saved = await upsertSlaSettings({
      complianceTargetPercent: slaTargetPercent.value,
      alertingEnabled: alertingEnabled.value,
      alertIntervalMinutes: alertIntervalMinutes.value,
      alertEmails: alertEmailsText.value
        .split(/[\n,]/)
        .map((email) => email.trim())
        .filter((email) => email.length > 0),
      webhookIds: alertWebhookIds.value,
      biExportEnabled: biExportEnabled.value,
      biIntervalHours: biIntervalHours.value,
      biRetentionDays: biRetentionDays.value,
    })
    slaConfigured.value = true
    slaTargetPercent.value = saved.complianceTargetPercent
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('admin.types.errors.sla')
  }
}

function retentionPayload() {
  if (form.value.retentionMode === 'never') {
    return { retentionDays: null, retentionNever: true }
  }
  if (form.value.retentionMode === 'days') {
    return { retentionDays: form.value.retentionDays, retentionNever: false }
  }
  return { retentionDays: null, retentionNever: false }
}

async function create() {
  error.value = null
  const fieldErrors = validateFieldDefinitions(form.value.fieldDefinitions)
  if (fieldErrors.length > 0) {
    error.value = fieldErrors.join(' ')
    return
  }
  try {
    await createDossierType({
      code: form.value.code,
      label: form.value.label,
      workflowKey: form.value.workflowKey,
      active: form.value.active,
      allowUnknownFields: form.value.allowUnknownFields,
      fieldDefinitions: form.value.fieldDefinitions,
      slaTargetHours: form.value.slaTargetHours || null,
      documentTemplateKeys: form.value.documentTemplateKeys,
      ...retentionPayload(),
    })
    form.value = {
      code: '',
      label: '',
      workflowKey: '',
      active: true,
      retentionMode: 'inherit',
      retentionDays: 365,
      slaTargetHours: null,
      allowUnknownFields: false,
      fieldDefinitions: [],
      documentTemplateKeys: [],
    }
    await refresh()
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('admin.types.errors.create')
  }
}

function startEditFields(type: DossierType) {
  editingCode.value = type.code
  editDraft.value = {
    allowUnknownFields: type.allowUnknownFields,
    fieldDefinitions: [...type.fieldDefinitions],
    documentTemplateKeys: [...(type.documentTemplateKeys ?? [])],
  }
  editFieldErrors.value = []
}

function cancelEditFields() {
  editingCode.value = null
  editFieldErrors.value = []
}

async function saveEditFields() {
  const fieldErrors = validateFieldDefinitions(editDraft.value.fieldDefinitions)
  if (fieldErrors.length > 0) {
    editFieldErrors.value = fieldErrors
    return
  }
  try {
    await updateDossierType(editingCode.value as string, {
      allowUnknownFields: editDraft.value.allowUnknownFields,
      fieldDefinitions: editDraft.value.fieldDefinitions,
      documentTemplateKeys: editDraft.value.documentTemplateKeys,
    })
    editingCode.value = null
    editFieldErrors.value = []
    await refresh()
  } catch (err) {
    editFieldErrors.value = [toProblemDetail(err).detail ?? t('admin.types.errors.schema')]
  }
}

async function toggleActive(type: DossierType) {
  await updateDossierType(type.code, { active: !type.active })
  await refresh()
}

async function setTypeNever(type: DossierType) {
  await updateDossierType(type.code, { retentionNever: true, retentionDays: null })
  await refresh()
}

async function setTypeInherit(type: DossierType) {
  await updateDossierType(type.code, { retentionNever: false, retentionDays: null })
  await refresh()
}

async function clearTypeSla(type: DossierType) {
  error.value = null
  try {
    await updateDossierType(type.code, { clearSlaTargetHours: true })
    await refresh()
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('admin.types.errors.clearSla')
  }
}

async function grant() {
  await grantDossierTypeAcl(acl.value.code, acl.value.subjectUserId, acl.value.permission)
  acl.value.subjectUserId = ''
}

async function revoke() {
  await revokeDossierTypeAcl(acl.value.code, acl.value.subjectUserId)
  acl.value.subjectUserId = ''
}

const { t } = useI18n()
</script>

<template>
  <section>
    <h1>{{ t('admin.types.title') }}</h1>
    <p v-if="error" class="error">{{ error }}</p>

    <section class="retention-block" aria-labelledby="retention-heading">
      <h2 id="retention-heading">{{ t('admin.types.retention') }}</h2>
      <p v-if="!retentionConfigured" class="hint">
        {{ t('admin.types.noRetention') }}
      </p>
      <p v-else class="hint">{{ t('admin.types.currentRetention', { days: retentionDaysDefault }) }}</p>
      <form class="admin-form" @submit.prevent="saveRetention">
        <label>
          {{ t('admin.types.defaultDays') }}
          <input
            v-model.number="retentionDraft"
            type="number"
            min="1"
            required
            data-testid="retention-default-days"
          />
        </label>
        <button type="submit" data-testid="retention-save">{{ t('admin.types.saveRetention') }}</button>
      </form>
    </section>

    <section class="retention-block" aria-labelledby="sla-heading">
      <h2 id="sla-heading">{{ t('admin.types.slaTitle') }}</h2>
      <p v-if="!slaConfigured" class="hint">{{ t('admin.types.noSla') }}</p>
      <form class="admin-form" data-testid="sla-settings-form" @submit.prevent="saveSla">
        <label>
          {{ t('admin.types.slaTarget') }}
          <input
            v-model.number="slaTargetPercent"
            type="number"
            min="1"
            max="100"
            required
            data-testid="sla-target-percent"
          />
        </label>
        <label>
          <input v-model="alertingEnabled" type="checkbox" data-testid="sla-alerting-enabled" />
          {{ t('admin.types.slaAlerting') }}
        </label>
        <label>
          {{ t('admin.types.alertInterval') }}
          <input
            v-model.number="alertIntervalMinutes"
            type="number"
            min="15"
            max="10080"
            required
            data-testid="sla-alert-interval"
          />
        </label>
        <label>
          {{ t('admin.types.alertEmails') }}
          <textarea v-model="alertEmailsText" rows="3" data-testid="sla-alert-emails" />
        </label>
        <label>
          {{ t('admin.types.alertWebhooks') }}
          <select
            v-model="alertWebhookIds"
            multiple
            data-testid="sla-alert-webhooks"
          >
            <option v-for="hook in enabledWebhooks" :key="hook.id" :value="hook.id">
              {{ hook.name }}
            </option>
          </select>
        </label>
        <label>
          <input v-model="biExportEnabled" type="checkbox" data-testid="sla-bi-enabled" />
          {{ t('admin.types.biExport') }}
        </label>
        <label>
          {{ t('admin.types.biInterval') }}
          <input
            v-model.number="biIntervalHours"
            type="number"
            min="1"
            max="720"
            required
            data-testid="sla-bi-interval"
          />
        </label>
        <label>
          {{ t('admin.types.biRetention') }}
          <input
            v-model.number="biRetentionDays"
            type="number"
            min="1"
            max="365"
            required
            data-testid="sla-bi-retention"
          />
        </label>
        <button type="submit" data-testid="sla-save">{{ t('admin.types.saveSla') }}</button>
      </form>
    </section>

    <form class="admin-form" data-testid="type-create-form" @submit.prevent="create">
      <input v-model="form.code" :placeholder="t('admin.types.codePlaceholder')" required />
      <input v-model="form.label" :placeholder="t('admin.types.labelPlaceholder')" required />
      <input v-model="form.workflowKey" :placeholder="t('admin.types.workflowKeyPlaceholder')" required />
      <select v-model="form.retentionMode" data-testid="type-retention-mode">
        <option value="inherit">{{ t('admin.types.retentionInherit') }}</option>
        <option value="days">{{ t('admin.types.retentionDays') }}</option>
        <option value="never">{{ t('admin.types.retentionNever') }}</option>
      </select>
      <input
        v-if="form.retentionMode === 'days'"
        v-model.number="form.retentionDays"
        type="number"
        min="1"
        :placeholder="t('admin.types.daysPlaceholder')"
        data-testid="type-retention-days"
        required
      />
      <input
        v-model.number="form.slaTargetHours"
        type="number"
        min="1"
        :placeholder="t('admin.types.slaHoursPlaceholder')"
        data-testid="type-sla-hours"
      />
      <label class="allow-unknown-fields">
        <input v-model="form.allowUnknownFields" type="checkbox" data-testid="type-allow-unknown-fields" />
        {{ t('admin.types.allowUnknown') }}
      </label>
      <FormDesigner v-model="form.fieldDefinitions" />
      <fieldset>
        <legend>{{ t('admin.types.allowedTemplates') }}</legend>
        <DocumentTemplateKeysEditor v-model="form.documentTemplateKeys" />
      </fieldset>
      <button type="submit">{{ t('admin.types.create') }}</button>
    </form>
    <table>
      <thead>
        <tr>
          <th>{{ t('admin.types.code') }}</th>
          <th>{{ t('admin.types.label') }}</th>
          <th>{{ t('admin.types.workflow') }}</th>
          <th>{{ t('admin.types.active') }}</th>
          <th>{{ t('admin.types.retentionHeader') }}</th>
          <th />
        </tr>
      </thead>
      <tbody>
        <tr v-for="type in types" :key="type.code">
          <td>{{ type.code }}</td>
          <td>{{ type.label }}</td>
          <td>{{ type.workflowKey }}</td>
          <td>{{ type.active ? t('admin.types.yes') : t('admin.types.no') }}</td>
          <td data-testid="type-retention-label">{{ retentionLabel(type) }}</td>
          <td class="actions">
            <button type="button" @click="toggleActive(type)">{{ t('admin.types.toggle') }}</button>
            <button type="button" @click="setTypeNever(type)">{{ t('admin.types.never') }}</button>
            <button type="button" @click="setTypeInherit(type)">{{ t('admin.types.inherit') }}</button>
            <button
              v-if="type.slaTargetHours != null"
              type="button"
              data-testid="type-clear-sla"
              @click="clearTypeSla(type)"
            >
              {{ t('admin.types.clearSla') }}
            </button>
            <button type="button" data-testid="type-edit-fields" @click="startEditFields(type)">
              {{ t('admin.types.editFields') }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>
    <AdminPager v-model="page" :total-pages="totalPages" />

    <section v-if="editingCode" class="edit-fields-panel" data-testid="type-edit-fields-panel">
      <h2>{{ t('admin.types.editFieldsTitle', { code: editingCode }) }}</h2>
      <p v-if="editFieldErrors.length" class="error">{{ editFieldErrors.join(' ') }}</p>
      <label class="allow-unknown-fields">
        <input v-model="editDraft.allowUnknownFields" type="checkbox" data-testid="type-allow-unknown-fields" />
        {{ t('admin.types.allowUnknown') }}
      </label>
      <FormDesigner v-model="editDraft.fieldDefinitions" />
      <fieldset>
        <legend>{{ t('admin.types.allowedTemplates') }}</legend>
        <DocumentTemplateKeysEditor v-model="editDraft.documentTemplateKeys" />
      </fieldset>
      <div class="edit-fields-panel__actions">
        <button type="button" data-testid="type-edit-fields-save" @click="saveEditFields">{{ t('admin.types.save') }}</button>
        <button type="button" data-testid="type-edit-fields-cancel" @click="cancelEditFields">{{ t('common.cancel') }}</button>
      </div>
    </section>

    <h2>{{ t('admin.types.aclTitle') }}</h2>
    <form class="admin-form" data-testid="acl-form" @submit.prevent="grant">
      <input v-model="acl.code" :placeholder="t('admin.types.aclCode')" required />
      <input v-model="acl.subjectUserId" :placeholder="t('admin.types.aclUser')" required />
      <select v-model="acl.permission">
        <option value="VIEW">VIEW</option>
        <option value="EDIT">EDIT</option>
      </select>
      <button type="submit">{{ t('admin.types.grant') }}</button>
      <button type="button" @click="revoke">{{ t('admin.types.revoke') }}</button>
    </form>
  </section>
</template>

<style scoped>
.admin-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin: 1rem 0;
  align-items: end;
}
.retention-block {
  margin: 1rem 0 1.5rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid #ddd;
}
.hint {
  color: #555;
  font-size: 0.95rem;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  border-bottom: 1px solid #ddd;
  text-align: left;
  padding: 0.45rem;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}
.error {
  color: #c92a2a;
}
.allow-unknown-fields {
  flex-direction: row !important;
  align-items: center;
  gap: 0.4rem !important;
}
.edit-fields-panel {
  margin-top: 1.5rem;
  padding-top: 1rem;
  border-top: 1px solid #ddd;
}
.edit-fields-panel__actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.5rem;
}
button,
input,
select {
  padding: 0.4rem 0.6rem;
}
label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.9rem;
}
</style>
