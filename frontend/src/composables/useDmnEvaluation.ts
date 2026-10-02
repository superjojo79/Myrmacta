import { ref, shallowRef } from 'vue'
import { i18n } from '@/i18n'
import {
  evaluateDecision,
  listDecisionTableVersions,
  redeployDecisionTable,
  testDecisionCsv,
  testDecisionJson,
} from '@/api/workflows'
import type {
  DecisionEvaluationResult,
  DecisionTableDeploymentResult,
  DecisionTableVersionInfo,
  DecisionTestCase,
  DecisionTestReport,
} from '@/api/types'
import { toProblemDetail } from '@/api/http'

export function useDmnEvaluation() {
  const versions = ref<DecisionTableVersionInfo[]>([])
  const evaluation = ref<DecisionEvaluationResult | null>(null)
  const report = ref<DecisionTestReport | null>(null)
  const redeployed = ref<DecisionTableDeploymentResult | null>(null)
  const error = shallowRef<string | null>(null)
  const pendingRedeployVersion = shallowRef<number | null>(null)
  const loading = shallowRef(false)

  async function loadVersions(decisionKey: string): Promise<void> {
    loading.value = true
    error.value = null
    try {
      versions.value = await listDecisionTableVersions(decisionKey)
    } catch (err) {
      versions.value = []
      error.value = toProblemDetail(err).detail ?? i18n.global.t('common.errors2.history')
    } finally {
      loading.value = false
    }
  }

  async function evaluate(
    decisionKey: string,
    inputsJson: string,
    version: number | null,
  ): Promise<void> {
    error.value = null
    evaluation.value = null
    let inputs: Record<string, unknown>
    try {
      const parsed: unknown = JSON.parse(inputsJson)
      if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
        throw new Error('objet attendu')
      }
      inputs = parsed as Record<string, unknown>
    } catch {
      error.value = i18n.global.t('common.errors2.inputsObject')
      return
    }
    loading.value = true
    try {
      evaluation.value = await evaluateDecision(decisionKey, inputs, version)
    } catch (err) {
      error.value = toProblemDetail(err).detail ?? i18n.global.t('common.errors.evaluate')
    } finally {
      loading.value = false
    }
  }

  async function runSuite(decisionKey: string, file: File, version: number | null): Promise<void> {
    error.value = null
    report.value = null
    loading.value = true
    try {
      const text = await file.text()
      if (file.name.toLowerCase().endsWith('.csv')) {
        report.value = await testDecisionCsv(decisionKey, text, version)
        return
      }
      const parsed: unknown = JSON.parse(text)
      const cases = suiteCases(parsed)
      report.value = await testDecisionJson(decisionKey, cases, version)
    } catch (err) {
      if (err instanceof SyntaxError) {
        error.value = 'Jeu de tests JSON illisible.'
        return
      }
      error.value = toProblemDetail(err).detail ?? i18n.global.t('common.errors.suite')
    } finally {
      loading.value = false
    }
  }

  function askRedeploy(version: number): void {
    pendingRedeployVersion.value = version
    error.value = null
  }

  function cancelRedeploy(): void {
    pendingRedeployVersion.value = null
  }

  async function confirmRedeploy(decisionKey: string): Promise<void> {
    const version = pendingRedeployVersion.value
    if (version == null) return
    loading.value = true
    error.value = null
    try {
      redeployed.value = await redeployDecisionTable(decisionKey, version)
      pendingRedeployVersion.value = null
      await loadVersions(decisionKey)
    } catch (err) {
      error.value = toProblemDetail(err).detail ?? i18n.global.t('common.errors.redeploy')
    } finally {
      loading.value = false
    }
  }

  return {
    versions,
    evaluation,
    report,
    redeployed,
    error,
    pendingRedeployVersion,
    loading,
    loadVersions,
    evaluate,
    runSuite,
    askRedeploy,
    cancelRedeploy,
    confirmRedeploy,
  }
}

function suiteCases(parsed: unknown): DecisionTestCase[] {
  if (parsed !== null && typeof parsed === 'object' && 'cases' in parsed) {
    const cases = (parsed as { cases: unknown }).cases
    if (Array.isArray(cases)) {
      return cases as DecisionTestCase[]
    }
  }
  throw new Error('cases manquants')
}
