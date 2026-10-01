<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { computed, onMounted, ref, shallowRef } from 'vue'
import { useRoute } from 'vue-router'
import DmnEditorCanvas from '@/components/workflow/DmnEditorCanvas.vue'
import { useDmnDraft } from '@/composables/useDmnDraft'
import { useDmnDeployment } from '@/composables/useDmnDeployment'
import { useDmnEvaluation } from '@/composables/useDmnEvaluation'

const route = useRoute()
const draft = useDmnDraft()
const deployment = useDmnDeployment()
const evaluation = useDmnEvaluation()

const canvasRef = ref<InstanceType<typeof DmnEditorCanvas> | null>(null)
const importedXml = ref<string | null>(null)
const readError = ref<string | null>(null)
const saveError = ref<string | null>(null)
const deployPending = ref(false)
const testerOpen = shallowRef(false)
const suiteOpen = shallowRef(false)
const historyOpen = shallowRef(false)
const selectedVersion = shallowRef('')
const inputsJson = shallowRef('{}')
const toolingKey = shallowRef<string | null>(null)
// Mirrors BpmnEditorView.vue : only mount the canvas once the initial content
// (draft/deployed/blank) is known, avoids a blank-then-reimport flicker/race.
const ready = ref(false)

const decisionKey = computed(() => {
  const raw = route.query.decisionKey
  return typeof raw === 'string' && raw.length > 0 ? raw : null
})

const canvasXml = computed(() => importedXml.value ?? draft.xml.value ?? '')

onMounted(async () => {
  if (decisionKey.value) {
    await draft.load(decisionKey.value)
  }
  ready.value = true
})

// Phase 7 gap : contrairement à l'éditeur BPMN (accessible via le lien "Éditer" de
// WorkflowConfigsAdminView, ?processDefinitionKey=...), aucun écran de la SPA ne liste les tables
// de décision déployées ni ne lie vers /admin/dmn-editor?decisionKey=... -- le 3ᵉ point d'entrée
// ("édition d'une table déjà déployée") promis par le design D-DMN-DEPLOY n'était donc jamais
// atteignable depuis l'UI, uniquement en tapant l'URL à la main. Une liste dédiée serait une
// nouvelle fonctionnalité (hors scope M9, déjà livré) ; ce champ ferme l'accès manquant sans en
// ajouter une, en réutilisant le mécanisme de chargement de brouillon déjà écrit.
const loadKeyInput = ref('')

async function loadExistingTable() {
  const key = loadKeyInput.value.trim()
  if (!key) return
  importedXml.value = null
  await draft.load(key)
}

function extractDecisionKey(xml: string): string | null {
  const match = xml.match(/<(?:[\w-]+:)?decision\b[^>]*\bid\s*=\s*["']([^"']+)["']/)
  return match ? match[1] : null
}

async function onFileChange(event: Event) {
  readError.value = null
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  if (!file) return

  let text: string
  try {
    text = await file.text()
  } catch {
    readError.value = t('admin.dmnEditor.errors.unreadable')
    return
  }

  importedXml.value = text
  draft.markDirty()
}

function onCanvasDirty() {
  draft.markDirty()
}

async function saveDraft() {
  if (!canvasRef.value) return
  saveError.value = null
  const xml = await canvasRef.value.saveXML()
  const key = extractDecisionKey(xml)
  if (!key) {
    saveError.value = t('admin.dmnEditor.errors.notDmn')
    return
  }
  await draft.save(key, xml)
}

async function prepareDeploy() {
  if (!canvasRef.value) return
  saveError.value = null
  const xml = await canvasRef.value.saveXML()
  const key = extractDecisionKey(xml)
  if (!key) {
    saveError.value = t('admin.dmnEditor.errors.notDmn')
    return
  }
  deployPending.value = true
  await deployment.loadInfo(key)
}

async function confirmDeploy() {
  if (!canvasRef.value) return
  const xml = await canvasRef.value.saveXML()
  const key = extractDecisionKey(xml)
  if (!key) return
  const file = new File([xml], `${key}.dmn`, { type: 'application/xml' })
  await deployment.deploy(file)
}

