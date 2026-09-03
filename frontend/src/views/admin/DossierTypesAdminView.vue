<script setup lang="ts">
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
import type { DossierPermission, DossierType, FieldDefinition } from '@/api/types'
import { toProblemDetail } from '@/api/http'
import AdminPager from '@/components/admin/AdminPager.vue'
import FieldDefinitionDefsEditor from '@/components/dossier/FieldDefinitionDefsEditor.vue'
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
  if (type.retentionNever) return 'NEVER'
  if (type.retentionDays != null) return `${type.retentionDays} j`
  return 'héritage'
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
  } catch (err) {
    const status = toProblemDetail(err).status
    if (status === 404) {
      slaConfigured.value = false
    } else {
      throw err
    }
  }
}

watch(
  [page, size],
  () => {
    void refresh().catch((err) => {
      error.value = toProblemDetail(err).detail ?? 'Chargement impossible'
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
    error.value = toProblemDetail(err).detail ?? 'Enregistrement rétention impossible'
  }
}

async function saveSla() {
  error.value = null
  try {
    const saved = await upsertSlaSettings(slaTargetPercent.value)
    slaConfigured.value = true
    slaTargetPercent.value = saved.complianceTargetPercent
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? 'Enregistrement SLA impossible'
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
    error.value = toProblemDetail(err).detail ?? 'Création impossible'
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
    editFieldErrors.value = [toProblemDetail(err).detail ?? 'Enregistrement du schéma impossible']
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
    error.value = toProblemDetail(err).detail ?? 'Retrait SLA impossible'
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
</script>

<template>
  <section>
    <h1>Types de dossier</h1>
    <p v-if="error" class="error">{{ error }}</p>

    <section class="retention-block" aria-labelledby="retention-heading">
      <h2 id="retention-heading">Rétention tenant</h2>
      <p v-if="!retentionConfigured" class="hint">
        Aucune durée par défaut configurée (GET 404). Le job de rétention est inactif jusqu’au
        premier enregistrement.
      </p>
      <p v-else class="hint">Défaut actuel : {{ retentionDaysDefault }} jours.</p>
      <form class="admin-form" @submit.prevent="saveRetention">
        <label>
          Durée par défaut (jours)
          <input
            v-model.number="retentionDraft"
            type="number"
            min="1"
            required
            data-testid="retention-default-days"
          />
        </label>
        <button type="submit" data-testid="retention-save">Enregistrer la rétention</button>
      </form>
    </section>

    <section class="retention-block" aria-labelledby="sla-heading">
      <h2 id="sla-heading">Objectif SLA tenant</h2>
      <p v-if="!slaConfigured" class="hint">Aucune cible configurée (GET 404) — le rapport utilise 95 %.</p>
      <form class="admin-form" data-testid="sla-settings-form" @submit.prevent="saveSla">
        <label>
          Objectif de conformité (%)
          <input
            v-model.number="slaTargetPercent"
            type="number"
            min="1"
            max="100"
            required
            data-testid="sla-target-percent"
          />
        </label>
        <button type="submit" data-testid="sla-save">Enregistrer l'objectif SLA</button>
      </form>
    </section>

    <form class="admin-form" data-testid="type-create-form" @submit.prevent="create">
      <input v-model="form.code" placeholder="code" required />
      <input v-model="form.label" placeholder="libellé" required />
      <input v-model="form.workflowKey" placeholder="workflowKey" required />
      <select v-model="form.retentionMode" data-testid="type-retention-mode">
        <option value="inherit">Rétention : héritage</option>
        <option value="days">Rétention : jours</option>
        <option value="never">Rétention : NEVER</option>
      </select>
      <input
        v-if="form.retentionMode === 'days'"
        v-model.number="form.retentionDays"
        type="number"
        min="1"
        placeholder="jours"
        data-testid="type-retention-days"
        required
      />
      <input
        v-model.number="form.slaTargetHours"
        type="number"
        min="1"
        placeholder="SLA heures"
        data-testid="type-sla-hours"
      />
      <label class="allow-unknown-fields">
        <input v-model="form.allowUnknownFields" type="checkbox" data-testid="type-allow-unknown-fields" />
        Autoriser les champs inconnus
      </label>
      <FieldDefinitionDefsEditor v-model="form.fieldDefinitions" />
      <fieldset>
        <legend>Templates de documents autorisés (document-generation)</legend>
        <DocumentTemplateKeysEditor v-model="form.documentTemplateKeys" />
      </fieldset>
      <button type="submit">Créer</button>
    </form>
    <table>
      <thead>
        <tr>
          <th>Code</th>
          <th>Libellé</th>
          <th>Workflow</th>
          <th>Actif</th>
          <th>Rétention</th>
          <th />
        </tr>
      </thead>
      <tbody>
        <tr v-for="type in types" :key="type.code">
          <td>{{ type.code }}</td>
          <td>{{ type.label }}</td>
          <td>{{ type.workflowKey }}</td>
          <td>{{ type.active ? 'oui' : 'non' }}</td>
          <td data-testid="type-retention-label">{{ retentionLabel(type) }}</td>
          <td class="actions">
            <button type="button" @click="toggleActive(type)">Basculer</button>
            <button type="button" @click="setTypeNever(type)">NEVER</button>
            <button type="button" @click="setTypeInherit(type)">Héritage</button>
            <button
              v-if="type.slaTargetHours != null"
              type="button"
              data-testid="type-clear-sla"
              @click="clearTypeSla(type)"
            >
              Retirer le SLA
            </button>
            <button type="button" data-testid="type-edit-fields" @click="startEditFields(type)">
              Modifier les champs
            </button>
          </td>
        </tr>
      </tbody>
    </table>
    <AdminPager v-model="page" :total-pages="totalPages" />

    <section v-if="editingCode" class="edit-fields-panel" data-testid="type-edit-fields-panel">
      <h2>Modifier les champs — {{ editingCode }}</h2>
      <p v-if="editFieldErrors.length" class="error">{{ editFieldErrors.join(' ') }}</p>
      <label class="allow-unknown-fields">
        <input v-model="editDraft.allowUnknownFields" type="checkbox" data-testid="type-allow-unknown-fields" />
        Autoriser les champs inconnus
      </label>
      <FieldDefinitionDefsEditor v-model="editDraft.fieldDefinitions" />
      <fieldset>
        <legend>Templates de documents autorisés (document-generation)</legend>
        <DocumentTemplateKeysEditor v-model="editDraft.documentTemplateKeys" />
      </fieldset>
      <div class="edit-fields-panel__actions">
        <button type="button" data-testid="type-edit-fields-save" @click="saveEditFields">Enregistrer</button>
        <button type="button" data-testid="type-edit-fields-cancel" @click="cancelEditFields">Annuler</button>
      </div>
    </section>

    <h2>ACL</h2>
    <form class="admin-form" data-testid="acl-form" @submit.prevent="grant">
      <input v-model="acl.code" placeholder="code type" required />
      <input v-model="acl.subjectUserId" placeholder="userId" required />
      <select v-model="acl.permission">
        <option value="VIEW">VIEW</option>
        <option value="EDIT">EDIT</option>
      </select>
      <button type="submit">Accorder</button>
      <button type="button" @click="revoke">Révoquer</button>
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
