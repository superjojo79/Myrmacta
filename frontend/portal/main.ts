// TODO §68 (portail-externe) — page publique, build Vite séparé (D-PORTAL-UI-BUILD) : aucune
// dépendance au SPA interne (store d'auth, routeur, client axios) — règle ESLint dédiée.
import { createApp } from 'vue'
import './portal.css'
import PortalApp from './PortalApp.vue'
import { takeLinkFromLocation } from './token'

const link = takeLinkFromLocation(window.location, window.history)

createApp(PortalApp, { token: link?.token ?? null, kind: link?.kind ?? 'task' }).mount('#portal')
