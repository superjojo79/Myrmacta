import type fr from '../fr/shell'

const shell: typeof fr = {
  brand: 'Myrmacta',
  tagline: 'Collective intelligence for your cases and decisions',
  navLabel: 'Operations',
  sessionError: 'Unable to load the session',
  session: 'Session',
  logout: 'Log out',
  sections: { pilotage: 'Operations', admin: 'Admin' },
  nav: {
    supervision: 'Supervision',
    tasks: 'Tasks',
    signatures: 'My signatures',
    reports: 'Reports & SLA',
    newDossier: 'New dossier',
    mailDeposit: 'Deposit mail',
    types: 'Types',
    templates: 'Templates',
    notificationTemplates: 'Notification templates',
    connectors: 'Connectors',
    promotion: 'Promotion',
    purgeQueue: 'Purge queue',
    observability: 'Observability',
    userGroups: 'Groups',
    webhooks: 'Webhooks',
    assistant: 'Assistant',
    parcours: 'Journeys',
    drafts: 'Drafts',
    incidents: 'Incidents',
    workflows: 'Workflows',
    taskQuestions: 'Task questions',
    decisionTables: 'Decision tables',
  },
}

export default shell