async function currentDecisionKey(): Promise<string | null> {
  if (canvasRef.value) {
    const xml = await canvasRef.value.saveXML()
    const key = extractDecisionKey(xml)
    if (key) return key
  }
  return decisionKey.value
}

function chosenVersion(): number | null {
  if (selectedVersion.value === '') return null
  const parsed = Number(selectedVersion.value)
  return Number.isInteger(parsed) ? parsed : null
}

async function openTester() {
  testerOpen.value = true
  const key = await currentDecisionKey()
  toolingKey.value = key
  if (key) await evaluation.loadVersions(key)
}

async function openSuite() {
  suiteOpen.value = true
  const key = await currentDecisionKey()
  toolingKey.value = key
  if (key) await evaluation.loadVersions(key)
}

async function openHistory() {
  historyOpen.value = true
  const key = await currentDecisionKey()
  toolingKey.value = key
  if (key) await evaluation.loadVersions(key)
}

async function runEvaluation() {
  if (!toolingKey.value) return
  await evaluation.evaluate(toolingKey.value, inputsJson.value, chosenVersion())
}

async function onSuiteFile(event: Event) {
  if (!toolingKey.value) return
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  await evaluation.runSuite(toolingKey.value, file, chosenVersion())
}

function askRedeploy(version: number) {
  evaluation.askRedeploy(version)
}

async function confirmRedeploy() {
  if (!toolingKey.value) return
  await evaluation.confirmRedeploy(toolingKey.value)
}

const { t } = useI18n()
</script>

