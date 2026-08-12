import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
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
app.mount('#app')

void useAuthStore().init()
