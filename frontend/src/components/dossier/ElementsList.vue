<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { DossierElementView } from '@/api/types'
import { downloadElementContent, elementContentUrl } from '@/api/elements'
import AdminPager from '@/components/admin/AdminPager.vue'

defineProps<{
  elements: DossierElementView[]
  totalPages?: number
}>()

const page = defineModel<number>('page', { default: 0 })

function attachmentFilename(element: DossierElementView): string {
  const metaFilename = element.metadata?.filename
  return typeof metaFilename === 'string' && metaFilename.trim() !== ''
    ? metaFilename
    : element.type === 'PRODUCED_DOC'
      ? `${element.label}.pdf`
      : element.label
}

async function download(element: DossierElementView) {
  if (!element.downloadable) return
  const blob = await downloadElementContent(element.id)
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = attachmentFilename(element)
  a.click()
  URL.revokeObjectURL(url)
}

const { t } = useI18n()
</script>

<template>
  <section class="elements-list">
    <header class="elements-list__header">
      <h2>{{ t('dossier.elements.title') }}</h2>
      <slot name="actions" />
    </header>
    <p v-if="elements.length === 0" class="elements-list__empty">{{ t('dossier.elements.empty') }}</p>
    <ul v-else>
      <li v-for="el in elements" :key="el.id">
        <div>
          <strong>{{ el.label }}</strong>
          <span class="elements-list__meta">{{ el.type }} · {{ el.addedBy }}</span>
          <span v-if="el.type === 'NOTE'" class="elements-list__note">{{ el.metadata?.text }}</span>
          <a
            v-else-if="el.type === 'LINK'"
            class="elements-list__link"
            :href="String(el.metadata?.url ?? '')"
            target="_blank"
            rel="noopener noreferrer"
          >
            {{ el.metadata?.url }}
          </a>
          <dl v-else-if="el.type === 'INFORMATION'" class="elements-list__information">
            <template v-for="(value, key) in el.metadata" :key="key">
              <dt>{{ key }}</dt>
              <dd>{{ value }}</dd>
            </template>
          </dl>
        </div>
        <a
          v-if="el.downloadable"
          :href="elementContentUrl(el.id)"
          @click.prevent="download(el)"
        >
          {{ t('dossier.elements.download') }}
        </a>
      </li>
    </ul>
    <AdminPager v-if="(totalPages ?? 1) > 1" v-model="page" :total-pages="totalPages ?? 1" />
  </section>
</template>

<style scoped>
.elements-list__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.elements-list ul {
  list-style: none;
  padding: 0;
  margin: 0;
}
.elements-list li {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.65rem 0;
  border-bottom: 1px solid #ddd;
}
.elements-list__meta {
  display: block;
  font-size: 0.85rem;
  opacity: 0.7;
}
.elements-list__empty {
  opacity: 0.7;
}
.elements-list__note {
  display: block;
  font-size: 0.9rem;
  white-space: pre-wrap;
}
.elements-list__link {
  display: block;
  font-size: 0.9rem;
}
.elements-list__information {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0 0.5rem;
  margin: 0;
  font-size: 0.9rem;
}
.elements-list__information dt {
  font-weight: 600;
}
.elements-list__information dd {
  margin: 0;
}
button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
</style>
