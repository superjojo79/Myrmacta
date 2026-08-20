<script setup lang="ts">
import { ref, watch } from 'vue'
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
import TemplatePreviewPanel from '@/components/document/TemplatePreviewPanel.vue'
import AdminPager from '@/components/admin/AdminPager.vue'
import { useAdminListQuery } from '@/composables/useAdminListQuery'

const { page, size } = useAdminListQuery()
const templates = ref<DocumentTemplate[]>([])
const totalPages = ref(0)
const error = ref<string | null>(null)
const form = ref({
  templateKey: '',
  label: '',
  htmlContent: '<p>Contenu</p>',
})

async function refresh() {
  const result = await listDocumentTemplates(undefined, false, { page: page.value, size: size.value })
  templates.value = result.content
  totalPages.value = result.totalPages
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
    <div class="templates-layout">
      <form class="admin-form" @submit.prevent="create">
        <input v-model="form.templateKey" placeholder="templateKey" required />
        <input v-model="form.label" placeholder="libellé" required />
        <textarea v-model="form.htmlContent" rows="4" />
        <button type="submit">Créer</button>
      </form>
      <TemplatePreviewPanel :html-content="form.htmlContent" />
    </div>
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
    <AdminPager v-model="page" :total-pages="totalPages" />
  </section>
</template>

<style scoped>
.templates-layout {
  display: grid;
  grid-template-columns: minmax(16rem, 32rem) minmax(16rem, 1fr);
  gap: 1.5rem;
  align-items: start;
  margin-bottom: 1rem;
}
.admin-form {
  display: grid;
  gap: 0.5rem;
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
