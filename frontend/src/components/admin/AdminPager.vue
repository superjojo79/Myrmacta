<script setup lang="ts">
const page = defineModel<number>({ required: true })

defineProps<{
  totalPages: number
}>()
</script>

<template>
  <nav class="admin-pager" aria-label="Pagination">
    <button
      type="button"
      class="admin-pager__btn"
      data-testid="admin-pager-prev"
      :disabled="page <= 0"
      @click="page = page - 1"
    >
      Précédent
    </button>
    <span data-testid="admin-pager-status">{{ page + 1 }} / {{ Math.max(totalPages, 1) }}</span>
    <button
      type="button"
      class="admin-pager__btn"
      data-testid="admin-pager-next"
      :disabled="page + 1 >= totalPages"
      @click="page = page + 1"
    >
      Suivant
    </button>
  </nav>
</template>

<style scoped>
.admin-pager {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  margin: 1rem 0;
}
.admin-pager__btn:disabled {
  opacity: 0.5;
}
</style>
