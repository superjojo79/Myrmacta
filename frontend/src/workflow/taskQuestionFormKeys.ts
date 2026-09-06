const PREFIX = 'user-question:'

/** Frontend mirror of the backend's TaskQuestionFormKeys (single source of truth for the convention). */
export function schemaKeyOf(formKey: string | null | undefined): string | null {
  if (!formKey || !formKey.startsWith(PREFIX)) {
    return null
  }
  return formKey.slice(PREFIX.length)
}
