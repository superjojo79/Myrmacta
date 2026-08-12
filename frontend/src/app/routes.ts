import type { RouteRecordRaw } from 'vue-router'

export const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/supervision' },
  {
    path: '/supervision',
    name: 'supervision',
    component: () => import('@/views/pilotage/SupervisionView.vue'),
  },
  {
    path: '/tasks',
    name: 'tasks',
    component: () => import('@/views/pilotage/MobileTasksView.vue'),
  },
  {
    path: '/dossiers/new',
    name: 'dossier-create',
    component: () => import('@/views/pilotage/CreateDossierView.vue'),
  },
  {
    path: '/dossiers/:id',
    name: 'dossier-detail',
    component: () => import('@/views/pilotage/DossierDetailView.vue'),
  },
  {
    path: '/admin/dossier-types',
    name: 'admin-dossier-types',
    component: () => import('@/views/admin/DossierTypesAdminView.vue'),
  },
  {
    path: '/admin/templates',
    name: 'admin-templates',
    component: () => import('@/views/admin/TemplatesAdminView.vue'),
  },
  {
    path: '/admin/connectors',
    name: 'admin-connectors',
    component: () => import('@/views/admin/ConnectorsAdminView.vue'),
  },
  {
    path: '/admin/workflow-types',
    name: 'admin-workflow-types',
    component: () => import('@/views/admin/WorkflowConfigsAdminView.vue'),
  },
]
