<script setup lang="ts">
import type { DossierElementView } from '@/api/types'
import { downloadElementContent, elementContentUrl } from '@/api/elements'

defineProps<{
  elements: DossierElementView[]
}>()

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
</script>

<template>
  <section class="elements-list">
    <header class="elements-list__header">
      <h2>Éléments</h2>
      <slot name="actions" />
    </header>
    <p v-if="elements.length === 0" class="elements-list__empty">Aucun élément.</p>
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
        </div>
        <a
          v-if="el.downloadable"
          :href="elementContentUrl(el.id)"
          @click.prevent="download(el)"
        >
          Télécharger
        </a>
      </li>
    </ul>
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
button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
</style>
