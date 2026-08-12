<script setup lang="ts">
import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{
  value: unknown
  sensitive?: boolean
}>()

const auth = useAuthStore()
const { isTenantAdmin } = storeToRefs(auth)
const revealed = ref(false)

const display = computed(() => {
  if (!props.sensitive) return String(props.value ?? '')
  if (isTenantAdmin.value || revealed.value) return String(props.value ?? '')
  return '••••'
})

const canReveal = computed(() => props.sensitive && !isTenantAdmin.value)
</script>

<template>
  <span class="sensitive-value">
    <span>{{ display }}</span>
    <button
      v-if="canReveal"
      type="button"
      class="sensitive-value__toggle"
      @click="revealed = !revealed"
    >
      {{ revealed ? 'Masquer' : 'Révéler' }}
    </button>
  </span>
</template>

<style scoped>
.sensitive-value {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}
.sensitive-value__toggle {
  border: 1px solid #b7b7ba;
  background: white;
  padding: 0.15rem 0.45rem;
  cursor: pointer;
  font-size: 0.8rem;
}
</style>
