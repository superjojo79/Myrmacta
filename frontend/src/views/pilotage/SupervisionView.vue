<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useDossierStore } from '@/stores/dossier'
import { toProblemDetail } from '@/api/http'

const router = useRouter()
const store = useDossierStore()
const query = ref('')
const error = ref<string | null>(null)
const loading = ref(false)

async function openDossier() {
  error.value = null
  const value = query.value.trim()
  if (!value) {
    error.value = 'Saisir une référence ou un identifiant'
    return
  }
  loading.value = true
  try {
    if (/^[0-9a-f-]{36}$/i.test(value)) {
      await store.loadById(value)
    } else {
      await store.loadByRef(value)
    }
    if (store.dossier) await router.push(`/dossiers/${store.dossier.id}`)
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? 'Dossier introuvable'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="supervision">
    <h1>Supervision</h1>
    <p>Ouvrir un dossier par référence ou identifiant.</p>
    <form class="supervision__form" @submit.prevent="openDossier">
      <input v-model="query" type="text" placeholder="DOS-… ou UUID" />
      <button type="submit" :disabled="loading">Ouvrir</button>
      <RouterLink class="supervision__create" to="/dossiers/new">Créer un dossier</RouterLink>
    </form>
    <p v-if="error" class="supervision__error">{{ error }}</p>
  </section>
</template>

<style scoped>
.supervision__form {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  align-items: center;
  margin-top: 1rem;
}
input {
  min-width: 240px;
  padding: 0.45rem 0.6rem;
}
button {
  background: var(--color-accent);
  color: white;
  border: 0;
  padding: 0.45rem 0.8rem;
  cursor: pointer;
}
.supervision__create {
  color: var(--color-accent);
}
.supervision__error {
  color: #c92a2a;
}
</style>
