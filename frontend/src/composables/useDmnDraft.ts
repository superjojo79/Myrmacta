import { onBeforeUnmount, ref } from 'vue'
import { getDmnDraft, getDeployedDmnXml, saveDmnDraft } from '@/api/workflows'

// Mirrors useBpmnDraft.ts (D-EDIT-UNSAVED reconduit, design D5) — draft
// load/save + "unsaved changes" tracking for the DMN editor: no autosave,
// the draft is only persisted on an explicit save() call, beforeunload
// only warns while hasUnsavedChanges is true.
export function useDmnDraft() {
  const xml = ref<string | null>(null)
  const loading = ref(false)
  const loadError = ref<string | null>(null)
  const hasUnsavedChanges = ref(false)

  function beforeUnloadHandler(event: BeforeUnloadEvent) {
    if (hasUnsavedChanges.value) {
      event.preventDefault()
      event.returnValue = ''
    }
  }

  window.addEventListener('beforeunload', beforeUnloadHandler)
  onBeforeUnmount(() => {
    window.removeEventListener('beforeunload', beforeUnloadHandler)
  })

  async function load(decisionKey: string): Promise<void> {
    loading.value = true
    loadError.value = null
    try {
      const draft = await getDmnDraft(decisionKey)
      xml.value = draft ? draft.xml : await getDeployedDmnXml(decisionKey)
      hasUnsavedChanges.value = false
    } catch (error) {
      loadError.value = error instanceof Error ? error.message : String(error)
    } finally {
      loading.value = false
    }
  }

  async function save(decisionKey: string, newXml: string): Promise<void> {
    await saveDmnDraft(decisionKey, newXml)
    xml.value = newXml
    hasUnsavedChanges.value = false
  }

  function markDirty(): void {
    hasUnsavedChanges.value = true
  }

  return { xml, loading, loadError, hasUnsavedChanges, load, save, markDirty }
}
