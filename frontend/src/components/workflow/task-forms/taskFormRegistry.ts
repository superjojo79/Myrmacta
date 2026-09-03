import type { Component } from 'vue'
import DossierFieldsTaskForm from './DossierFieldsTaskForm.vue'
import DocumentGenerationTaskForm from './DocumentGenerationTaskForm.vue'

// TODO §52 (formulaires-dynamiques-taches) -- D-FORM-KIND-SCOPE: the backend never interprets
// formKey (opaque string, see MyWorkflowTask.formKey/WorkflowTaskView.formKey). This registry is
// the single place that knows the current 2 kinds; adding a 3rd later is an entry here + a
// component, no backend change. Each registered component receives a `dossierId` prop and emits
// `ready(variables)` once its kind-specific pre-completion step succeeds, or `error(message)`.
// Object.create(null) (no prototype) so a `formKey` equal to an inherited Object.prototype key
// ("toString", "constructor", "hasOwnProperty", "__proto__", ...) can never resolve to something
// truthy through the plain-object lookup below -- found in Phase 9 adversarial review: a
// `flowable:formKey` set to one of those breaks task-list rendering (Vue render errors, or a
// vanished "Compléter" button) instead of degrading to the blind-complete default.
const REGISTRY: Record<string, Component> = Object.assign(Object.create(null), {
  'dossier-fields': DossierFieldsTaskForm,
  'document-generation': DocumentGenerationTaskForm,
})

/** Unknown/absent formKey degrades to the caller's default blind "Compléter" button (D-FORM-BACKWARD-COMPAT). */
export function resolveTaskFormComponent(formKey: string | null | undefined): Component | null {
  if (!formKey) return null
  return REGISTRY[formKey] ?? null
}
