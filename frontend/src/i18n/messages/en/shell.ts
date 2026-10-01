import type fr from '../fr/shell'

const shell: typeof fr = {
  brand: 'FlowForge',
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
    types: 'Types',
    templates: 'Templates',
    connectors: 'Connectors',
    promotion: 'Promotion',
    purgeQueue: 'Purge queue',
    observability: 'Observability',
    webhooks: 'Webhooks',
    assistant: 'Assistant',
    parcours: 'Journeys',
    drafts: 'Drafts',
    workflows: 'Workflows',
    taskQuestions: 'Task questions',
    decisionTables: 'Decision tables',
  },
}

export default shell