<template>
  <section class="dmn-editor">
    <h1>{{ t('admin.dmnEditor.title') }}</h1>

    <div class="dmn-editor__toolbar">
      <label>
        {{ t('admin.dmnEditor.importFile') }}
        <input type="file" accept=".xml,.dmn" @change="onFileChange" />
      </label>
      <label>
        {{ t('admin.dmnEditor.loadExisting') }}
        <input
          v-model="loadKeyInput"
          type="text"
          :placeholder="t('admin.dmnEditor.keyPlaceholder')"
          @keyup.enter="loadExistingTable"
        />
      </label>
      <button type="button" class="dmn-editor__load" @click="loadExistingTable">{{ t('admin.dmnEditor.load') }}</button>
      <button type="button" class="dmn-editor__save" @click="saveDraft">{{ t('admin.dmnEditor.saveDraft') }}</button>
      <button type="button" class="dmn-editor__deploy" @click="prepareDeploy">{{ t('admin.dmnEditor.deploy') }}</button>
      <button type="button" class="dmn-editor__tester" @click="openTester">{{ t('admin.dmnEditor.test') }}</button>
      <button type="button" class="dmn-editor__suite" @click="openSuite">{{ t('admin.dmnEditor.suite') }}</button>
      <button type="button" class="dmn-editor__history" @click="openHistory">{{ t('admin.dmnEditor.history') }}</button>
    </div>

    <p v-if="draft.loadError.value" class="dmn-editor__error">
      {{ t('admin.dmnEditor.loadError', { error: draft.loadError.value }) }}
    </p>
    <p v-if="readError" class="dmn-editor__error">{{ readError }}</p>
    <p v-if="saveError" class="dmn-editor__error">{{ saveError }}</p>
    <p v-if="draft.hasUnsavedChanges.value" class="dmn-editor__unsaved">{{ t('admin.dmnEditor.unsaved') }}</p>

    <DmnEditorCanvas v-if="ready" ref="canvasRef" :dmn-xml="canvasXml" @dirty="onCanvasDirty" />

    <section v-if="deployPending" class="dmn-editor__deploy-panel">
      <p v-if="deployment.deploymentInfo.value">
        {{ t('admin.dmnEditor.currentVersion', { version: deployment.deploymentInfo.value.latestVersion }) }}
      </p>
      <p v-else>{{ t('admin.dmnEditor.none') }}</p>

      <p v-if="deployment.deployError.value" class="dmn-editor__error">{{ deployment.deployError.value }}</p>
      <p v-if="deployment.deployedResult.value" class="dmn-editor__success">
        {{ t('admin.dmnEditor.deployed', { version: deployment.deployedResult.value.deployedVersion, id: deployment.deployedResult.value.deploymentId }) }}
      </p>

      <button
        type="button"
        class="dmn-editor__confirm-deploy"
        :disabled="deployment.deploying.value"
        @click="confirmDeploy"
      >
        {{ deployment.deploying.value ? t('admin.dmnEditor.deploying') : t('admin.dmnEditor.confirm') }}
      </button>
    </section>

    <section v-if="testerOpen" class="dmn-editor__tester-panel">
      <label>
        {{ t('admin.dmnEditor.inputs') }}
        <textarea v-model="inputsJson" class="dmn-editor__inputs" rows="4" />
      </label>
      <label>
        {{ t('admin.dmnEditor.version') }}
        <select v-model="selectedVersion" class="dmn-editor__version">
          <option value="">{{ t('admin.dmnEditor.latest') }}</option>
          <option v-for="item in evaluation.versions.value" :key="item.version" :value="String(item.version)">
            {{ t('admin.dmnEditor.versionN', { n: item.version }) }}
          </option>
        </select>
      </label>
      <button type="button" class="dmn-editor__evaluate" @click="runEvaluation">{{ t('admin.dmnEditor.evaluate') }}</button>
      <pre v-if="evaluation.evaluation.value" class="dmn-editor__rows">{{
        JSON.stringify(evaluation.evaluation.value.rows)
      }}</pre>
    </section>

    <section v-if="suiteOpen" class="dmn-editor__suite-panel">
      <label>
        {{ t('admin.dmnEditor.suiteFile') }}
        <input class="dmn-editor__suite-file" type="file" accept=".json,.csv" @change="onSuiteFile" />
      </label>
      <label>
        {{ t('admin.dmnEditor.suiteVersion') }}
        <select v-model="selectedVersion">
          <option value="">{{ t('admin.dmnEditor.latest') }}</option>
          <option v-for="item in evaluation.versions.value" :key="'suite-' + item.version" :value="String(item.version)">
            {{ t('admin.dmnEditor.versionN', { n: item.version }) }}
          </option>
        </select>
      </label>
      <ul v-if="evaluation.report.value">
        <li v-for="result in evaluation.report.value.results" :key="result.index">
          {{ t('admin.dmnEditor.case', { n: result.index + 1, state: result.passed ? t('admin.dmnEditor.passed') : t('admin.dmnEditor.failed') }) }}
          <span v-if="result.failureMessage"> — {{ result.failureMessage }}</span>
        </li>
      </ul>
    </section>

    <section v-if="historyOpen" class="dmn-editor__history-panel">
      <ul>
        <li v-for="item in evaluation.versions.value" :key="item.version">
          {{ t('admin.dmnEditor.versionAt', { n: item.version, at: item.deployedAt }) }}
          <button type="button" @click="askRedeploy(item.version)">
            {{ t('admin.dmnEditor.redeploy', { n: item.version }) }}
          </button>
        </li>
      </ul>
      <div v-if="evaluation.pendingRedeployVersion.value != null" class="dmn-editor__redeploy-confirm">
        <p>{{ t('admin.dmnEditor.askRedeploy', { n: evaluation.pendingRedeployVersion.value }) }}</p>
        <button type="button" class="dmn-editor__confirm-redeploy" @click="confirmRedeploy">
          {{ t('admin.dmnEditor.confirmRedeploy') }}
        </button>
        <button type="button" class="dmn-editor__cancel-redeploy" @click="evaluation.cancelRedeploy()">
          {{ t('common.cancel') }}
        </button>
      </div>
      <p v-if="evaluation.redeployed.value" class="dmn-editor__success">
        {{ t('admin.dmnEditor.redeployed', { n: evaluation.redeployed.value.deployedVersion }) }}
      </p>
    </section>

    <p v-if="evaluation.error.value" class="dmn-editor__error">{{ evaluation.error.value }}</p>
  </section>
</template>

<style scoped>
.dmn-editor__toolbar {
  display: flex;
  gap: 1rem;
  align-items: center;
  margin-bottom: 1rem;
}
.dmn-editor__error {
  color: #c92a2a;
  background: #fff0f0;
  padding: 0.5rem;
  border-radius: 4px;
}
.dmn-editor__success {
  color: #2f9e44;
}
.dmn-editor__unsaved {
  color: #8a4b08;
}
</style>
