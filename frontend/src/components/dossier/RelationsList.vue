<script setup lang="ts">
import { RouterLink } from 'vue-router'
import type { DossierRelation } from '@/api/types'
import { relationLabel } from './relationLabels'

defineProps<{
  relations: DossierRelation[]
}>()
</script>

<template>
  <section class="relations-list">
    <header class="relations-list__header">
      <h2>Dossiers liés</h2>
      <slot name="actions" />
    </header>
    <p v-if="relations.length === 0" class="relations-list__empty">Aucun dossier lié.</p>
    <ul v-else>
      <li v-for="relation in relations" :key="relation.id">
        <div>
          <strong>{{ relationLabel(relation.type, relation.direction) }}</strong>
          <span class="relations-list__meta">
            {{ relation.relatedDossier.reference }} · {{ relation.relatedDossier.dossierTypeLabel }}
          </span>
        </div>
        <RouterLink :to="`/dossiers/${relation.relatedDossier.id}`">
          {{ relation.relatedDossier.label }}
        </RouterLink>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.relations-list__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.relations-list ul {
  list-style: none;
  padding: 0;
  margin: 0;
}
.relations-list li {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.65rem 0;
  border-bottom: 1px solid #ddd;
}
.relations-list__meta {
  display: block;
  font-size: 0.85rem;
  opacity: 0.7;
}
.relations-list__empty {
  opacity: 0.7;
}
</style>
