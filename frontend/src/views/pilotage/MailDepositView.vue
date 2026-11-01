<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { toProblemDetail } from '@/api/http'
import {
  depositMail,
  listDepositPoints,
  MAX_DEPOSIT_FILES,
  type DepositPoint,
  type DepositResult,
} from '@/api/mailDeposits'

/** TODO §113 (D-MAILROOM-DEPOSIT) — un agent dépose des PDF déjà numérisés ; chacun devient un courrier qui suit le tri. */
const { t } = useI18n()
const points = ref<DepositPoint[]>([])
const pointId = ref('')
const files = ref<File[]>([])
const expediteur = ref('')
const objet = ref('')
const loading = ref(true)
const sending = ref(false)
const error = ref<string | null>(null)
const results = ref<DepositResult[]>([])

const tooMany = computed(() => files.value.length > MAX_DEPOSIT_FILES)
const canSend = computed(() => pointId.value !== '' && files.value.length > 0 && !tooMany.value && !sending.value)

onMounted(async () => {
  try {
    points.value = await listDepositPoints()
    if (points.value.length === 1) pointId.value = points.value[0]!.id
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('mailDeposit.errors.load')
  } finally {
    loading.value = false
  }
})

function onFiles(event: Event) {
  const input = event.target as HTMLInputElement
  files.value = Array.from(input.files ?? [])
}

async function send() {
  if (!canSend.value) return
  sending.value = true
  error.value = null
  results.value = []
  try {
    results.value = await depositMail(pointId.value, files.value, { expediteur: expediteur.value, objet: objet.value })
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('mailDeposit.errors.send')
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <section class="mail-deposit" data-testid="mail-deposit">
    <h1>{{ t('mailDeposit.title') }}</h1>
    <p class="hint">{{ t('mailDeposit.hint') }}</p>
    <p v-if="error" class="error" role="alert" data-testid="mail-deposit-error">{{ error }}</p>
    <p v-if="!loading && points.length === 0 && !error" data-testid="mail-deposit-no-point">{{ t('mailDeposit.noPoint') }}</p>
    <form v-if="points.length > 0" class="mail-deposit__form" @submit.prevent="send">
      <label>
        {{ t('mailDeposit.point') }}
        <select v-model="pointId" data-testid="mail-deposit-point" required>
          <option value="" disabled>{{ t('mailDeposit.choose') }}</option>
          <option v-for="point in points" :key="point.id" :value="point.id">{{ point.name }} ({{ point.dossierTypeCode }})</option>
        </select>
      </label>
      <label>
        {{ t('mailDeposit.files') }}
        <input type="file" accept="application/pdf,.pdf" multiple data-testid="mail-deposit-files" @change="onFiles" />
      </label>
      <p v-if="tooMany" class="error" data-testid="mail-deposit-too-many">{{ t('mailDeposit.tooMany', { max: MAX_DEPOSIT_FILES }) }}</p>
      <label>
        {{ t('mailDeposit.sender') }}
        <input v-model="expediteur" type="text" maxlength="200" data-testid="mail-deposit-sender" />
      </label>
      <label>
        {{ t('mailDeposit.subject') }}
        <input v-model="objet" type="text" maxlength="200" :placeholder="t('mailDeposit.subjectHint')" data-testid="mail-deposit-subject" />
      </label>
      <button type="submit" :disabled="!canSend" data-testid="mail-deposit-send">
        {{ sending ? t('mailDeposit.sending') : t('mailDeposit.send') }}
      </button>
    </form>
    <table v-if="results.length > 0" class="mail-deposit__results" data-testid="mail-deposit-results">
      <thead>
        <tr>
          <th>{{ t('mailDeposit.file') }}</th>
          <th>{{ t('mailDeposit.outcome') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(result, index) in results" :key="index" data-testid="mail-deposit-result">
          <td>{{ result.filename }}</td>
          <td v-if="result.dossierId">
            <RouterLink :to="`/dossiers/${result.dossierId}`">{{ result.reference ?? result.dossierId }}</RouterLink>
          </td>
          <td v-else class="error">{{ t('mailDeposit.refused', { reason: result.refusal }) }}</td>
        </tr>
      </tbody>
    </table>
  </section>
</template>

<style scoped>
.mail-deposit__form {
  display: grid;
  gap: 0.75rem;
  max-width: 36rem;
}
.mail-deposit__form label {
  display: grid;
  gap: 0.25rem;
}
.mail-deposit__results {
  margin-top: 1rem;
  border-collapse: collapse;
}
.mail-deposit__results th,
.mail-deposit__results td {
  padding: 0.3rem 0.6rem;
  text-align: left;
}
.hint {
  opacity: 0.75;
}
.error {
  color: #c92a2a;
}
</style>
