<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { shallowRef } from 'vue'
import { toProblemDetail } from '@/api/http'
import { enqueueDossierPurge } from '@/api/purgeOutbox'

const props = defineProps<{
  dossierId: string
}>()

const pending = shallowRef(false)
const error = shallowRef<string | null>(null)
const done = shallowRef(false)

async function requestPurge() {
  pending.value = true
  error.value = null
  try {
    await enqueueDossierPurge(props.dossierId)
    done.value = true
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('dossier.purge.error')
  } finally {
    pending.value = false
  }
}

const { t } = useI18n()
</script>

<template>
  <div class="dossier-purge-request">
    <button
      type="button"
      data-testid="dossier-purge-request"
      :disabled="pending"
      @click="requestPurge"
    >
      {{ t('dossier.purge.request') }}
    </button>
    <p v-if="done">{{ t('dossier.purge.done') }}</p>
    <p v-if="error" class="error">{{ error }}</p>
  </div>
</template>

<style scoped>
.error {
  color: #c92a2a;
}
</style>
