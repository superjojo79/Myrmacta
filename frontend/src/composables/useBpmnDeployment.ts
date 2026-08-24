import { ref } from 'vue'
import { deployBpmn, getBpmnDeploymentInfo } from '@/api/workflows'
import type { BpmnDeploymentResult, ProcessDeploymentInfo } from '@/api/types'
import { toProblemDetail } from '@/api/http'

// Deployment-confirmation flow shared by BpmnUploadPreview.vue and
// BpmnEditorView.vue (D-EDIT-DEPLOY-FLOW): fetch info for the target key,
// let the caller show it for confirmation, then POST the actual deployment.
// Extracted from BpmnUploadPreview.vue's original inline logic — behaviour
// unchanged, only relocated.
export function useBpmnDeployment() {
  const deploymentInfo = ref<ProcessDeploymentInfo | null>(null)
  const deployError = ref<string | null>(null)
  const deploying = ref(false)
  const deployedResult = ref<BpmnDeploymentResult | null>(null)

  async function loadInfo(processDefinitionKey: string): Promise<void> {
    try {
      deploymentInfo.value = await getBpmnDeploymentInfo(processDefinitionKey)
    } catch {
      deploymentInfo.value = null
    }
  }

  async function deploy(file: File): Promise<BpmnDeploymentResult | null> {
    deploying.value = true
    deployError.value = null
    try {
      const result = await deployBpmn(file)
      deployedResult.value = result
      return result
    } catch (err) {
      deployError.value = toProblemDetail(err).detail ?? 'Déploiement impossible'
      return null
    } finally {
      deploying.value = false
    }
  }

  function reset(): void {
    deploymentInfo.value = null
    deployError.value = null
    deployedResult.value = null
  }

  return { deploymentInfo, deployError, deploying, deployedResult, loadInfo, deploy, reset }
}
