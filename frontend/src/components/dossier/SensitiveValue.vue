<script setup lang="ts">
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{
  value: unknown
  sensitive?: boolean
}>()

const auth = useAuthStore()
const { isTenantAdmin } = storeToRefs(auth)

/** D-SENSITIVE = A : masqué sauf TENANT_ADMIN (pas de reveal côté non-admin). */
const display = computed(() => {
  if (!props.sensitive) return String(props.value ?? '')
  if (isTenantAdmin.value) return String(props.value ?? '')
  return '••••'
})
</script>

<template>
  <span class="sensitive-value">{{ display }}</span>
</template>
