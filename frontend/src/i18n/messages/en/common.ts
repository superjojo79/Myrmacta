import type fr from '../fr/common'

const common: typeof fr = {
  language: 'Language',
  languages: { fr: 'Français', en: 'English' },
  save: 'Save',
  cancel: 'Cancel',
  close: 'Close',
  delete: 'Delete',
  edit: 'Edit',
  create: 'Create',
  loading: 'Loading…',
  none: '—',
  yes: 'Yes',
  no: 'No',
  previous: 'Previous',
  next: 'Next',
  retry: 'Retry',
  search: 'Search',
  actions: 'Actions',
  pagination: 'Pagination',
  notifications: {
    bell: 'Notifications',
    streamFailed: 'Real-time feed unavailable',
    title: 'Notifications',
    markAllRead: 'Mark all read',
    empty: 'No notification.',
    loadMore: 'Load more',
  },
  errors: {
    tasks: 'Unable to load the tasks',
    dossier: 'Unable to load the dossier',
    deploy: 'Deployment failed',
    previewHtml: 'HTML preview failed',
    previewPdf: 'PDF preview failed',
    evaluate: 'Evaluation failed',
    suite: 'Test suite failed',
    redeploy: 'Redeployment failed',
  },
  errors2: {
    history: 'History unavailable',
    observability: 'Unable to load observability',
    inputsObject: 'Inputs must be a JSON object.',
  },
}

export default common
