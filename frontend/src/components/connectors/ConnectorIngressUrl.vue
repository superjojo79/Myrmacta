<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ConnectorIngress } from './connectorConfig'

const props = defineProps<{
  kind: ConnectorIngress['kind']
  url: string
}>()

const copied = ref(false)

const title = computed(() => (props.kind === 'callback' ? 'URL callback' : 'URL webhook'))

async function copy() {
  copied.value = false
  try {
    await navigator.clipboard.writeText(props.url)
    copied.value = true
  } catch {
    copied.value = false
  }
}
</script>

<template>
  <div class="connector-ingress" data-testid="connector-ingress">
    <span class="connector-ingress__label">{{ title }}</span>
    <code class="connector-ingress__url" data-testid="connector-ingress-url">POST {{ url }}</code>
    <button
      type="button"
      class="connector-ingress__copy"
      data-testid="connector-ingress-copy"
      :aria-label="`Copier ${title}`"
      @click="copy"
    >
      {{ copied ? 'Copié' : 'Copier' }}
    </button>
  </div>
</template>

<style scoped>
.connector-ingress {
  display: grid;
  gap: 0.25rem;
  margin-top: 0.35rem;
  font-size: 0.85rem;
}

.connector-ingress__label {
  opacity: 0.8;
}

.connector-ingress__url {
  display: block;
  overflow-wrap: anywhere;
  font-size: 0.8rem;
}

.connector-ingress__copy {
  justify-self: start;
}
</style>
