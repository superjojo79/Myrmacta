import { ref } from 'vue'
import { i18n } from '@/i18n'
import { deployDecisionTable, getDecisionTableDeploymentInfo } from '@/api/workflows'
import type { DecisionTableDeploymentResult, DecisionTableDeploymentInfo } from '@/api/types'
import { toProblemDetail } from '@/api/http'

// Mirrors useBpmnDeployment.ts (deployment-confirmation flow, D-EDIT-DEPLOY-FLOW).
export function useDmnDeployment() {
  const deploymentInfo = ref<DecisionTableDeploymentInfo | null>(null)
  const deployError = ref<string | null>(null)
  const deploying = ref(false)
  const deployedResult = ref<DecisionTableDeploymentResult | null>(null)

  async function loadInfo(decisionKey: string): Promise<void> {
    try {
      deploymentInfo.value = await getDecisionTableDeploymentInfo(decisionKey)
    } catch {
      deploymentInfo.value = null
    }
  }

  async function deploy(file: File): Promise<DecisionTableDeploymentResult | null> {
    deploying.value = true
    deployError.value = null
    try {
      const result = await deployDecisionTable(file)
      deployedResult.value = result
      return result
    } catch (err) {
      deployError.value = toProblemDetail(err).detail ?? i18n.global.t('common.errors.deploy')
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
