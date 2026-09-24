// TODO §68 (portail-externe) — page publique, build Vite séparé (D-PORTAL-UI-BUILD) : aucune
// dépendance au SPA interne (store d'auth, routeur, client axios) — règle ESLint dédiée.
import { createApp } from 'vue'
import './portal.css'
import PortalApp from './PortalApp.vue'
import { takeTokenFromLocation } from './token'

createApp(PortalApp, { token: takeTokenFromLocation(window.location, window.history) }).mount('#portal')
