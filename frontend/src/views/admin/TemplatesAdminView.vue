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
import TemplateVisualEditor from '@/components/document/TemplateVisualEditor.vue'
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
// TODO §84 (LC4b) : variables d'exemple de l'aperçu (menu de l'éditeur visuel) et édition du contenu d'un brouillon.
const sampleVariables = ref<Record<string, unknown>>({})
const editing = ref<{ id: string; templateKey: string; label: string; htmlContent: string } | null>(null)
const editingError = ref<string | null>(null)

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

async function editContent(template: DocumentTemplate) {
  editingError.value = null
  try {
    const withContent = await listDocumentTemplates(template.templateKey, true, { page: 0, size: 100 })
    const full = withContent.content.find((t) => t.id === template.id)
    editing.value = {
      id: template.id,
      templateKey: template.templateKey,
      label: template.label,
      htmlContent: full?.htmlContent ?? '',
    }
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? 'Chargement du contenu impossible'
  }
}

async function saveContent() {
  if (!editing.value) return
  editingError.value = null
  try {
    await updateDocumentTemplate(editing.value.id, { htmlContent: editing.value.htmlContent })
    editing.value = null
    await refresh()
  } catch (err) {
    editingError.value = toProblemDetail(err).detail ?? 'Enregistrement impossible'
  }
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
        <TemplateVisualEditor v-model="form.htmlContent" :sample-variables="sampleVariables" />
        <button type="submit">Créer</button>
      </form>
      <TemplatePreviewPanel :html-content="editing ? editing.htmlContent : form.htmlContent" @update:variables="sampleVariables = $event" />
    </div>
    <section v-if="editing" class="templates-edit" data-testid="template-edit">
      <h2>Modifier le contenu de « {{ editing.templateKey }} » (brouillon)</h2>
      <p v-if="editingError" class="error" role="alert">{{ editingError }}</p>
      <TemplateVisualEditor v-model="editing.htmlContent" :sample-variables="sampleVariables" />
      <div class="actions">
        <button type="button" data-testid="save-content" @click="saveContent">Enregistrer</button>
        <button type="button" @click="editing = null">Annuler</button>
      </div>
    </section>
    <ul>
      <li v-for="tpl in templates" :key="tpl.id">
        <div>
          <strong>{{ tpl.templateKey }}</strong> — {{ tpl.label }} (v{{ tpl.version }}, {{ tpl.status }})
        </div>
        <div class="actions">
          <button v-if="tpl.status === 'DRAFT'" type="button" data-testid="edit-content" @click="editContent(tpl)">Modifier le contenu</button>
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
