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
import type { DossierPermission, DossierType } from '@/api/types'
import { toProblemDetail } from '@/api/http'
import AdminPager from '@/components/admin/AdminPager.vue'
import { useAdminListQuery } from '@/composables/useAdminListQuery'

const { page, size } = useAdminListQuery()
const types = ref<DossierType[]>([])
const totalPages = ref(0)
const error = ref<string | null>(null)
const retentionDaysDefault = ref<number | null>(null)
const retentionConfigured = ref(false)
const retentionDraft = ref(365)
const form = ref({
  code: '',
  label: '',
  workflowKey: '',
  active: true,
  retentionMode: 'inherit' as 'inherit' | 'days' | 'never',
  retentionDays: 365,
})
const acl = ref({ code: '', subjectUserId: '', permission: 'VIEW' as DossierPermission })

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
  try {
    await createDossierType({
      code: form.value.code,
      label: form.value.label,
      workflowKey: form.value.workflowKey,
      active: form.value.active,
      fieldDefinitions: [],
      ...retentionPayload(),
    })
    form.value = {
      code: '',
      label: '',
      workflowKey: '',
      active: true,
      retentionMode: 'inherit',
      retentionDays: 365,
    }
    await refresh()
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? 'Création impossible'
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

    <form class="admin-form" @submit.prevent="create">
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
          </td>
        </tr>
      </tbody>
    </table>
    <AdminPager v-model="page" :total-pages="totalPages" />

    <h2>ACL</h2>
    <form class="admin-form" @submit.prevent="grant">
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
