<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  activateDocumentTemplate,
  archiveDocumentTemplate,
  createDocumentTemplate,
  listDocumentTemplates,
  newDocumentTemplateVersion,
  updateDocumentTemplate,
} from '@/api/admin'
import type { DocumentTemplate } from '@/api/types'
import { toProblemDetail } from '@/api/http'

const templates = ref<DocumentTemplate[]>([])
const error = ref<string | null>(null)
const form = ref({
  templateKey: '',
  label: '',
  htmlContent: '<p>Contenu</p>',
})

async function refresh() {
  templates.value = await listDocumentTemplates(undefined, false)
}

onMounted(() => {
  void refresh().catch((err) => {
    error.value = toProblemDetail(err).detail ?? 'Chargement impossible'
  })
})

async function create() {
  try {
    await createDocumentTemplate(form.value)
    form.value = { templateKey: '', label: '', htmlContent: '<p>Contenu</p>' }
    await refresh()
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? 'Création impossible'
  }
}

async function rename(template: DocumentTemplate) {
  const label = prompt('Nouveau libellé', template.label)
  if (!label) return
  await updateDocumentTemplate(template.id, { label })
  await refresh()
}

async function activate(template: DocumentTemplate) {
  await activateDocumentTemplate(template.id)
  await refresh()
}

async function archive(template: DocumentTemplate) {
  await archiveDocumentTemplate(template.id)
  await refresh()
}

async function newVersion(template: DocumentTemplate) {
  await newDocumentTemplateVersion(template.id)
  await refresh()
}
</script>

<template>
  <section>
    <h1>Templates documents</h1>
    <p v-if="error" class="error">{{ error }}</p>
    <form class="admin-form" @submit.prevent="create">
      <input v-model="form.templateKey" placeholder="templateKey" required />
      <input v-model="form.label" placeholder="libellé" required />
      <textarea v-model="form.htmlContent" rows="4" />
      <button type="submit">Créer</button>
    </form>
    <ul>
      <li v-for="tpl in templates" :key="tpl.id">
        <div>
          <strong>{{ tpl.templateKey }}</strong> — {{ tpl.label }} (v{{ tpl.version }}, {{ tpl.status }})
        </div>
        <div class="actions">
          <button type="button" @click="rename(tpl)">Renommer</button>
          <button type="button" @click="activate(tpl)">Activer</button>
          <button type="button" @click="archive(tpl)">Archiver</button>
          <button type="button" @click="newVersion(tpl)">Nouvelle version</button>
        </div>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.admin-form {
  display: grid;
  gap: 0.5rem;
  max-width: 520px;
  margin-bottom: 1rem;
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
.error {
  color: #c92a2a;
}
</style>
