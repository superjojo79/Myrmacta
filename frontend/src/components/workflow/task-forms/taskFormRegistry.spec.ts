import { describe, expect, it } from 'vitest'
import { resolveTaskFormComponent } from './taskFormRegistry'
import DossierFieldsTaskForm from './DossierFieldsTaskForm.vue'
import DocumentGenerationTaskForm from './DocumentGenerationTaskForm.vue'
import UserQuestionTaskForm from './UserQuestionTaskForm.vue'

describe('resolveTaskFormComponent', () => {
  it('resolves dossier-fields to DossierFieldsTaskForm', () => {
    expect(resolveTaskFormComponent('dossier-fields')).toBe(DossierFieldsTaskForm)
  })

  it('resolves document-generation to DocumentGenerationTaskForm', () => {
    expect(resolveTaskFormComponent('document-generation')).toBe(DocumentGenerationTaskForm)
  })

  it('resolves any user-question:<schemaKey> to UserQuestionTaskForm', () => {
    expect(resolveTaskFormComponent('user-question:orientation-dossier')).toBe(UserQuestionTaskForm)
    expect(resolveTaskFormComponent('user-question:other-schema')).toBe(UserQuestionTaskForm)
  })

  it('returns null for a task without a formKey', () => {
    expect(resolveTaskFormComponent(null)).toBeNull()
    expect(resolveTaskFormComponent(undefined)).toBeNull()
  })

  it('returns null for an unknown kind (degrades to the blind complete button)', () => {
    expect(resolveTaskFormComponent('some-future-kind')).toBeNull()
  })

  it.each(['toString', 'constructor', 'hasOwnProperty', 'valueOf', '__proto__'])(
    'returns null for %s -- an Object.prototype key must not be treated as a registered kind',
    (formKey) => {
      expect(resolveTaskFormComponent(formKey)).toBeNull()
    },
  )
})
