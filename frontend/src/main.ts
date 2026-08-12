import { createApp } from 'vue'
import { createPinia } from 'pinia'
import '@/styles/main.css'
import App from './App.vue'
import { configureHttpClient } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

configureHttpClient()

const app = createApp(App)
app.use(createPinia())
app.mount('#app')

void useAuthStore().init()
