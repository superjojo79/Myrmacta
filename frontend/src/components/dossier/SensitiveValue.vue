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
const revealed = computed(() => !props.sensitive || isTenantAdmin.value)
const display = computed(() => (revealed.value ? String(props.value ?? '') : '••••'))
</script>

<template>
  <!-- TODO §76 : le contenu par défaut (ex. tableau des lignes d'un groupe) n'est rendu que si la valeur est visible. -->
  <span class="sensitive-value"><slot v-if="revealed">{{ display }}</slot><template v-else>{{ display }}</template></span>
</template>
