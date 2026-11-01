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
    path: '/signatures',
    name: 'signatures',
    component: () => import('@/views/pilotage/MySignaturesView.vue'),
  },
  {
    path: '/rapports',
    name: 'rapports',
    component: () => import('@/views/pilotage/RapportsView.vue'),
  },
  {
    path: '/mail-deposit',
    name: 'mail-deposit',
    component: () => import('@/views/pilotage/MailDepositView.vue'),
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
    meta: { roles: ['TENANT_ADMIN'] },
    component: () => import('@/views/admin/DossierTypesAdminView.vue'),
  },
  {
    path: '/admin/templates',
    name: 'admin-templates',
    meta: { roles: ['TENANT_ADMIN'] },
    component: () => import('@/views/admin/TemplatesAdminView.vue'),
  },
  {
    path: '/admin/connectors',
    name: 'admin-connectors',
    meta: { roles: ['TENANT_ADMIN'] },
    component: () => import('@/views/admin/ConnectorsAdminView.vue'),
  },
  {
    path: '/admin/workflow-types',
    name: 'admin-workflow-types',
    meta: { roles: ['TENANT_ADMIN', 'WORKFLOW_ADMIN'] },
    component: () => import('@/views/admin/WorkflowConfigsAdminView.vue'),
  },
  {
    path: '/admin/task-question-schemas',
    name: 'admin-task-question-schemas',
    meta: { roles: ['TENANT_ADMIN', 'WORKFLOW_ADMIN'] },
    component: () => import('@/views/admin/TaskQuestionSchemasAdminView.vue'),
  },
  {
    path: '/admin/bpmn-editor',
    name: 'admin-bpmn-editor',
    meta: { roles: ['TENANT_ADMIN', 'WORKFLOW_ADMIN'] },
    component: () => import('@/views/admin/BpmnEditorView.vue'),
  },
  {
    path: '/admin/dmn-editor',
    name: 'admin-dmn-editor',
    meta: { roles: ['TENANT_ADMIN', 'WORKFLOW_ADMIN'] },
    component: () => import('@/views/admin/DmnEditorView.vue'),
  },
  {
    path: '/admin/configuration',
    name: 'admin-configuration',
    meta: { roles: ['TENANT_ADMIN'] },
    component: () => import('@/views/admin/ConfigurationPromotionView.vue'),
  },
  {
    path: '/admin/assistant',
    name: 'admin-assistant',
    meta: { roles: ['TENANT_ADMIN', 'WORKFLOW_ADMIN'] },
    component: () => import('@/views/admin/AssistantView.vue'),
  },
  {
    path: '/admin/parcours',
    name: 'admin-parcours',
    meta: { roles: ['TENANT_ADMIN', 'WORKFLOW_ADMIN'] },
    component: () => import('@/views/admin/ParcoursView.vue'),
  },
  {
    path: '/admin/drafts',
    name: 'admin-drafts',
    meta: { roles: ['TENANT_ADMIN', 'WORKFLOW_ADMIN'] },
    component: () => import('@/views/admin/DraftsAdminView.vue'),
  },
  {
    path: '/admin/incidents',
    name: 'admin-incidents',
    meta: { roles: ['TENANT_ADMIN', 'WORKFLOW_ADMIN'] },
    component: () => import('@/views/admin/IncidentsAdminView.vue'),
  },
  {
    path: '/admin/purge-outbox',
    name: 'admin-purge-outbox',
    meta: { roles: ['TENANT_ADMIN'] },
    component: () => import('@/views/admin/PurgeOutboxAdminView.vue'),
  },
  {
    path: '/admin/observabilite',
    name: 'admin-observabilite',
    meta: { roles: ['TENANT_ADMIN'] },
    component: () => import('@/views/admin/ObservabilityAdminView.vue'),
  },
  {
    path: '/admin/user-groups',
    name: 'admin-user-groups',
    meta: { roles: ['TENANT_ADMIN'] },
    component: () => import('@/views/admin/UserGroupsAdminView.vue'),
  },
  {
    path: '/admin/notification-webhooks',
    name: 'admin-notification-webhooks',
    meta: { roles: ['TENANT_ADMIN'] },
    component: () => import('@/views/admin/NotificationWebhooksAdminView.vue'),
  },
]
