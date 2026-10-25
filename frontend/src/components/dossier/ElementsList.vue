<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { DossierElementView, PieceType } from '@/api/types'
import { downloadElementContent, elementContentUrl } from '@/api/elements'
import AdminPager from '@/components/admin/AdminPager.vue'

const props = defineProps<{
  elements: DossierElementView[]
  totalPages?: number
  /** TODO §105 — types de pièces du type de dossier ; sans eux, rien n'est affiché ni reclassable. */
  pieceTypes?: PieceType[]
  canClassify?: boolean
}>()

const emit = defineEmits<{ classify: [elementId: string, pieceKey: string | null] }>()

/** Clé du type de la pièce, vide si elle n'est pas classée ou si son type n'est plus déclaré. */
function pieceKeyOf(element: DossierElementView): string {
  const key = element.metadata?.pieceKey
  return typeof key === 'string' && (props.pieceTypes ?? []).some((type) => type.key === key) ? key : ''
}

function pieceLabelOf(element: DossierElementView): string {
  const key = pieceKeyOf(element)
  return (props.pieceTypes ?? []).find((type) => type.key === key)?.label ?? t('dossier.pieceTypes.unclassified')
}

function onClassify(element: DossierElementView, event: Event) {
  const value = (event.target as HTMLSelectElement).value
  emit('classify', element.id, value === '' ? null : value)
}

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
          <template v-if="el.type === 'ATTACHMENT' && (pieceTypes?.length ?? 0) > 0">
            <select
              v-if="canClassify"
              class="elements-list__piece"
              :value="pieceKeyOf(el)"
              :aria-label="t('dossier.pieceTypes.classify', { label: el.label })"
              :data-testid="`element-piece-type-${el.id}`"
              @change="onClassify(el, $event)"
            >
              <option value="">{{ t('dossier.pieceTypes.unclassified') }}</option>
              <option v-for="pieceType in pieceTypes" :key="pieceType.key" :value="pieceType.key">
                {{ pieceType.label }}
              </option>
            </select>
            <span v-else class="elements-list__piece" :data-testid="`element-piece-label-${el.id}`">
              {{ pieceLabelOf(el) }}
            </span>
          </template>
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
.elements-list__piece {
  display: block;
  margin-top: 0.25rem;
  font-size: 0.85rem;
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
