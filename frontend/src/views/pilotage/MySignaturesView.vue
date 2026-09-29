<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { toProblemDetail } from '@/api/http'
import {
  declineMine,
  fetchMySignatureDocument,
  listMySignatures,
  signMine,
  type MySignatureItem,
} from '@/api/signatures'

// TODO §72 US-10 — signatures internes qui me sont adressées. « À mon tour » (actives) puis
// « En attente » (séquentiel : un signataire précédent doit d'abord décider).
const items = ref<MySignatureItem[]>([])
const loading = ref(true)
const unavailable = ref(false)
const busyId = ref<string | null>(null)
const decliningId = ref<string | null>(null)
const reason = ref('')
const error = ref('')

const myTurn = () => items.value.filter((i) => i.myTurn)
const waiting = () => items.value.filter((i) => !i.myTurn)

function formatDate(value: string | null): string {
  return value ? new Date(value).toLocaleDateString('fr-FR', { dateStyle: 'medium' }) : '—'
}

async function load() {
  try {
    items.value = await listMySignatures()
  } catch {
    items.value = []
    unavailable.value = true
  } finally {
    loading.value = false
  }
}

async function openDocument(item: MySignatureItem) {
  error.value = ''
  try {
    const url = URL.createObjectURL(await fetchMySignatureDocument(item.signerId))
    window.open(url, '_blank', 'noopener')
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  } catch (e) {
    error.value = toProblemDetail(e).detail ?? 'Document indisponible.'
  }
}

async function decide(item: MySignatureItem, action: () => Promise<unknown>) {
  busyId.value = item.signerId
  error.value = ''
  try {
    await action()
    decliningId.value = null
    reason.value = ''
    await load()
  } catch (e) {
    error.value = toProblemDetail(e).detail ?? 'Action impossible.'
    await load()
  } finally {
    busyId.value = null
  }
}

onMounted(load)
</script>

<template>
  <section class="my-signatures">
    <h1>Mes signatures</h1>
    <p v-if="loading">Chargement…</p>
    <p v-else-if="unavailable" class="empty" data-testid="signatures-unavailable">
      La signature électronique n’est pas activée.
    </p>
    <template v-else>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <p v-if="!items.length" class="empty" data-testid="signatures-empty">Aucune signature en attente.</p>

      <template v-for="group in [{ title: 'À mon tour', list: myTurn(), active: true }, { title: 'En attente', list: waiting(), active: false }]" :key="group.title">
        <section v-if="group.list.length" :data-testid="group.active ? 'signatures-active' : 'signatures-waiting'">
          <h2>{{ group.title }}</h2>
          <ul class="my-signatures__list">
            <li v-for="item in group.list" :key="item.signerId">
              <div>
                <strong>{{ item.documentLabel }}</strong>
                <span>
                  Dossier {{ item.dossierReference }} · échéance {{ formatDate(item.deadlineAt) }}
                  <em v-if="!item.required">(facultatif)</em>
                  <em v-if="item.simulated" class="my-signatures__simulation">SIMULATION — sans valeur juridique</em>
                </span>
              </div>
              <div class="my-signatures__actions">
                <RouterLink :to="`/dossiers/${item.dossierId}`">Dossier</RouterLink>
                <button type="button" @click="openDocument(item)">Consulter</button>
                <template v-if="group.active">
                  <template v-if="decliningId !== item.signerId">
                    <button type="button" :disabled="busyId === item.signerId" @click="decide(item, () => signMine(item.signerId))">
                      Signer
                    </button>
                    <button type="button" :disabled="busyId === item.signerId" @click="decliningId = item.signerId">
                      Refuser
                    </button>
                  </template>
                  <template v-else>
                    <input v-model="reason" type="text" maxlength="500" placeholder="Motif (facultatif)" aria-label="Motif du refus" />
                    <button
                      type="button"
                      :disabled="busyId === item.signerId"
                      @click="decide(item, () => declineMine(item.signerId, reason))"
                    >
                      Confirmer le refus
                    </button>
                    <button type="button" @click="decliningId = null">Annuler</button>
                  </template>
                </template>
              </div>
            </li>
          </ul>
        </section>
      </template>
    </template>
  </section>
</template>

<style scoped>
.my-signatures__list {
  list-style: none;
  padding: 0;
}
.my-signatures__list li {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.6rem 0;
  border-bottom: 1px solid #ddd;
}
.my-signatures__list span {
  display: block;
  font-size: 0.9rem;
  color: #556;
}
.my-signatures__actions {
  display: flex;
  gap: 0.4rem;
  align-items: center;
}
.my-signatures__simulation {
  margin-left: 0.5rem;
  font-weight: 700;
  color: #8a5a00;
}
</style>
