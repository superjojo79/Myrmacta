<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  createDossierType,
  listDossierTypes,
  updateDossierType,
  grantDossierTypeAcl,
  revokeDossierTypeAcl,
} from '@/api/dossierTypes'
import type { DossierPermission, DossierType } from '@/api/types'
import { toProblemDetail } from '@/api/http'

const types = ref<DossierType[]>([])
const error = ref<string | null>(null)
const form = ref({
  code: '',
  label: '',
  workflowKey: '',
  active: true,
})
const acl = ref({ code: '', subjectUserId: '', permission: 'VIEW' as DossierPermission })

async function refresh() {
  types.value = await listDossierTypes()
}

onMounted(() => {
  void refresh().catch((err) => {
    error.value = toProblemDetail(err).detail ?? 'Chargement impossible'
  })
})

async function create() {
  error.value = null
  try {
    await createDossierType({ ...form.value, fieldDefinitions: [] })
    form.value = { code: '', label: '', workflowKey: '', active: true }
    await refresh()
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? 'Création impossible'
  }
}

async function toggleActive(type: DossierType) {
  await updateDossierType(type.code, { active: !type.active })
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
    <form class="admin-form" @submit.prevent="create">
      <input v-model="form.code" placeholder="code" required />
      <input v-model="form.label" placeholder="libellé" required />
      <input v-model="form.workflowKey" placeholder="workflowKey" required />
      <button type="submit">Créer</button>
    </form>
    <table>
      <thead>
        <tr>
          <th>Code</th>
          <th>Libellé</th>
          <th>Workflow</th>
          <th>Actif</th>
          <th />
        </tr>
      </thead>
      <tbody>
        <tr v-for="type in types" :key="type.code">
          <td>{{ type.code }}</td>
          <td>{{ type.label }}</td>
          <td>{{ type.workflowKey }}</td>
          <td>{{ type.active ? 'oui' : 'non' }}</td>
          <td>
            <button type="button" @click="toggleActive(type)">Basculer</button>
          </td>
        </tr>
      </tbody>
    </table>

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
.error {
  color: #c92a2a;
}
button,
input,
select {
  padding: 0.4rem 0.6rem;
}
</style>
