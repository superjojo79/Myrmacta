<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { createDocumentTemplate, listDocumentTemplates, updateDocumentTemplate } from '@/api/admin'
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
        <strong>{{ tpl.templateKey }}</strong> — {{ tpl.label }} (v{{ tpl.version }}, {{ tpl.status }})
        <button type="button" @click="rename(tpl)">Renommer</button>
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
  padding: 0.5rem 0;
  border-bottom: 1px solid #ddd;
}
.error {
  color: #c92a2a;
}
</style>
