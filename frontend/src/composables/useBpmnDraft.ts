import { onBeforeUnmount, ref } from 'vue'
import { getBpmnDraft, getDeployedBpmnXml, saveBpmnDraft } from '@/api/workflows'

// Draft load/save + "unsaved changes" tracking for the BPMN editor
// (D-EDIT-UNSAVED): no autosave — the draft is only persisted on an explicit
// save() call, and beforeunload only warns while hasUnsavedChanges is true.
export function useBpmnDraft() {
  const xml = ref<string | null>(null)
  const loading = ref(false)
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

  async function load(processDefinitionKey: string): Promise<void> {
    loading.value = true
    try {
      const draft = await getBpmnDraft(processDefinitionKey)
      xml.value = draft ? draft.xml : await getDeployedBpmnXml(processDefinitionKey)
    } finally {
      loading.value = false
    }
    hasUnsavedChanges.value = false
  }

  async function save(processDefinitionKey: string, newXml: string): Promise<void> {
    await saveBpmnDraft(processDefinitionKey, newXml)
    xml.value = newXml
    hasUnsavedChanges.value = false
  }

  function markDirty(): void {
    hasUnsavedChanges.value = true
  }

  return { xml, loading, hasUnsavedChanges, load, save, markDirty }
}
