import type fr from '../fr/workflow'

const workflow: typeof fr = {
  startDialog: {
    title: 'Start a workflow',
    useOther: 'Choose another configuration',
    defaultKey: 'Default key:',
    configuration: 'Configuration',
    start: 'Start',
    variables: {
      title: 'Start variables',
      hint: 'Text values passed to the workflow. A parameter written in the BPMN takes precedence over a variable with the same name.',
      name: 'Name',
      value: 'Value',
      add: 'Add a variable',
      remove: 'Remove the variable',
      errors: {
        invalid: 'Invalid name: letters, digits and _, not starting with a digit.',
        reserved: '“{name}” is reserved by the platform.',
        duplicate: '“{name}” is already set.',
      },
    },
  },
  incidents: {
    title: 'Failed steps',
    state: {
      INCIDENT: 'Incident',
      RETRYING: 'Retry scheduled ({n} left) | Retry scheduled ({n} left)',
    },
    since: 'since {date}',
    retry: 'Retry',
    retryStep: 'Retry: {step}',
    alreadyRetried: 'This incident has already been retried or is being resumed.',
    hintAdmin: 'Fix the cause (dossier data, configuration…) then retry the incident.',
    hintReader: 'An administrator must retry the step once the cause is fixed.',
    retryError: 'The retry failed.',
  },
  tasks: {
    title: 'Link tasks',
    empty: 'No open task.',
    assignee: 'Assignee: {name}',
    complete: 'Complete',
    reassign: 'Reassign',
    claim: 'Assign to me',
    you: 'you',
  },
  history: { title: 'History', ongoing: 'ongoing', empty: 'No activity yet.' },
  diagram: { title: 'Diagram', unavailable: 'Diagram unavailable (process not started or link without instance).' },
  upload: {
    title: 'Deploy a BPMN diagram',
    processKey: 'Process key:',
    currentVersion: 'Currently deployed version: {version} — active instances: {count}',
    none: 'No version deployed for this key yet.',
    deployed: 'Deployed: version {version} (deployment {id}).',
    deploying: 'Deploying…',
    confirm: 'Confirm the deployment',
    errors: {
      unreadable: 'Unreadable file.',
      notBpmn: 'File not recognised as a valid BPMN diagram (process id not found).',
    },
  },
  taskForms: {
    loadingForm: 'Loading the form…',
    complete: 'Complete',
    chooseDocument: 'Choose a document…',
    noTemplate: 'No template available for this dossier type.',
    errors: {
      templates: 'Unable to load the templates',
      form: 'Unable to load the form',
      fields: 'Unable to update the fields',
      invalidFormKey: 'Invalid formKey for the user-question kind',
    },
  },
}

export default workflow
