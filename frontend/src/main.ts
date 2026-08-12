import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import PrimeVue from 'primevue/config'
import Aura from '@primevue/themes/aura'
import 'primeicons/primeicons.css'
import '@/styles/main.css'
import App from './App.vue'
import { configureHttpClient } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { routes } from '@/app/routes'

configureHttpClient()

const app = createApp(App)
const pinia = createPinia()
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

app.use(pinia)
app.use(router)
app.use(PrimeVue, {
  theme: {
    preset: Aura,
  },
})
app.mount('#app')

void useAuthStore().init()
